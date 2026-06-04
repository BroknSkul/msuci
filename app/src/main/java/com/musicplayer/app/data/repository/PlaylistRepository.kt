package com.musicplayer.app.data.repository

import com.musicplayer.app.data.local.database.PlaylistDao
import com.musicplayer.app.data.local.database.entity.PlaylistEntity
import com.musicplayer.app.data.local.database.entity.PlaylistSongCrossRef
import com.musicplayer.app.data.local.database.entity.toDomain
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlaylistRepository @Inject constructor(
    private val playlistDao: PlaylistDao
) {
    fun getAllPlaylists(): Flow<List<Playlist>> = playlistDao.getAllPlaylistsWithCount().map { entities ->
        entities.map { it.playlist.toDomain(it.songCount) }
    }

    suspend fun createPlaylist(name: String, description: String? = null, id: String? = null) {
        val finalId = id ?: java.util.UUID.randomUUID().toString()
        playlistDao.insertPlaylist(PlaylistEntity(finalId, name, description, null))
    }

    suspend fun renamePlaylist(playlistId: String, newName: String) {
        playlistDao.renamePlaylist(playlistId, newName)
    }

    suspend fun updatePlaylistThumbnail(playlistId: String, thumbnailUrl: String?) {
        playlistDao.updateThumbnail(playlistId, thumbnailUrl)
    }

    suspend fun togglePinnedStatus(playlistId: String, isPinned: Boolean) {
        playlistDao.updatePinnedStatus(playlistId, isPinned)
    }

    suspend fun deletePlaylist(playlistId: String) {
        if (playlistId == "favorites_playlist") return // Protect favorites
        playlistDao.deletePlaylistSongs(playlistId)
        playlistDao.deletePlaylist(playlistId)
    }

    fun getSongsInPlaylist(playlistId: String): Flow<List<Song>> = 
        playlistDao.getSongsInPlaylist(playlistId).map { entities ->
            entities.map { it.toDomain() }
        }

    suspend fun addSongToPlaylist(songId: String, playlistId: String) {
        playlistDao.insertSongToPlaylist(PlaylistSongCrossRef(playlistId, songId))
    }

    suspend fun removeSongFromPlaylist(songId: String, playlistId: String) {
        playlistDao.removeSongFromPlaylist(playlistId, songId)
    }

    suspend fun isSongInPlaylist(songId: String, playlistId: String): Boolean {
        return playlistDao.isSongInPlaylist(playlistId, songId)
    }
}

fun PlaylistEntity.toDomain(songCount: Int = 0) = Playlist(
    id = id,
    name = name,
    description = description,
    thumbnailUrl = thumbnailUrl,
    isPinned = isPinned,
    songCount = songCount
)
