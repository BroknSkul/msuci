package com.musicplayer.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.data.local.database.SearchHistoryDao
import com.musicplayer.app.data.local.database.entity.SearchHistoryEntity
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.repository.SearchRepository
import com.musicplayer.app.data.repository.MetadataRepository
import com.musicplayer.app.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SearchViewModel @Inject constructor(
    private val searchRepository: SearchRepository,
    private val metadataRepository: MetadataRepository,
    private val searchHistoryDao: SearchHistoryDao,
    private val songRepository: SongRepository
) : ViewModel() {
    private val _searchResults = MutableStateFlow<List<Song>>(emptyList())
    val searchResults: StateFlow<List<Song>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    val searchHistory: StateFlow<List<SearchHistoryEntity>> = searchHistoryDao.getSearchHistory()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private var searchJob: Job? = null

    fun search(query: String) {
        searchJob?.cancel()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        
        searchJob = viewModelScope.launch {
            delay(150) // Faster debounce
            _isSearching.value = true
            try {
                // Use a flow to get raw results immediately, then enriched ones
                searchRepository.searchSongsFlow(query).collect { results ->
                    _searchResults.value = results
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                _isSearching.value = false
            }
        }
    }

    fun onSongClicked(song: Song, query: String) {
        viewModelScope.launch {
            // Save the query to history
            searchHistoryDao.addSearchQuery(
                SearchHistoryEntity(
                    query = query,
                    thumbnailUrl = song.thumbnailUrl
                )
            )
            // Save the song to recent search songs
            songRepository.addRecentSong(song)
        }
    }

    fun deleteHistoryItem(query: String) {
        viewModelScope.launch {
            searchHistoryDao.deleteSearchQuery(query)
        }
    }

    fun clearHistory() {
        viewModelScope.launch {
            searchHistoryDao.clearHistory()
        }
    }
}
