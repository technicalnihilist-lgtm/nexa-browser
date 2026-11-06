package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A single visited page. Private-browsing visits are never written here; the
 * private manager keeps its own in-memory list and discards it on close.
 */
// The composite (url, visitedAt) index replaces the single-column `url` index:
// it answers every `WHERE url = ?` lookup the same way (a prefix match) *and*
// covers the start page's `GROUP BY url` aggregate, which the old index could
// not do without reading the whole table for each row's title.
@Entity(tableName = "history", indices = [Index(value = ["url", "visitedAt"]), Index("visitedAt")])
data class HistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val visitedAt: Long = System.currentTimeMillis()
)
