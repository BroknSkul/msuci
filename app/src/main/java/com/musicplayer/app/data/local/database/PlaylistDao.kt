package com.musicplayer.app.data.local.database

import androidx.room.*
import com.musicplayer.app.data.local.database.entity.PlaylistEntity
import com.musicplayer.app.data.local.database.entity.PlaylistSongCrossRef
import com.musicplayer.app.data.local.database.entity.SongEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PlaylistDao {
    @Query("""
        SELECT p.*, (SELECT COUNT(*) FROM PlaylistSongCrossRef WHERE playlistId = p.id) as songCount 
        FROM playlists p 
        ORDER BY p.isPinned DESC, p.name ASC
    """)
    fun getAllPlaylistsWithCount(): Flow<List<PlaylistWithCount>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET isPinned = :isPinned WHERE id = :playlistId")
    suspend fun updatePinnedStatus(playlistId: String, isPinned: Boolean)

    @Query("UPDATE playlists SET name = :newName WHERE id = :playlistId")
    suspend fun renamePlaylist(playlistId: String, newName: String)

    @Query("UPDATE playlists SET thumbnailUrl = :thumbnailUrl WHERE id = :playlistId")
    suspend fun updateThumbnail(playlistId: String, thumbnailUrl: String?)

    @Query("DELETE FROM playlists WHERE id = :playlistId")
    suspend fun deletePlaylist(playlistId: String)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId")
    suspend fun deletePlaylistSongs(playlistId: String)

    @Query("DELETE FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeSongFromPlaylist(playlistId: String, songId: String)

    @RewriteQueriesToDropUnusedColumns
    @Transaction
    @Query("SELECT * FROM songs INNER JOIN PlaylistSongCrossRef ON songs.id = PlaylistSongCrossRef.songId WHERE PlaylistSongCrossRef.playlistId = :playlistId")
    fun getSongsInPlaylist(playlistId: String): Flow<List<SongEntity>>

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertSongToPlaylist(crossRef: PlaylistSongCrossRef)

    @Query("SELECT EXISTS(SELECT 1 FROM PlaylistSongCrossRef WHERE playlistId = :playlistId AND songId = :songId)")
    suspend fun isSongInPlaylist(playlistId: String, songId: String): Boolean
}

data class PlaylistWithCount(
    @Embedded val playlist: PlaylistEntity,
    val songCount: Int
)
