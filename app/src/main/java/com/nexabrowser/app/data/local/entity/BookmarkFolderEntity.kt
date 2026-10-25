package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** A folder used to organise bookmarks. */
@Entity(tableName = "bookmark_folders")
data class BookmarkFolderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val createdAt: Long = System.currentTimeMillis(),
    val position: Int = 0
)
