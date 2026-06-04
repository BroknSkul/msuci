package com.musicplayer.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.remote.model.LastFmArtistInfo
import com.musicplayer.app.data.repository.MetadataRepository
import com.musicplayer.app.data.repository.SearchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ArtistDetailViewModel @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val searchRepository: SearchRepository
) : ViewModel() {

    private val _artistInfo = MutableStateFlow<LastFmArtistInfo?>(null)
    val artistInfo: StateFlow<LastFmArtistInfo?> = _artistInfo.asStateFlow()

    private val _highResImageUrl = MutableStateFlow<String?>(null)
    val highResImageUrl: StateFlow<String?> = _highResImageUrl.asStateFlow()

    private val _topTracks = MutableStateFlow<List<Song>>(emptyList())
    val topTracks: StateFlow<List<Song>> = _topTracks.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    fun loadArtistDetails(artistName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // 1. Fetch basic info (bio, stats) from Last.fm
                val info = metadataRepository.getArtistInfo(artistName)
                _artistInfo.value = info
                
                // 2. Fetch High-Res Image (with fallback to Genius to avoid the "Star" placeholder)
                val imageUrl = metadataRepository.getArtistImage(artistName)
                _highResImageUrl.value = imageUrl
                
                // 3. Get top tracks via search
                val results = searchRepository.searchSongs(artistName)
                _topTracks.value = results.take(15)
                
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }
}
