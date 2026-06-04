package com.musicplayer.app.data.local.database

import androidx.room.*
import com.musicplayer.app.data.local.database.entity.RecentPlayedSongEntity
import com.musicplayer.app.data.local.database.entity.RecentSearchSongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecentPlayedSongDao {
    @Query("SELECT * FROM recent_played_songs ORDER BY timestamp DESC LIMIT 15")
    fun getRecentPlayedSongs(): Flow<List<RecentPlayedSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentPlayedSong(song: RecentPlayedSongEntity)

    @Query("UPDATE recent_played_songs SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnail(id: String, thumbnailUrl: String)

    @Query("DELETE FROM recent_played_songs WHERE id NOT IN (SELECT id FROM recent_played_songs ORDER BY timestamp DESC LIMIT 15)")
    suspend fun trimRecentPlayedSongs()

    @Transaction
    suspend fun addRecentPlayedSong(song: RecentPlayedSongEntity) {
        insertRecentPlayedSong(song)
        trimRecentPlayedSongs()
    }
}

@Dao
interface RecentSearchSongDao {
    @Query("SELECT * FROM recent_search_songs ORDER BY timestamp DESC LIMIT 15")
    fun getRecentSearchSongs(): Flow<List<RecentSearchSongEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecentSearchSong(song: RecentSearchSongEntity)

    @Query("UPDATE recent_search_songs SET thumbnailUrl = :thumbnailUrl WHERE id = :id")
    suspend fun updateThumbnail(id: String, thumbnailUrl: String)

    @Query("DELETE FROM recent_search_songs WHERE id NOT IN (SELECT id FROM recent_search_songs ORDER BY timestamp DESC LIMIT 15)")
    suspend fun trimRecentSearchSongs()

    @Transaction
    suspend fun addRecentSearchSong(song: RecentSearchSongEntity) {
        insertRecentSearchSong(song)
        trimRecentSearchSongs()
    }
}
