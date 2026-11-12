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

    /**
     * The newest [limit] visits. The start page shows only a handful of rows, so
     * it subscribes to this instead of [observeAll]: reading (and mapping) the
     * entire table once per completed page load was pure waste, and it grew with
     * every site the user had ever visited.
     */
    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    fun observeRecent(limit: Int): Flow<List<HistoryEntity>>

    @Query("SELECT * FROM history WHERE url = :url ORDER BY visitedAt DESC LIMIT 1")
    suspend fun mostRecentForUrl(url: String): HistoryEntity?

    @Query("SELECT * FROM history ORDER BY visitedAt DESC LIMIT :limit")
    suspend fun recent(limit: Int): List<HistoryEntity>

    /**
     * Real visit frequency, computed from stored history only.
     *
     * The aggregate carries no title: `MAX(title)` is not covered by any index,
     * so it made SQLite visit every row of the table (74 ms on a 20k-row history,
     * re-run on every history write while the start page was open). Counting over
     * the covering index `(url, visitedAt)` instead costs ~24 ms, and the dozen
     * titles the start page shows are read with [titlesForUrls] afterwards.
     */
    @Query(
        "SELECT url AS url, COUNT(*) AS visits, MAX(visitedAt) AS lastVisit " +
            "FROM history GROUP BY url ORDER BY visits DESC, lastVisit DESC LIMIT :limit"
    )
    fun topSites(limit: Int): Flow<List<TopSiteAggregateRow>>

    /**
     * Titles of the newest visit to each of [urls] — one query for the whole list.
     *
     * The start page used to read these one at a time, so publishing its
     * "frequently visited" rows cost thirteen statements per history write: the
     * aggregate plus one indexed title lookup per row, each with its own trip
     * through the query executor and its own cursor. The inner select reads only
     * the covering `(url, visitedAt)` index (one range scan per url, `MAX` at the
     * end of it); the outer join then fetches the title of exactly those rows.
     *
     * Two visits to the same url with an identical timestamp produce two rows, so
     * callers take the first row per url.
     */
    @Query(
        "SELECT h.url AS url, h.title AS title FROM history h " +
            "JOIN (SELECT url AS url, MAX(visitedAt) AS lastVisit FROM history " +
            "WHERE url IN (:urls) GROUP BY url) newest " +
            "ON h.url = newest.url AND h.visitedAt = newest.lastVisit"
    )
    suspend fun titlesForUrls(urls: List<String>): List<UrlTitleRow>

    @Insert
    suspend fun insert(entry: HistoryEntity): Long

    /**
     * Folds a repeat visit into an existing row.
     *
     * `faviconUrl` is written here as well as `title`. It used to be left out,
     * so the icon a repeat visit had just written to disk was dropped on the
     * floor and the row kept the `null` it was inserted with — a site visited
     * once, then reloaded, showed a letter avatar in history for good even
     * though its icon was cached. The caller passes the merged value from
     * [com.nexabrowser.app.data.repository.HistoryDedupe], which decides
     * whether an incoming `null` may clear the stored path (it may not).
     */
    @Query("UPDATE history SET visitedAt = :visitedAt, title = :title, faviconUrl = :faviconUrl WHERE id = :id")
    suspend fun touch(id: Long, visitedAt: Long, title: String, faviconUrl: String?)

    @Query("DELETE FROM history WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM history WHERE visitedAt >= :from AND visitedAt < :to")
    suspend fun deleteRange(from: Long, to: Long)

    @Query("DELETE FROM history WHERE url = :url")
    suspend fun deleteByUrl(url: String)

    @Query("DELETE FROM history")
    suspend fun clearAll()
}
