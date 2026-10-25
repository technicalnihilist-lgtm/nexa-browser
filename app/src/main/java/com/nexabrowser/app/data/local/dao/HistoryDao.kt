package com.nexabrowser.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.nexabrowser.app.data.local.entity.HistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface HistoryDao {

    @Query("SELECT * FROM history ORDER BY visitedAt DESC")
    fun observeAll(): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE title LIKE '%' || :query || '%' OR url LIKE '%' || :query || '%' ORDER BY visitedAt DESC")
    fun search(query: String): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun mostRecentForUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    /** Real visit frequency, computed from stored history only. */
    @Query(
        "SELECT url AS url, MAX(title) AS title, COUNT(*) AS visits, MAX(visitedAt) AS lastVisit " +
            "FROM history GROUP BY url ORDER BY visits DESC, lastVisit DESC LIMIT :limit"
    )
    fun topSites(limit: Int): Flow<List<TopSiteRow>>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    @Query("UPDATE history SET visitedAt = :visitedAt, title = :title WHERE id = :id")
    suspend fun touch(id: Long, visitedAt: Long, title: String)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :from AND visitedAt < :to")
    suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
