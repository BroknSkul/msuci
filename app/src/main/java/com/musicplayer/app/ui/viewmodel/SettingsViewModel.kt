package com.musicplayer.app.ui.viewmodel

import android.app.Application
import android.content.Context
import androidx.annotation.OptIn
import androidx.core.content.edit
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SongRepository
import com.musicplayer.app.playback.MusicPlayer
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class SettingsViewModel @Inject constructor(
    application: Application,
    private val musicPlayer: MusicPlayer,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("settings", Context.MODE_PRIVATE)

    private val _isGaplessPlaybackEnabled = MutableStateFlow(prefs.getBoolean("gapless_playback", true))
    val isGaplessPlaybackEnabled: StateFlow<Boolean> = _isGaplessPlaybackEnabled.asStateFlow()

    val playlists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _exportMessage = MutableSharedFlow<String>()
    val exportMessage = _exportMessage.asSharedFlow()

    fun toggleGaplessPlayback(enabled: Boolean) {
        _isGaplessPlaybackEnabled.value = enabled
        prefs.edit { putBoolean("gapless_playback", enabled) }
    }

    fun clearCache() {
        viewModelScope.launch {
            // 1. Get all downloaded songs to find their files
            val downloadedSongs = songRepository.getDownloadedSongsSync()
            
            // 2. Delete physical files for each song from music_storage (new correct location)
            val downloadDir = File(getApplication<Application>().getExternalFilesDir(null), "music_storage")
            if (downloadDir.exists()) {
                downloadDir.listFiles()?.forEach { it.delete() }
            }
            
            // 3. Update database: reset local paths and isDownloaded status
            for (song in downloadedSongs) {
                songRepository.insertSong(song.copy(localPath = null, isDownloaded = false))
            }

            // 4. Clear the in-memory stream URL cache in MusicPlayer
            musicPlayer.clearStreamUrlCache()
            
            _exportMessage.emit("Cache cleared successfully")
        }
    }

    fun exportPlaylist(playlist: Playlist) {
        viewModelScope.launch {
            try {
                // Use the repository to get the songs for export
                val songs = playlistRepository.getSongsInPlaylist(playlist.id).first()
                if (songs.isEmpty()) {
                    _exportMessage.emit("Playlist is empty")
                    return@launch
                }
                
                val content = songs.joinToString("\n") { "${it.title} - ${it.artist}" }
                val fileName = "${playlist.name.replace(" ", "_")}_export.txt"
                
                val resolver = getApplication<Application>().contentResolver
                val contentValues = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
                    }
                }
                
                val uri = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                    resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)
                } else {
                    // Fallback for older versions
                    val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)
                    val file = File(downloadsDir, fileName)
                    file.writeText(content)
                    android.net.Uri.fromFile(file)
                }

                if (uri != null) {
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        resolver.openOutputStream(uri)?.use { outputStream ->
                            outputStream.write(content.toByteArray())
                        }
                    }
                    _exportMessage.emit("Exported to Downloads/$fileName")
                } else {
                    _exportMessage.emit("Failed to create file in Downloads")
                }
            } catch (e: Exception) {
                _exportMessage.emit("Error: ${e.localizedMessage}")
            }
        }
    }
}
