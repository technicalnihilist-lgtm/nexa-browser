package com.nexabrowser.app.util

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Small on-disk cache for page favicons so history, bookmarks and shortcuts can
 * show a real icon without re-fetching it over the network. Icons are keyed by
 * host and live in the app cache directory, so the OS may evict them freely.
 */
class FaviconStore(private val context: Context) {

    private val dir: File = File(context.cacheDir, "favicons").apply { mkdirs() }

    fun pathForHost(host: String): String? {
        if (host.isBlank()) return null
        val f = File(dir, "${host.hashCode()}.png")
        return if (f.exists() && f.length() > 0) f.absolutePath else null
    }

    suspend fun save(host: String, bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (host.isBlank()) return@withContext null
        runCatching {
            val f = File(dir, "${host.hashCode()}.png")
            FileOutputStream(f).use { out -> bitmap.compress(Bitmap.CompressFormat.PNG, 90, out) }
            f.absolutePath
        }.getOrNull()
    }

    fun clear() {
        runCatching { dir.listFiles()?.forEach { it.delete() } }
    }
}
