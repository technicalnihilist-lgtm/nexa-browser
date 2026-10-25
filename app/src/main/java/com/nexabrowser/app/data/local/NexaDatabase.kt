package com.nexabrowser.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import com.nexabrowser.app.data.local.dao.BookmarkDao
import com.nexabrowser.app.data.local.dao.BookmarkFolderDao
import com.nexabrowser.app.data.local.dao.DownloadDao
import com.nexabrowser.app.data.local.dao.HistoryDao
import com.nexabrowser.app.data.local.dao.ReadingListDao
import com.nexabrowser.app.data.local.dao.RecentTabDao
import com.nexabrowser.app.data.local.dao.ShortcutDao
import com.nexabrowser.app.data.local.dao.SitePermissionDao
import com.nexabrowser.app.data.local.entity.BookmarkEntity
import com.nexabrowser.app.data.local.entity.BookmarkFolderEntity
import com.nexabrowser.app.data.local.entity.DownloadEntity
import com.nexabrowser.app.data.local.entity.HistoryEntity
import com.nexabrowser.app.data.local.entity.ReadingListEntity
import com.nexabrowser.app.data.local.entity.RecentTabEntity
import com.nexabrowser.app.data.local.entity.ShortcutEntity
import com.nexabrowser.app.data.local.entity.SitePermissionEntity

@Database(
    entities = [
        BookmarkEntity::class,
        BookmarkFolderEntity::class,
        HistoryEntity::class,
        DownloadEntity::class,
        ReadingListEntity::class,
        ShortcutEntity::class,
        RecentTabEntity::class,
        SitePermissionEntity::class
    ],
    version = 1,
    exportSchema = true
)
abstract class NexaDatabase : RoomDatabase() {

    abstract fun bookmarkDao(): BookmarkDao
    abstract fun bookmarkFolderDao(): BookmarkFolderDao
    abstract fun historyDao(): HistoryDao
    abstract fun downloadDao(): DownloadDao
    abstract fun readingListDao(): ReadingListDao
    abstract fun shortcutDao(): ShortcutDao
    abstract fun recentTabDao(): RecentTabDao
    abstract fun sitePermissionDao(): SitePermissionDao

    companion object {
        private const val DB_NAME = "nexa_browser.db"

        @Volatile
        private var instance: NexaDatabase? = null

        fun get(context: Context): NexaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    DB_NAME
                )
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
