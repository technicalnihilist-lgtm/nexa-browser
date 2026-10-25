package com.nexabrowser.app.data.repository

import com.nexabrowser.app.data.local.NexaDatabase
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * Single entry point for all persisted browser data except downloads. The
 * repository maps storage entities to UI-facing domain models so the Compose
 * layer never depends on Room types.
 */
class BrowserRepository(private val db: NexaDatabase) {

    private val bookmarkDao = db.bookmarkDao()
    private val folderDao = db.bookmarkFolderDao()
    private val historyDao = db.historyDao()
    private val readingListDao = db.readingListDao()
    private val shortcutDao = db.shortcutDao()
    private val recentTabDao = db.recentTabDao()
    private val permissionDao = db.sitePermissionDao()

    // ---------------------------------------------------------------- bookmarks

    val bookmarks: Flow<List<Bookmark>> = bookmarkDao.observeAll().map { list -> list.map { it.toDomain() } }

    val bookmarkFolders: Flow<List<BookmarkFolder>> =
        folderDao.observeAll().map { list -> list.map { BookmarkFolder(it.id, it.name, it.position) } }

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

    val history: Flow<List<HistoryEntry>> = historyDao.observeAll().map { list -> list.map { it.toDomain() } }

    fun searchHistory(query: String): Flow<List<HistoryEntry>> =
        historyDao.search(query).map { list -> list.map { it.toDomain() } }

    suspend fun recentHistory(limit: Int): List<HistoryEntry> = historyDao.recent(limit).map { it.toDomain() }

    /** Frequently visited pages, computed from real history rows. */
    fun topSites(limit: Int = 12): Flow<List<TopSite>> =
        historyDao.topSites(limit).map { rows -> rows.map { TopSite(it.url, it.title, it.visits, it.lastVisit) } }

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
        readingListDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun isInReadingList(url: String): Boolean = readingListDao.countByUrl(url) > 0

    suspend fun addToReadingList(title: String, url: String, faviconUrl: String?): Long =
        readingListDao.insert(ReadingListEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl))

    suspend fun removeFromReadingList(id: Long) = readingListDao.deleteById(id)

    suspend fun readingListByUrl(url: String): ReadingListItem? = readingListDao.findByUrl(url)?.toDomain()

    suspend fun setOfflineCopy(id: Long, path: String?, hasCopy: Boolean) =
        readingListDao.updateArchive(id, path, hasCopy)

    // --------------------------------------------------------------- shortcuts

    val shortcuts: Flow<List<Shortcut>> =
        shortcutDao.observeAll().map { list -> list.map { it.toDomain() } }

    suspend fun addShortcut(title: String, url: String, faviconUrl: String? = null, position: Int = 0): Long =
        shortcutDao.insert(ShortcutEntity(title = title.ifBlank { url }, url = url, faviconUrl = faviconUrl, position = position))

    suspend fun updateShortcut(shortcut: Shortcut) {
        shortcutDao.update(ShortcutEntity(shortcut.id, shortcut.title, shortcut.url, shortcut.faviconUrl, shortcut.position))
    }

    suspend fun deleteShortcut(id: Long) = shortcutDao.deleteById(id)

    suspend fun reorderShortcuts(ordered: List<Shortcut>) {
        ordered.forEachIndexed { index, s -> shortcutDao.updatePosition(s.id, index) }
    }

    // ------------------------------------------------------------ session tabs

    suspend fun saveSessionTabs(tabs: List<Triple<String, String, Int>>) {
        recentTabDao.clearAll()
        recentTabDao.insertAll(tabs.map { RecentTabEntity(url = it.first, title = it.second, position = it.third) })
    }

    suspend fun loadSessionTabs(): List<Triple<String, String, Int>> =
        recentTabDao.getAll().map { Triple(it.url, it.title, it.position) }

    // -------------------------------------------------------- site permissions

    val sitePermissions: Flow<List<SitePermissionRecord>> =
        permissionDao.observeAll().map { list -> list.mapNotNull { it.toDomain() } }

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
