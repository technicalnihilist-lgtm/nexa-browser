package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Persisted tab state used for session restore across process death. Only the
 * URL and title are stored — WebView instances are never serialised.
 */
@Entity(tableName = "recent_tabs")
data class RecentTabEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val url: String,
    val title: String,
    val position: Int,
    val savedAt: Long = System.currentTimeMillis()
)
