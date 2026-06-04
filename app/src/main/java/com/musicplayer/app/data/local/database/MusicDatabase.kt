package com.musicplayer.app.data.local.database

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.musicplayer.app.data.local.database.entity.PlaylistEntity
import com.musicplayer.app.data.local.database.entity.PlaylistSongCrossRef
import com.musicplayer.app.data.local.database.entity.SearchHistoryEntity
import com.musicplayer.app.data.local.database.entity.SongEntity
import com.musicplayer.app.data.local.database.entity.RecentPlayedSongEntity
import com.musicplayer.app.data.local.database.entity.RecentSearchSongEntity
import com.musicplayer.app.data.local.database.entity.NotificationEntity
import com.musicplayer.app.data.local.database.entity.StreamUrlCacheEntity

@Database(
    entities = [
        SongEntity::class,
        PlaylistEntity::class,
        PlaylistSongCrossRef::class,
        SearchHistoryEntity::class,
        RecentPlayedSongEntity::class,
        RecentSearchSongEntity::class,
        NotificationEntity::class,
        StreamUrlCacheEntity::class
    ],
    version = 8,
    exportSchema = false
)
abstract class MusicDatabase : RoomDatabase() {
    abstract fun songDao(): SongDao
    abstract fun playlistDao(): PlaylistDao
    abstract fun searchHistoryDao(): SearchHistoryDao
    abstract fun recentPlayedSongDao(): RecentPlayedSongDao
    abstract fun recentSearchSongDao(): RecentSearchSongDao
    abstract fun notificationDao(): NotificationDao
    abstract fun streamUrlCacheDao(): StreamUrlCacheDao

    companion object {
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `stream_url_cache` (
                        `videoId` TEXT NOT NULL, 
                        `url` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`videoId`)
                    )
                """.trimIndent())
            }
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `notifications` (
                        `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `message` TEXT NOT NULL, 
                        `timestamp` INTEGER NOT NULL, 
                        `isRead` INTEGER NOT NULL, 
                        `type` TEXT NOT NULL
                    )
                """.trimIndent())
            }
        }
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE playlists ADD COLUMN isPinned INTEGER NOT NULL DEFAULT 0")
            }
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `search_history` (`query` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`query`))")
            }
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE search_history ADD COLUMN thumbnailUrl TEXT")
            }
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recent_played_songs` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artist` TEXT NOT NULL, 
                        `album` TEXT NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `thumbnailUrl` TEXT NOT NULL, 
                        `streamUrl` TEXT, 
                        `localPath` TEXT, 
                        `isDownloaded` INTEGER NOT NULL, 
                        `genre` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `recent_search_songs` (
                        `id` TEXT NOT NULL, 
                        `title` TEXT NOT NULL, 
                        `artist` TEXT NOT NULL, 
                        `album` TEXT NOT NULL, 
                        `duration` INTEGER NOT NULL, 
                        `thumbnailUrl` TEXT NOT NULL, 
                        `streamUrl` TEXT, 
                        `localPath` TEXT, 
                        `isDownloaded` INTEGER NOT NULL, 
                        `genre` TEXT, 
                        `timestamp` INTEGER NOT NULL, 
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE songs ADD COLUMN lyrics TEXT")
            }
        }
    }
}
