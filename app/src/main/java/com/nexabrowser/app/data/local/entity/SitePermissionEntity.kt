package com.nexabrowser.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index

/** A remembered allow/deny decision for one permission on one origin. */
@Entity(
    tableName = "site_permissions",
    primaryKeys = ["origin", "permission"],
    indices = [Index("origin")]
)
data class SitePermissionEntity(
    val origin: String,
    val permission: String,
    val decision: String,
    val updatedAt: Long = System.currentTimeMillis()
)
