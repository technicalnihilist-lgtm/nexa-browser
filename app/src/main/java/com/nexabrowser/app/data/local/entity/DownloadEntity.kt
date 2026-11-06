package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A download tracked by the app. [id] is the Android DownloadManager id so the
 * row can be reconciled against the system download store.
 *
 * [status] is indexed because the tracker asks for the active rows
 * (`status IN ('PENDING','RUNNING','PAUSED')`) once a second for as long as
 * anything is downloading, and [createdAt] because the downloads screen is
 * ordered by it — both were full table scans of every download the user had
 * ever made, repeated on every poll tick.
 */
@Entity(tableName = "downloads", indices = [Index("status"), Index("createdAt")])
data class DownloadEntity(
    @PrimaryKey val id: Long,
    val url: String,
    val fileName: String,
    val mimeType: String?,
    val totalBytes: Long,
    val downloadedBytes: Long,
    val status: String,
    val localUri: String?,
    val reason: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
