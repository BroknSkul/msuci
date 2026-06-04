package com.musicplayer.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.repository.PlaylistRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class PlaylistViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository
) : ViewModel() {

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _songsInPlaylist = MutableStateFlow<List<Song>>(emptyList())
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _currentPlaylist = MutableStateFlow<Playlist?>(null)
    val currentPlaylist: StateFlow<Playlist?> = _currentPlaylist.asStateFlow()

    val filteredSongs: StateFlow<List<Song>> = combine(_songsInPlaylist, _searchQuery) { songs, query ->
        if (query.isBlank()) {
            songs
        } else {
            songs.filter { 
                it.title.contains(query, ignoreCase = true) || 
                it.artist.contains(query, ignoreCase = true)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val songsInPlaylist: StateFlow<List<Song>> = _songsInPlaylist.asStateFlow()

    init {
        viewModelScope.launch {
            playlistRepository.getAllPlaylists().collect {
                _playlists.value = it
            }
        }
    }

    fun loadSongsInPlaylist(playlistId: String) {
        viewModelScope.launch {
            // Update current playlist info
            playlistRepository.getAllPlaylists().map { list ->
                list.find { it.id == playlistId }
            }.collect {
                _currentPlaylist.value = it
            }
        }
        viewModelScope.launch {
            playlistRepository.getSongsInPlaylist(playlistId).collect {
                _songsInPlaylist.value = it
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun removeSongFromPlaylist(songId: String, playlistId: String) {
        viewModelScope.launch {
            playlistRepository.removeSongFromPlaylist(songId, playlistId)
        }
    }

    fun toggleFavorite(song: Song) {
        viewModelScope.launch {
            val favoritesId = "favorites_playlist"
            val isFav = playlistRepository.isSongInPlaylist(song.id, favoritesId)
            if (isFav) {
                playlistRepository.removeSongFromPlaylist(song.id, favoritesId)
            } else {
                playlistRepository.addSongToPlaylist(song.id, favoritesId)
            }
        }
    }

    fun createPlaylist(name: String, description: String? = null) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name, description)
        }
    }

    fun addSongToPlaylist(songId: String, playlistId: String) {
        viewModelScope.launch {
            playlistRepository.addSongToPlaylist(songId, playlistId)
        }
    }

    fun updatePlaylistThumbnail(playlistId: String, thumbnailUrl: String?) {
        viewModelScope.launch {
            playlistRepository.updatePlaylistThumbnail(playlistId, thumbnailUrl)
        }
    }
}
