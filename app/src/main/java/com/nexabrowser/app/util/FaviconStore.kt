package com.nexabrowser.app.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Pure sizing rules for stored favicons, kept free of Android types so they can
 * be unit tested on the JVM.
 */
object FaviconSizing {

    /**
     * Icons are drawn at 16–28dp, so anything larger than this is wasted file
     * size and wasted decode memory. 96px still looks sharp at 3x density.
     */
    const val MAX_ICON_EDGE = 96

    /**
     * Target size for an icon of [width] x [height]: the longest edge is capped
     * at [max] and the other edge keeps the aspect ratio, never below 1px.
     */
    fun targetSize(width: Int, height: Int, max: Int = MAX_ICON_EDGE): Pair<Int, Int> {
        if (width <= 0 || height <= 0) return 1 to 1
        val longest = maxOf(width, height)
        if (longest <= max) return width to height
        val scale = max.toFloat() / longest
        val w = (width * scale).toInt().coerceAtLeast(1)
        val h = (height * scale).toInt().coerceAtLeast(1)
        return w to h
    }
}

/**
 * Remembers which favicon files exist on disk.
 *
 * The icon composable asks "does this path exist?" while it is being drawn — for
 * every row of the history, bookmark and reading-list lists, on every
 * recomposition, i.e. dozens of times per frame while scrolling. Each of those
 * questions used to be a stat() on the main thread. The answer cannot change
 * except through [FaviconStore], so it is remembered here once and invalidated
 * only by the writer. Keys are absolute paths; the map is bounded so a very long
 * session cannot grow it without limit.
 */
object FaviconPathCache {

    private val known = ConcurrentHashMap<String, Boolean>(64)

    /** True when [path] is a file that exists. Never touches the disk twice. */
    fun exists(path: String?): Boolean {
        if (path.isNullOrBlank()) return false
        known[path]?.let { return it }
        val exists = runCatching { File(path).let { it.isFile && it.length() > 0 } }.getOrDefault(false)
        if (known.size >= MAX_ENTRIES) known.clear()
        known[path] = exists
        return exists
    }

    /** Called by the store once it has written [path]. */
    fun markStored(path: String) {
        if (known.size >= MAX_ENTRIES) known.clear()
        known[path] = true
    }

    fun forget(path: String?) {
        if (path != null) known.remove(path)
    }

    fun clear() = known.clear()

    private const val MAX_ENTRIES = 512
}

/**
 * Small on-disk cache for page favicons so history, bookmarks and shortcuts can
 * show a real icon without re-fetching it over the network. Icons are keyed by
 * host and live in the app cache directory, so the OS may evict them freely.
 *
 * Two costs are avoided here:
 *  * [pathForHost] is answered from memory after the first lookup. It used to
 *    stat() the file every time, and its caller runs on the main thread once
 *    per completed page load.
 *  * [save] skips the re-encode when the same page hands back an icon of the
 *    same size that is already stored, and caps the stored icon at
 *    [FaviconSizing.MAX_ICON_EDGE] so decoding it later is cheap.
 */
class FaviconStore(private val context: Context) {

    private val dir: File = File(context.cacheDir, "favicons").apply { mkdirs() }

    /** host -> path, or null when the host is known to have no icon. */
    private val paths = object : LinkedHashMap<String, String?>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String?>?): Boolean =
            size > MAX_CACHED_HOSTS
    }

    /**
     * host -> packed size of the icon already written, so repeats are free.
     * Bounded like [paths]: it is keyed by host, and a long browsing session can
     * touch far more hosts than the cache should ever remember.
     */
    private val storedSizes = object : LinkedHashMap<String, Int>(64, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Int>?): Boolean =
            size > MAX_CACHED_HOSTS
    }

    fun pathForHost(host: String): String? {
        if (host.isBlank()) return null
        synchronized(paths) {
            if (paths.containsKey(host)) return paths[host]
        }
        val f = fileFor(host)
        val path = if (f.exists() && f.length() > 0) f.absolutePath else null
        synchronized(paths) { paths[host] = path }
        return path
    }

    suspend fun save(host: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        val (w, h) = FaviconSizing.targetSize(bitmap.width, bitmap.height)
        val signature = (w shl 16) xor h
        synchronized(storedSizes) {
            if (storedSizes[host] == signature) {
                val existing = synchronized(paths) { paths[host] }
                if (existing != null) return@withContext existing
            }
        }
        val target = fileFor(host)
        // The icon is written beside its final path and renamed into place. A
        // write that was cut short — process death, a full cache partition, a
        // failing encode — used to leave a truncated PNG at the real path, which
        // pathForHost then reported as a valid icon for the rest of the session
        // and Coil could never decode: that host kept a blank icon until the
        // cache was cleared by hand. A rename is atomic, so the target path only
        // ever holds a complete file.
        val temp = File(dir, "${target.name}.tmp")
        val written = runCatching {
            val scaled = if (w == bitmap.width && h == bitmap.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, w, h, true)
            try {
                FileOutputStream(temp).use { out ->
                    if (!scaled.compress(Bitmap.CompressFormat.PNG, 100, out)) {
                        error("favicon encode produced no data")
                    }
                }
            } finally {
                if (scaled !== bitmap) scaled.recycle()
            }
            if (!temp.renameTo(target)) {
                // renameTo does not replace an existing file on every
                // filesystem, so drop the old icon and try once more before
                // giving up on this one.
                target.delete()
                if (!temp.renameTo(target)) error("favicon rename failed")
            }
            true
        }.getOrElse {
            runCatching { temp.delete() }
            false
        }
        if (!written) return@withContext null

        synchronized(storedSizes) { storedSizes[host] = signature }
        synchronized(paths) { paths[host] = target.absolutePath }
        FaviconPathCache.markStored(target.absolutePath)
        target.absolutePath
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        FaviconPathCache.clear()
        synchronized(paths) { paths.clear() }
        synchronized(storedSizes) { storedSizes.clear() }
    }

    private fun fileFor(host: String) = File(dir, "${host.hashCode()}.png")

    private companion object {
        const val MAX_CACHED_HOSTS = 256
    }
}
