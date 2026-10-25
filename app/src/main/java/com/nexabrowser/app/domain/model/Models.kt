package com.nexabrowser.app.domain.model

/** A bookmark as used by the UI layer. */
data class Bookmark(
    val id: Long,
    val title: String,
    val url: String,
    val faviconUrl: String?,
    val folderId: Long?,
    val createdAt: Long
)

/** A bookmark folder. */
data class BookmarkFolder(
    val id: Long,
    val name: String,
    val position: Int
)

/** A visited page. */
data class HistoryEntry(
    val id: Long,
    val title: String,
    val url: String,
    val faviconUrl: String?,
    val visitedAt: Long
)

/** A saved-for-later page. */
data class ReadingListItem(
    val id: Long,
    val title: String,
    val url: String,
    val faviconUrl: String?,
    val addedAt: Long,
    val archivePath: String?,
    val hasOfflineCopy: Boolean
)

/** A start-page shortcut. */
data class Shortcut(
    val id: Long,
    val title: String,
    val url: String,
    val faviconUrl: String?,
    val position: Int
)

/** A frequently visited page, derived from real stored history. */
data class TopSite(
    val url: String,
    val title: String,
    val visits: Int,
    val lastVisit: Long
)

/** State of a download. Mirrors the DownloadManager status values we care about. */
enum class DownloadStatus {
    PENDING, RUNNING, PAUSED, COMPLETED, FAILED, CANCELLED;

    val isActive: Boolean get() = this == PENDING || this == RUNNING || this == PAUSED
    val isFinished: Boolean get() = this == COMPLETED || this == FAILED || this == CANCELLED
}

/** A download row for the downloads screen. */
data class DownloadItem(
    val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: DownloadStatus,
    val localUri: String?,
    val reason: String?,
    val createdAt: Long,
    val updatedAt: Long
) {
    /** 0f..1f when the total size is known, otherwise null. */
    val progress: Float?
        get() = if (totalBytes > 0L) (downloadedBytes.toFloat() / totalBytes.toFloat()).coerceIn(0f, 1f) else null
}

/** A website permission the user can review and revoke. */
enum class SitePermission(val key: String) {
    CAMERA("camera"),
    MICROPHONE("microphone"),
    LOCATION("location");

    companion object {
        fun fromKey(key: String): SitePermission? = entries.firstOrNull { it.key == key }
    }
}

enum class PermissionDecision { ALLOW, DENY }

/** A stored permission decision for one origin. */
data class SitePermissionRecord(
    val origin: String,
    val permission: SitePermission,
    val decision: PermissionDecision,
    val updatedAt: Long
)
