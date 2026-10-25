package com.nexabrowser.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexabrowser.app.data.local.entity.SitePermissionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SitePermissionDao {

    @Query("SELECT * FROM site_permissions ORDER BY origin ASC, permission ASC")
    fun observeAll(): Flow<List<SitePermissionEntity>>

    @Query("SELECT * FROM site_permissions WHERE origin = :origin AND permission = :permission LIMIT 1")
    suspend fun find(origin: String, permission: String): SitePermissionEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(record: SitePermissionEntity)

    @Query("DELETE FROM site_permissions WHERE origin = :origin AND permission = :permission")
    suspend fun delete(origin: String, permission: String)

    @Query("DELETE FROM site_permissions")
    suspend fun clearAll()
}
