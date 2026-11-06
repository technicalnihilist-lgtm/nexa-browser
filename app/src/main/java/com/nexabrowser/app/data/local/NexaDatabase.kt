package com.nexabrowser.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
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
    version = 2,
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

        /**
         * Adds the indexes that queries added in 1.3.21 rely on.
         *
         * Every one of these is a plain CREATE INDEX: no table is rebuilt and no
         * row is touched, so the upgrade is a few milliseconds on an existing
         * database and the user keeps their downloads, reading list and history.
         * The names have to match exactly what Room generates for an
         * `Index("column")` on that table, or the schema validation on open fails.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_status` ON `downloads` (`status`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_downloads_createdAt` ON `downloads` (`createdAt`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_reading_list_url` ON `reading_list` (`url`)")
            }
        }

        fun get(context: Context): NexaDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    NexaDatabase::class.java,
                    DB_NAME
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
    }
}
