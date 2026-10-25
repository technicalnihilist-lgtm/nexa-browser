package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single visited page. Private-browsing visits are never written here; the
 * private manager keeps its own in-memory list and discards it on close.
 */
@Entity(tableName = "history", indices = [Index("url"), Index("visitedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)
