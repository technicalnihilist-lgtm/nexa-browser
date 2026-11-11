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

    /**
     * True while anything is downloading, answered by an indexed EXISTS probe.
     *
     * The tracker used to decide whether to poll from [observeAll], which means
     * the whole table — every finished download the user had ever made — was
     * read out of SQLite, materialised into [DownloadEntity] objects and walked
     * on every single progress write, i.e. once a second for as long as any
     * transfer was running. `status` is indexed, so this returns after one
     * index seek and no row is ever built.
     *
     * The statuses come from [com.nexabrowser.app.domain.model.DownloadStatus]
     * through `DownloadActive`, so the query and the rule the tracker documents
     * cannot drift apart.
     */
    @Query("SELECT EXISTS(SELECT 1 FROM downloads WHERE status IN (:statuses))")
    fun observeAnyActive(statuses: List<String>): Flow<Boolean>

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
