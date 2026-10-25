package com.nexabrowser.app.data.repository

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import android.webkit.MimeTypeMap
import com.nexabrowser.app.data.local.dao.DownloadDao
import com.nexabrowser.app.data.local.entity.DownloadEntity
import com.nexabrowser.app.domain.model.DownloadItem
import com.nexabrowser.app.domain.model.DownloadStatus
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/** Everything needed to start one download. */
data class DownloadRequest(
    val url: String,
    val userAgent: String?,
    val contentDisposition: String?,
    val mimeType: String?,
    val cookie: String?,
    val wifiOnly: Boolean = false,
    val showNotification: Boolean = true
)

/**
 * Wraps Android's [DownloadManager]. The system service owns the actual bytes on
 * disk and the notification; this class mirrors its state into Room so the
 * downloads screen survives process death, and exposes open/cancel/retry.
 *
 * Progress is never invented: rows are refreshed from the DownloadManager
 * cursor, so a download is only ever marked COMPLETED once the system reports it
 * completed.
 */
class DownloadRepository(
    private val context: Context,
    private val dao: DownloadDao
) {

    private val manager: DownloadManager =
        context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager

    val downloads: Flow<List<DownloadItem>> =
        dao.observeAll().map { list -> list.map { it.toDomain() } }

    /** Enqueues a download and records a PENDING row. Returns the DownloadManager id. */
    suspend fun enqueue(request: DownloadRequest): Long = withContext(Dispatchers.IO) {
        val fileName = resolveFileName(request)
        val mime = request.mimeType ?: guessMime(fileName)

        val dmRequest = DownloadManager.Request(Uri.parse(request.url)).apply {
            setTitle(fileName)
            setDescription(request.url)
            setMimeType(mime)
            setNotificationVisibility(
                if (request.showNotification) DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED
                else DownloadManager.Request.VISIBILITY_HIDDEN
            )
            setAllowedOverMetered(!request.wifiOnly)
            setAllowedOverRoaming(true)
            request.userAgent?.let { addRequestHeader("User-Agent", it) }
            request.cookie?.let { addRequestHeader("Cookie", it) }
            try {
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
            } catch (_: IllegalStateException) {
                setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, fileName)
            }
        }

        val id = manager.enqueue(dmRequest)
        dao.upsert(
            DownloadEntity(
                id = id,
                url = request.url,
                fileName = fileName,
                mimeType = mime,
                totalBytes = -1L,
                downloadedBytes = 0L,
                status = DownloadStatus.PENDING.name,
                localUri = null
            )
        )
        id
    }

    /** Pulls the latest state for every non-finished row out of DownloadManager. */
    suspend fun refreshActive() = withContext(Dispatchers.IO) {
        val active = dao.active()
        for (row in active) {
            val cursor = manager.query(DownloadManager.Query().setFilterById(row.id))
            cursor.use { c ->
                if (c == null || !c.moveToFirst()) {
                    dao.updateStatus(row.id, DownloadStatus.FAILED.name, "Download record no longer available", System.currentTimeMillis())
                } else {
                    applyCursor(row, c)
                }
            }
        }
    }

    private suspend fun applyCursor(row: DownloadEntity, c: Cursor) {
        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))
        val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
        val localUri = if (localUriIdx >= 0) c.getString(localUriIdx) else null

        val mapped = when (status) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
            else -> DownloadStatus.FAILED
        }
        val reasonText = if (mapped == DownloadStatus.FAILED) reasonText(reason) else null

        dao.updateProgress(row.id, downloaded, total, mapped.name, reasonText, System.currentTimeMillis())
        if (mapped == DownloadStatus.COMPLETED) {
            val uri = localUri ?: manager.getUriForDownloadedFile(row.id)?.toString()
            dao.updateLocalUri(row.id, uri)
        }
    }

    /** Runs a lightweight poll while any download is active. */
    fun startTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            while (isActive) {
                refreshActive()
                delay(if (dao.active().isEmpty()) IDLE_INTERVAL_MS else ACTIVE_INTERVAL_MS)
            }
        }
    }

    suspend fun cancel(id: Long) = withContext(Dispatchers.IO) {
        runCatching { manager.remove(id) }
        dao.updateStatus(id, DownloadStatus.CANCELLED.name, "Cancelled by user", System.currentTimeMillis())
    }

    /** Removes the row and, when still known to the system, the underlying download. */
    suspend fun remove(id: Long) = withContext(Dispatchers.IO) {
        runCatching { manager.remove(id) }
        dao.deleteById(id)
    }

    suspend fun retry(item: DownloadItem): Long = withContext(Dispatchers.IO) {
        dao.deleteById(item.id)
        enqueue(
            DownloadRequest(
                url = item.url,
                userAgent = null,
                contentDisposition = "attachment; filename=\"${item.fileName}\"",
                mimeType = item.mimeType,
                cookie = null
            )
        )
    }

    suspend fun clearFinished() = withContext(Dispatchers.IO) { dao.clearCompleted() }

    /** Returns the content URI of a completed download, or null if unavailable. */
    fun uriFor(id: Long): Uri? = runCatching { manager.getUriForDownloadedFile(id) }.getOrNull()

    // ------------------------------------------------------------------ helpers

    private fun resolveFileName(request: DownloadRequest): String {
        val fromDisposition = request.contentDisposition
            ?.let { extractFilename(it) }
            ?.takeIf { it.isNotBlank() }
        val raw = fromDisposition
            ?: Uri.parse(request.url).lastPathSegment?.takeIf { it.isNotBlank() && !it.contains("/") }
            ?: "download"
        val sanitised = raw.replace(Regex("[\\\\/:*?\"<>|]"), "_").trim().ifBlank { "download" }
        return makeUnique(sanitised)
    }

    private fun extractFilename(disposition: String): String? {
        val utf8 = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE).find(disposition)
        if (utf8 != null) return runCatching { Uri.decode(utf8.groupValues[1]) }.getOrNull()
        val plain = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE).find(disposition)
        return plain?.groupValues?.get(1)?.trim()
    }

    /** Appends " (n)" until the name does not collide with an existing file. */
    private fun makeUnique(name: String): String {
        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!dir.exists()) return name
        if (!File(dir, name).exists()) return name
        val dot = name.lastIndexOf('.')
        val base = if (dot > 0) name.substring(0, dot) else name
        val ext = if (dot > 0) name.substring(dot) else ""
        var i = 1
        while (i < 1000) {
            val candidate = "$base ($i)$ext"
            if (!File(dir, candidate).exists()) return candidate
            i++
        }
        return "$base-${System.currentTimeMillis()}$ext"
    }

    private fun guessMime(fileName: String): String {
        val ext = fileName.substringAfterLast('.', "").lowercase()
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun reasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_CANNOT_RESUME -> "Could not resume the download"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "Storage not available"
        DownloadManager.ERROR_FILE_ALREADY_EXISTS -> "A file with that name already exists"
        DownloadManager.ERROR_FILE_ERROR -> "Storage error"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "Network data error"
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "Not enough storage space"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "Too many redirects"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "The server returned an unexpected response"
        DownloadManager.ERROR_UNKNOWN -> "Download failed"
        else -> "Download failed (code $reason)"
    }

    private fun DownloadEntity.toDomain() = DownloadItem(
        id = id,
        url = url,
        fileName = fileName,
        mimeType = mimeType,
        totalBytes = totalBytes,
        downloadedBytes = downloadedBytes,
        status = runCatching { DownloadStatus.valueOf(status) }.getOrDefault(DownloadStatus.PENDING),
        localUri = localUri,
        reason = reason,
        createdAt = createdAt,
        updatedAt = updatedAt
    )

    companion object {
        private const val ACTIVE_INTERVAL_MS = 1_000L
        private const val IDLE_INTERVAL_MS = 15_000L
    }
}
