package com.nexabrowser.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexabrowser.app.data.local.entity.DownloadEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface DownloadDao {

    @Query("SELECT * FROM downloads ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DownloadEntity>>

    @Query("SELECT * FROM downloads WHERE id = :id LIMIT 1")
    suspend fun findById(id: Long): DownloadEntity?

    @Query("SELECT * FROM downloads WHERE status IN ('PENDING','RUNNING','PAUSED')")
    suspend fun active(): List<DownloadEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(download: DownloadEntity)

    @Query("UPDATE downloads SET downloadedBytes = :done, totalBytes = :total, status = :status, reason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun updateProgress(id: Long, done: Long, total: Long, status: String, reason: String?, now: Long)

    @Query("UPDATE downloads SET status = :status, reason = :reason, updatedAt = :now WHERE id = :id")
    suspend fun updateStatus(id: Long, status: String, reason: String?, now: Long)

    @Query("UPDATE downloads SET localUri = :uri WHERE id = :id")
    suspend fun updateLocalUri(id: Long, uri: String?)

    @Query("DELETE FROM downloads WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM downloads WHERE status NOT IN ('PENDING','RUNNING','PAUSED')")
    suspend fun clearCompleted()
}
