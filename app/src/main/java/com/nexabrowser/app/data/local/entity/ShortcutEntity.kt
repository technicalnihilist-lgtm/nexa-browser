package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A user-managed quick-access shortcut shown on the start page. */
@Entity(tableName = "shortcuts")
data class ShortcutEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val url: String,
    val faviconUrl: String? = null,
    val position: Int = 0,
    val createdAt: Long = System.currentTimeMillis()
)
