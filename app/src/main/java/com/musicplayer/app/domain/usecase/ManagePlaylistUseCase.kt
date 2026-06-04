package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.repository.PlaylistRepository
import javax.inject.Inject

class ManagePlaylistUseCase @Inject constructor(
    private val playlistRepository: PlaylistRepository
) {
    suspend fun createPlaylist(name: String, description: String? = null) {
        playlistRepository.createPlaylist(name, description)
    }

    suspend fun addSongToPlaylist(songId: String, playlistId: String) {
        playlistRepository.addSongToPlaylist(songId, playlistId)
    }
}
