package com.musicplayer.app.di

import android.content.Context
import androidx.room.Room
import com.musicplayer.app.data.local.database.MusicDatabase
import com.musicplayer.app.data.local.database.PlaylistDao
import com.musicplayer.app.data.local.database.RecentPlayedSongDao
import com.musicplayer.app.data.local.database.RecentSearchSongDao
import com.musicplayer.app.data.local.database.SearchHistoryDao
import com.musicplayer.app.data.local.database.SongDao
import com.musicplayer.app.data.local.database.NotificationDao
import com.musicplayer.app.data.local.database.StreamUrlCacheDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): MusicDatabase {
        return Room.databaseBuilder(
            context,
            MusicDatabase::class.java,
            "music_db"
        )
        .addMigrations(
            MusicDatabase.MIGRATION_1_2,
            MusicDatabase.MIGRATION_2_3,
            MusicDatabase.MIGRATION_3_4,
            MusicDatabase.MIGRATION_4_5,
            MusicDatabase.MIGRATION_5_6,
            MusicDatabase.MIGRATION_6_7,
            MusicDatabase.MIGRATION_7_8
        )
        // Removed fallbackToDestructiveMigration() to protect user data from accidental wipes
        .build()
    }

    @Provides
    fun provideNotificationDao(database: MusicDatabase): NotificationDao {
        return database.notificationDao()
    }

    @Provides
    fun provideStreamUrlCacheDao(database: MusicDatabase): StreamUrlCacheDao {
        return database.streamUrlCacheDao()
    }

    @Provides
    fun provideSongDao(database: MusicDatabase): SongDao {
        return database.songDao()
    }

    @Provides
    fun providePlaylistDao(database: MusicDatabase): PlaylistDao {
        return database.playlistDao()
    }

    @Provides
    fun provideSearchHistoryDao(database: MusicDatabase): SearchHistoryDao {
        return database.searchHistoryDao()
    }

    @Provides
    fun provideRecentPlayedSongDao(database: MusicDatabase): RecentPlayedSongDao {
        return database.recentPlayedSongDao()
    }

    @Provides
    fun provideRecentSearchSongDao(database: MusicDatabase): RecentSearchSongDao {
        return database.recentSearchSongDao()
    }
}
