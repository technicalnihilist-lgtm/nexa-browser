package com.nexabrowser.app.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

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

    /** host -> "w x h" of the icon already written, so repeats are free. */
    private val storedSizes = HashMap<String, Int>()

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
        runCatching {
            val scaled = if (w == bitmap.width && h == bitmap.height) bitmap
            else Bitmap.createScaledBitmap(bitmap, w, h, true)
            val f = fileFor(host)
            FileOutputStream(f).use { out -> scaled.compress(Bitmap.CompressFormat.PNG, 100, out) }
            if (scaled !== bitmap) scaled.recycle()
            synchronized(storedSizes) { storedSizes[host] = signature }
            synchronized(paths) { paths[host] = f.absolutePath }
            f.absolutePath
        }.getOrNull()
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
        synchronized(paths) { paths.clear() }
        synchronized(storedSizes) { storedSizes.clear() }
    }

    private fun fileFor(host: String) = File(dir, "${host.hashCode()}.png")

    private companion object {
        const val MAX_CACHED_HOSTS = 256
    }
}
