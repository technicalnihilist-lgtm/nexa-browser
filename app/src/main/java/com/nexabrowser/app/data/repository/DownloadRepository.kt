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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
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

    /**
     * Pulls the latest state for every non-finished row out of DownloadManager.
     *
     * One cursor for all rows instead of one query per row: with four downloads
     * running this used to be four binder round trips to the DownloadManager
     * service every second, on every poll, forever. It is one now.
     *
     * Returns how many rows were still active, so the caller does not have to
     * ask the database the same question a second time.
     */
    suspend fun refreshActive(): Int = withContext(Dispatchers.IO) {
        val active = dao.active()
        if (active.isEmpty()) return@withContext 0

        val byId = HashMap<Long, DownloadEntity>(active.size)
        active.forEach { byId[it.id] = it }
        val query = DownloadManager.Query().setFilterById(*active.map { it.id }.toLongArray())
        val seen = HashSet<Long>(active.size)

        runCatching {
            manager.query(query)?.use { c ->
                while (c.moveToNext()) {
                    val id = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID))
                    val row = byId[id] ?: continue
                    seen.add(id)
                    applyCursor(row, c)
                }
            }
        }

        // A row the system no longer reports is a dead record.
        active.forEach { row ->
            if (row.id !in seen) {
                dao.updateStatus(
                    row.id,
                    DownloadStatus.FAILED.name,
                    "Download record no longer available",
                    System.currentTimeMillis()
                )
            }
        }
        active.size
    }

    /**
     * Mirrors one cursor row into Room — but only when something actually
     * changed.
     *
     * The poll runs once a second for as long as any row is PENDING, RUNNING or
     * PAUSED, and a paused or stalled transfer reports byte-identical values
     * every time. Writing them anyway invalidated the `downloads` Flow, which
     * re-read the table, re-mapped every row and recomposed the downloads screen
     * once a second for a progress bar that was not moving. Unchanged rows now
     * cost one comparison and no database work at all.
     */
    private suspend fun applyCursor(row: DownloadEntity, c: Cursor) {
        val status = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))
        val downloaded = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR))
        val total = c.getLong(c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES))
        val reason = c.getInt(c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON))

        val mapped = when (status) {
            DownloadManager.STATUS_PENDING -> DownloadStatus.PENDING
            DownloadManager.STATUS_RUNNING -> DownloadStatus.RUNNING
            DownloadManager.STATUS_PAUSED -> DownloadStatus.PAUSED
            DownloadManager.STATUS_SUCCESSFUL -> DownloadStatus.COMPLETED
            else -> DownloadStatus.FAILED
        }
        val reasonText = if (mapped == DownloadStatus.FAILED) reasonText(reason) else null

        if (DownloadProgressRow.needsWrite(row, downloaded, total, mapped.name, reasonText)) {
            dao.updateProgress(row.id, downloaded, total, mapped.name, reasonText, System.currentTimeMillis())
        }
        if (mapped == DownloadStatus.COMPLETED) {
            val localUriIdx = c.getColumnIndex(DownloadManager.COLUMN_LOCAL_URI)
            val localUri = if (localUriIdx >= 0) c.getString(localUriIdx) else null
            val uri = localUri ?: manager.getUriForDownloadedFile(row.id)?.toString()
            // A completed row is polled again on the next tick; only the first
            // one has a new URI to record.
            if (uri != null && uri != row.localUri) dao.updateLocalUri(row.id, uri)
        }
    }

    /**
     * Polls DownloadManager only while something is actually downloading.
     *
     * The old loop ran for the whole life of the process: with nothing in the
     * list at all it still opened a Room query every fifteen seconds, waking
     * the database and the CPU for a table it already knew was empty. It also
     * meant a download started just after an idle tick showed no progress — the
     * row sat at PENDING, or at 0%, for up to fifteen seconds before the first
     * refresh.
     *
     * The `downloads` table is now the trigger: Room re-emits the list whenever
     * a row is written (a new download, a cancel, a completion, a progress
     * write), so the poll starts the moment work appears and stops on its own
     * when the last transfer finishes. `distinctUntilChanged` on the boolean
     * keeps the once-a-second progress writes from restarting it.
     */
    fun startTracking(scope: CoroutineScope) {
        scope.launch(Dispatchers.IO) {
            dao.observeAll()
                .map { rows -> DownloadActive.anyActive(rows) }
                .distinctUntilChanged()
                .collectLatest { anyActive ->
                    if (!anyActive) return@collectLatest
                    while (isActive) {
                        runCatching { refreshActive() }
                        delay(ACTIVE_INTERVAL_MS)
                    }
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
        val sanitised = raw.replace(UNSAFE_FILENAME_CHARS, "_").trim().ifBlank { "download" }
        return makeUnique(sanitised)
    }

    private fun extractFilename(disposition: String): String? {
        val utf8 = UTF8_FILENAME.find(disposition)
        if (utf8 != null) return runCatching { Uri.decode(utf8.groupValues[1]) }.getOrNull()
        return PLAIN_FILENAME.find(disposition)?.groupValues?.get(1)?.trim()
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

        // Compiled once. Both patterns used to be built inside the parse, i.e.
        // two regex compilations for every download the user starts.
        private val UTF8_FILENAME = Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
        private val PLAIN_FILENAME = Regex("filename=\"?([^\";]+)\"?", RegexOption.IGNORE_CASE)

        // Compiled once, like the two patterns above: the sanitiser used to
        // compile this regex on every download to strip the characters a file
        // name cannot contain.
        private val UNSAFE_FILENAME_CHARS = Regex("[\\\\/:*?\"<>|]")
    }
}

/**
 * Whether a set of stored rows contains anything worth polling for. Pure, so
 * the "is the tracker idle?" decision is unit tested rather than inferred.
 */
internal object DownloadActive {
    /** Derived from [DownloadStatus.isActive] so the two can never disagree. */
    private val ACTIVE = DownloadStatus.entries.filter { it.isActive }.map { it.name }.toSet()

    fun anyActive(rows: List<DownloadEntity>): Boolean = rows.any { it.status in ACTIVE }
}

/**
 * Pure decision about whether a polled download differs from the stored row.
 * Kept free of Android and DownloadManager types so it can be unit tested.
 */
internal object DownloadProgressRow {
    fun needsWrite(
        row: DownloadEntity,
        downloadedBytes: Long,
        totalBytes: Long,
        status: String,
        reason: String?
    ): Boolean = row.downloadedBytes != downloadedBytes ||
        row.totalBytes != totalBytes ||
        row.status != status ||
        row.reason != reason
}
