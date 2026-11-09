package com.nexabrowser.app.data.repository

import androidx.room.withTransaction
import com.nexabrowser.app.data.local.NexaDatabase
import com.nexabrowser.app.data.local.dao.TopSitesMerge
import com.nexabrowser.app.data.local.entity.BookmarkEntity
import com.nexabrowser.app.data.local.entity.BookmarkFolderEntity
import com.nexabrowser.app.data.local.entity.HistoryEntity
import com.nexabrowser.app.data.local.entity.ReadingListEntity
import com.nexabrowser.app.data.local.entity.RecentTabEntity
import com.nexabrowser.app.data.local.entity.ShortcutEntity
import com.nexabrowser.app.data.local.entity.SitePermissionEntity
import com.nexabrowser.app.domain.model.Bookmark
import com.nexabrowser.app.domain.model.BookmarkFolder
import com.nexabrowser.app.domain.model.HistoryEntry
import com.nexabrowser.app.domain.model.PermissionDecision
import com.nexabrowser.app.domain.model.ReadingListItem
import com.nexabrowser.app.domain.model.Shortcut
import com.nexabrowser.app.domain.model.SitePermission
import com.nexabrowser.app.domain.model.SitePermissionRecord
import com.nexabrowser.app.domain.model.TopSite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Single entry point for all persisted browser data except downloads. The
 * repository maps storage entities to UI-facing domain models so the Compose
 * layer never depends on Room types.
 */
class BrowserRepository(private val db: NexaDatabase) {

    /**
     * Every table below is mapped from Room rows to domain objects in a `map`
     * operator, and every one of those flows is collected through
     * `stateIn(viewModelScope, …)` — i.e. on the **main** dispatcher. Room runs
     * the SQL itself on its query executor, but the transform that follows it
     * ran on whatever thread collected the flow, so the row mapping did not:
     * opening History mapped the whole table (one [HistoryEntry] per visit — a
     * 20k-visit history is 20k allocations) on the UI thread, and every history
     * write re-mapped it again while that screen was open. The same applied to
     * bookmarks, downloads and the reading list on every write.
     *
     * `flowOn(Dispatchers.Default)` moves the collection — and with it the
     * mapping — off the main thread. Only the finished list is handed back to
     * the collector, so the UI still sees exactly the same values in the same
     * order; it just no longer does the mapping work itself.
     */
    private fun <T> Flow<T>.offMainThread(): Flow<T> = flowOn(Dispatchers.Default)

    private val bookmarkDao = db.bookmarkDao()
    private val folderDao = db.bookmarkFolderDao()
    private val historyDao = db.historyDao()
    private val readingListDao = db.readingListDao()
    private val shortcutDao = db.shortcutDao()
    private val recentTabDao = db.recentTabDao()
    private val permissionDao = db.sitePermissionDao()

    // ---------------------------------------------------------------- bookmarks

    val bookmarks: Flow<List<Bookmark>> =
        bookmarkDao.observeAll().map { list -> list.map { it.toDomain() } }.offMainThread()

    val bookmarkFolders: Flow<List<BookmarkFolder>> =
        folderDao.observeAll()
            .map { list -> list.map { BookmarkFolder(it.id, it.name, it.position) } }
            .offMainThread()

    suspend fun isBookmarked(url: String): Boolean = bookmarkDao.countByUrl(url) > 0

    suspend fun addBookmark(title: String, url: String, faviconUrl: String?, folderId: Long? = null): Long {
        val existing = bookmarkDao.findByUrl(url)
        if (existing != null) {
            bookmarkDao.update(existing.copy(title = title, faviconUrl = faviconUrl))
            return existing.id
        }
        return bookmarkDao.insert(
            BookmarkEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl, folderId = folderId)
        )
    }

    suspend fun updateBookmark(bookmark: Bookmark) {
        bookmarkDao.update(
            BookmarkEntity(
                id = bookmark.id,
                title = bookmark.title,
                url = bookmark.url,
                faviconUrl = bookmark.faviconUrl,
                folderId = bookmark.folderId,
                createdAt = bookmark.createdAt
            )
        )
    }

    suspend fun deleteBookmark(bookmark: Bookmark) = bookmarkDao.deleteById(bookmark.id)

    suspend fun toggleBookmark(title: String, url: String, faviconUrl: String?): Boolean {
        return if (isBookmarked(url)) {
            bookmarkDao.deleteByUrl(url)
            false
        } else {
            addBookmark(title, url, faviconUrl)
            true
        }
    }

    suspend fun createFolder(name: String): Long = folderDao.insert(BookmarkFolderEntity(name = name))
    suspend fun deleteFolder(id: Long) = folderDao.deleteById(id)

    // ----------------------------------------------------------------- history

    val history: Flow<List<HistoryEntry>> =
        historyDao.observeAll().map { list -> list.map { it.toDomain() } }.offMainThread()

    fun searchHistory(query: String): Flow<List<HistoryEntry>> =
        historyDao.search(query).map { list -> list.map { it.toDomain() } }.offMainThread()

    suspend fun recentHistory(limit: Int): List<HistoryEntry> = historyDao.recent(limit).map { it.toDomain() }

    /** Only the newest [limit] visits — what the start page renders. */
    fun recentVisits(limit: Int = 6): Flow<List<HistoryEntry>> =
        historyDao.observeRecent(limit).map { list -> list.map { it.toDomain() } }.offMainThread()

    /**
     * Frequently visited pages, computed from real history rows.
     *
     * The aggregate is counted over the covering `(url, visitedAt)` index and
     * carries no title; the titles of the (at most [limit]) rows are then read
     * with one batched lookup. That replaced a `MAX(title)` in the GROUP BY,
     * which forced a full table scan — ~74 ms against ~24 ms for a 20k-visit
     * history, on a query Room re-runs on every history write.
     *
     * The batched lookup replaces one `titleForUrl` per row, which meant thirteen
     * statements and thirteen query-executor round trips for the twelve rows the
     * start page shows, on every history write.
     *
     * The title shown is the newest one the user saw for that URL, rather than the
     * lexicographically largest one `MAX(title)` happened to pick.
     */
    fun topSites(limit: Int = 12): Flow<List<TopSite>> =
        historyDao.topSites(limit).map { rows ->
            val titles = if (rows.isEmpty()) {
                emptyMap()
            } else {
                TopSitesMerge.titlesOf(historyDao.titlesForUrls(rows.map { it.url }))
            }
            TopSitesMerge.merge(rows, titles).map { TopSite(it.url, it.title, it.visits, it.lastVisit) }
        }.offMainThread()

    /**
     * Records a visit. Consecutive visits to the same URL within a short window
     * update the timestamp instead of creating duplicate rows.
     */
    suspend fun recordVisit(title: String, url: String, faviconUrl: String?) {
        if (url.isBlank() || url.startsWith("about:")) return
        val now = System.currentTimeMillis()
        val latest = historyDao.mostRecentForUrl(url)
        if (latest != null && now - latest.visitedAt < DEDUPE_WINDOW_MS) {
            historyDao.touch(latest.id, now, title.ifBlank { latest.title })
        } else {
            historyDao.insert(HistoryEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl, visitedAt = now))
        }
    }

    suspend fun deleteHistory(id: Long) = historyDao.deleteById(id)
    suspend fun deleteHistoryUrl(url: String) = historyDao.deleteByUrl(url)
    suspend fun clearHistory() = historyDao.clearAll()
    suspend fun clearHistoryRange(from: Long, to: Long) = historyDao.deleteRange(from, to)

    // ------------------------------------------------------------ reading list

    val readingList: Flow<List<ReadingListItem>> =
        readingListDao.observeAll().map { list -> list.map { it.toDomain() } }.offMainThread()

    suspend fun isInReadingList(url: String): Boolean = readingListDao.countByUrl(url) > 0

    suspend fun addToReadingList(title: String, url: String, faviconUrl: String?): Long =
        readingListDao.insert(ReadingListEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl))

    suspend fun removeFromReadingList(id: Long) = readingListDao.deleteById(id)

    suspend fun readingListByUrl(url: String): ReadingListItem? = readingListDao.findByUrl(url)?.toDomain()

    suspend fun setOfflineCopy(id: Long, path: String?, hasCopy: Boolean) =
        readingListDao.updateArchive(id, path, hasCopy)

    // --------------------------------------------------------------- shortcuts

    val shortcuts: Flow<List<Shortcut>> =
        shortcutDao.observeAll().map { list -> list.map { it.toDomain() } }.offMainThread()

    suspend fun addShortcut(title: String, url: String, faviconUrl: String? = null, position: Int = 0): Long =
        shortcutDao.insert(ShortcutEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl, position = position))

    suspend fun updateShortcut(shortcut: Shortcut) {
        shortcutDao.update(ShortcutEntity(shortcut.id, shortcut.title, shortcut.url, shortcut.faviconUrl, shortcut.position))
    }

    suspend fun deleteShortcut(id: Long) = shortcutDao.deleteById(id)

    /**
     * One transaction instead of N: a reorder used to be N separate writes, so a
     * drag could be interrupted half-applied and leave duplicate positions. It is
     * also a single fsync rather than one per row.
     */
    suspend fun reorderShortcuts(ordered: List<Shortcut>) {
        if (ordered.isEmpty()) return
        db.withTransaction {
            ordered.forEachIndexed { index, s -> shortcutDao.updatePosition(s.id, index) }
        }
    }

    // ------------------------------------------------------------ session tabs

    /**
     * Clear-then-insert in one transaction. Without it a crash between the two
     * statements wiped the saved session and left nothing behind.
     */
    suspend fun saveSessionTabs(tabs: List<Triple<String, String, Int>>) {
        db.withTransaction {
            recentTabDao.clearAll()
            recentTabDao.insertAll(tabs.map { RecentTabEntity(url = it.first, title = it.second, position = it.third) })
        }
    }

    suspend fun loadSessionTabs(): List<Triple<String, String, Int>> =
        recentTabDao.getAll().map { Triple(it.url, it.title, it.position) }

    // -------------------------------------------------------- site permissions

    val sitePermissions: Flow<List<SitePermissionRecord>> =
        permissionDao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }.offMainThread()

    suspend fun permissionFor(origin: String, permission: SitePermission): PermissionDecision? =
        permissionDao.find(origin, permission.key)?.let { PermissionDecision.valueOf(it.decision) }

    suspend fun rememberPermission(origin: String, permission: SitePermission, decision: PermissionDecision) =
        permissionDao.upsert(SitePermissionEntity(origin, permission.key, decision.name))

    suspend fun forgetPermission(origin: String, permission: SitePermission) =
        permissionDao.delete(origin, permission.key)

    suspend fun clearPermissions() = permissionDao.clearAll()

    private fun BookmarkEntity.toDomain() = Bookmark(id, title, url, faviconUrl, folderId, createdAt)
    private fun HistoryEntity.toDomain() = HistoryEntry(id, title, url, faviconUrl, visitedAt)
    private fun ReadingListEntity.toDomain() =
        ReadingListItem(id, title, url, faviconUrl, addedAt, archivePath, hasOfflineCopy)
    private fun ShortcutEntity.toDomain() = Shortcut(id, title, url, faviconUrl, position)
    private fun SitePermissionEntity.toDomain(): SitePermissionRecord? {
        val perm = SitePermission.fromKey(permission) ?: return null
        val decision = runCatching { PermissionDecision.valueOf(this.decision) }.getOrNull() ?: return null
        return SitePermissionRecord(origin, perm, decision, updatedAt)
    }

    companion object {
        private const val DEDUPE_WINDOW_MS = 30_000L
    }
}
