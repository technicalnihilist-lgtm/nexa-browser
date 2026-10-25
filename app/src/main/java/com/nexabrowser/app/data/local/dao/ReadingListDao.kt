package com.nexabrowser.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.nexabrowser.app.data.local.entity.ReadingListEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ReadingListDao {

    @Query("SELECT * FROM reading_list ORDER BY addedAt DESC")
    fun observeAll(): Flow<List<ReadingListEntity>>

    @Query("SELECT COUNT(*) FROM reading_list WHERE url = :url")
    suspend fun countByUrl(url: String): Int

    @Query("SELECT * FROM reading_list WHERE url = :url LIMIT 1")
    suspend fun findByUrl(url: String): ReadingListEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ReadingListEntity): Long

    @Query("UPDATE reading_list SET archivePath = :path, hasOfflineCopy = :hasCopy WHERE id = :id")
    suspend fun updateArchive(id: Long, path: String?, hasCopy: Boolean)

    @Query("DELETE FROM reading_list WHERE id = :id")
    suspend fun deleteById(id: Long)
}
