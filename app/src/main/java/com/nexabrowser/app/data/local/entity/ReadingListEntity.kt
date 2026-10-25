package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A page saved for later. When an offline copy exists we write a WebView web
 * archive and record its path in [archivePath]; [hasOfflineCopy] is only true
 * once that file actually exists on disk.
 */
@Entity(tableName = "reading_list")
data class ReadingListEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val addedAt: Long = System.currentTimeMillis(),
    val archivePath: String? = null,
    val hasOfflineCopy: Boolean = false
)
