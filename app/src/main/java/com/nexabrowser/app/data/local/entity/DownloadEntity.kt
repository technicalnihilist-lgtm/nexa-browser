package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A download tracked by the app. [id] is the Android DownloadManager id so the
 * row can be reconciled against the system download store.
 */
@Entity(tableName = "downloads")
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
