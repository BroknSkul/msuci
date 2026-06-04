package com.musicplayer.app.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SearchRepository
import com.musicplayer.app.data.repository.SongRepository
import com.musicplayer.app.worker.InstaPlaylistWorker
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.io.File
import java.util.*
import javax.inject.Inject

data class DownloadTask(
    val song: Song,
    val progress: Float
)

data class InstaPlaylistProgress(
    val name: String,
    val progress: Int,
    val status: String,
    val added: Int,
    val total: Int
)

@HiltViewModel
class LibraryViewModel @Inject constructor(
    application: Application,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository,
    private val searchRepository: SearchRepository
) : AndroidViewModel(application) {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    val downloadedSongs: StateFlow<List<Song>> = combine(
        songRepository.getDownloadedSongs(),
        _searchQuery
    ) { songs, query ->
        if (query.isBlank()) songs
        else songs.filter { 
            it.title.contains(query, ignoreCase = true) || 
            it.artist.contains(query, ignoreCase = true) 
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    private val _activeDownloads = MutableStateFlow<List<DownloadTask>>(emptyList())
    val activeDownloads: StateFlow<List<DownloadTask>> = _activeDownloads.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _instaPlaylistStatus = MutableStateFlow<String?>(null)
    val instaPlaylistStatus: StateFlow<String?> = _instaPlaylistStatus.asStateFlow()

    private val _activeInstaPlaylist = MutableStateFlow<InstaPlaylistProgress?>(null)
    val activeInstaPlaylist: StateFlow<InstaPlaylistProgress?> = _activeInstaPlaylist.asStateFlow()

    init {
        observeDownloads()
        observeInstaPlaylist()
    }

    private fun observeInstaPlaylist() {
        WorkManager.getInstance(getApplication())
            .getWorkInfosByTagLiveData("insta_playlist")
            .asFlow()
            .onEach { workInfos ->
                val activeInfo = workInfos.find { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                if (activeInfo != null) {
                    val progress = activeInfo.progress
                    _activeInstaPlaylist.value = InstaPlaylistProgress(
                        name = progress.getString("playlist_name") ?: "Creating...",
                        progress = progress.getInt("progress", 0),
                        status = progress.getString("status") ?: "Queued",
                        added = progress.getInt("added", 0),
                        total = progress.getInt("total", 0)
                    )
                } else {
                    _activeInstaPlaylist.value = null
                }
            }
            .launchIn(viewModelScope)
    }

    private fun observeDownloads() {
        WorkManager.getInstance(getApplication())
            .getWorkInfosByTagLiveData("download_task")
            .asFlow()
            .onEach { workInfos ->
                val tasks = mutableListOf<DownloadTask>()
                for (info in workInfos) {
                    if (info.state == WorkInfo.State.RUNNING || info.state == WorkInfo.State.ENQUEUED) {
                        val songId = info.tags.firstOrNull { it.startsWith("id_") }?.removePrefix("id_")
                        if (songId != null) {
                            viewModelScope.launch {
                                val song = songRepository.getSongById(songId)
                                if (song != null) {
                                    val progress = info.progress.getFloat("progress", 0f)
                                    val newTask = DownloadTask(song, progress)
                                    
                                    _activeDownloads.update { current ->
                                        val filtered = current.filter { it.song.id != songId }
                                        (filtered + newTask).sortedBy { it.song.title }
                                    }
                                }
                            }
                        }
                    }
                }
                val activeIds = workInfos
                    .filter { it.state == WorkInfo.State.RUNNING || it.state == WorkInfo.State.ENQUEUED }
                    .mapNotNull { info -> info.tags.firstOrNull { it.startsWith("id_") }?.removePrefix("id_") }
                    .toSet()
                
                _activeDownloads.update { it.filter { task -> activeIds.contains(task.song.id) } }
            }
            .launchIn(viewModelScope)
    }

    fun deleteSong(song: Song) {
        viewModelScope.launch {
            songRepository.deleteSong(song)
            // Delete physical files from music_storage (new location)
            song.localPath?.let { File(it).delete() }
            song.thumbnailUrl.let { if (it.startsWith("/")) File(it).delete() }
            val lyricsFile = File(getApplication<Application>().getExternalFilesDir(null), "music_storage/${song.id}.lrc")
            if (lyricsFile.exists()) lyricsFile.delete()
        }
    }

    fun togglePinnedStatus(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.togglePinnedStatus(playlist.id, !playlist.isPinned)
        }
    }

    fun deletePlaylist(playlist: Playlist) {
        viewModelScope.launch {
            playlistRepository.deletePlaylist(playlist.id)
        }
    }

    fun deletePlaylists(playlistIds: Set<String>) {
        viewModelScope.launch {
            playlistIds.forEach { id ->
                if (id != "favorites_playlist") {
                    playlistRepository.deletePlaylist(id)
                }
            }
        }
    }

    fun createPlaylist(name: String) {
        viewModelScope.launch {
            playlistRepository.createPlaylist(name)
        }
    }

    fun renamePlaylist(playlistId: String, newName: String) {
        viewModelScope.launch {
            playlistRepository.renamePlaylist(playlistId, newName)
        }
    }

    fun mergePlaylists(playlistIds: List<String>, newName: String) {
        viewModelScope.launch {
            val targetPlaylistId = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(newName, id = targetPlaylistId)
            
            playlistIds.forEach { sourceId ->
                val songs = playlistRepository.getSongsInPlaylist(sourceId).first()
                songs.forEach { song ->
                    playlistRepository.addSongToPlaylist(song.id, targetPlaylistId)
                }
            }
        }
    }

    fun createInstaPlaylist(name: String, songListText: String, onComplete: () -> Unit) {
        val inputData = androidx.work.workDataOf(
            "playlist_name" to name,
            "song_list" to songListText
        )
        
        val workRequest = androidx.work.OneTimeWorkRequestBuilder<com.musicplayer.app.worker.InstaPlaylistWorker>()
            .setInputData(inputData)
            .addTag("insta_playlist")
            .build()
            
        androidx.work.WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "insta_playlist_${System.currentTimeMillis()}",
            androidx.work.ExistingWorkPolicy.KEEP,
            workRequest
        )
        
        onComplete()
    }

    fun cancelInstaPlaylist() {
        WorkManager.getInstance(getApplication()).cancelAllWorkByTag("insta_playlist")
    }
}
