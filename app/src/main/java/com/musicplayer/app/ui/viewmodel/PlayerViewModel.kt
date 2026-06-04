package com.musicplayer.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.musicplayer.app.BuildConfig
import com.musicplayer.app.data.model.LyricLine
import com.musicplayer.app.data.model.PlayerState
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.remote.api.YoutubeApi
import com.musicplayer.app.data.repository.LyricsRepository
import com.musicplayer.app.data.repository.MetadataRepository
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.util.YoutubeDlUtil
import com.musicplayer.app.util.LyricsParser
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import javax.inject.Inject

@HiltViewModel
class PlayerViewModel @Inject constructor(
    application: Application,
    val exoPlayer: ExoPlayer,
    private val playlistRepository: PlaylistRepository,
    private val lyricsRepository: LyricsRepository,
    private val metadataRepository: MetadataRepository,
    private val youtubeApi: YoutubeApi
) : AndroidViewModel(application) {

    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    val currentLyricIndex: StateFlow<Int> = combine(playerState, _syncedLyrics) { state, lines ->
        if (lines.isNullOrEmpty()) -1
        else {
            // Offset progress by 200ms to compensate for UI lag
            val adjustedProgress = state.progress + 200
            lines.indexOfLast { it.time <= adjustedProgress }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _queue = MutableStateFlow<List<Song>>(emptyList())
    val queue: StateFlow<List<Song>> = _queue.asStateFlow()

    private val _albumCoverUrl = MutableStateFlow<String?>(null)
    val albumCoverUrl: StateFlow<String?> = _albumCoverUrl.asStateFlow()

    private val youtubeApiKey = BuildConfig.YOUTUBE_API_KEY
    private val favoritesPlaylistId = "favorites_playlist"

    private val playerListener = object : Player.Listener {
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateDuration()
            _playerState.update { it.copy(isPlaying = exoPlayer.isPlaying) }
            
            if (playbackState == Player.STATE_ENDED) {
                skipNext()
            }
        }
        
        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateDuration()
        }

        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
        }

        override fun onPlayerError(error: PlaybackException) {
            Log.e("PlayerViewModel", "ExoPlayer Error: ${error.errorCodeName}", error)
            _lyrics.value = "Playback Error: ${error.localizedMessage}"
        }
    }

    private fun updateDuration() {
        val duration = exoPlayer.duration
        if (duration > 0) {
            _playerState.update { it.copy(duration = duration) }
        }
    }

    init {
        exoPlayer.addListener(playerListener)
        
        viewModelScope.launch {
            while (true) {
                try {
                    if (exoPlayer.playbackState != Player.STATE_IDLE) {
                        _playerState.update { state ->
                            state.copy(
                                progress = exoPlayer.currentPosition,
                                duration = if (exoPlayer.duration > 0) exoPlayer.duration else state.duration,
                                isPlaying = exoPlayer.isPlaying
                            )
                        }
                    }
                } catch (e: Exception) {
                    Log.e("PlayerViewModel", "Error updating progress", e)
                }
                delay(1000)
            }
        }
        
        viewModelScope.launch {
            playlistRepository.createPlaylist("Favorites", "Your favorite songs", favoritesPlaylistId)
        }
    }

    override fun onCleared() {
        super.onCleared()
        exoPlayer.removeListener(playerListener)
    }

    fun playSong(song: Song) {
        Log.d("PlayerViewModel", "Preparing to play: ${song.title}")
        _playerState.update { it.copy(
            currentSong = song, 
            isPlaying = false, 
            progress = 0, 
            duration = if (song.duration > 0) song.duration else it.duration 
        ) }
        _lyrics.value = "Fetching stream..."
        _syncedLyrics.value = null
        _albumCoverUrl.value = song.thumbnailUrl
        
        viewModelScope.launch {
            // Priority Resolution
            val resolvedUrl: String? = withContext(Dispatchers.IO) {
                try {
                    if (song.localPath != null && File(song.localPath).exists()) {
                        song.localPath
                    } else {
                        val urlToExtract = song.streamUrl ?: "https://www.youtube.com/watch?v=${song.id}"
                        YoutubeDlUtil.getVideoInfo(getApplication(), urlToExtract)?.url
                    }
                } catch (e: Exception) {
                    null
                }
            }

            // Secondary tasks in background
            launch { fetchLyrics(song) }
            launch { fetchRecommendations(song.id) }
            launch { fetchLastFmMetadata(song) }
            checkIfFavorite(song.id)
            
            if (resolvedUrl != null) {
                Log.d("PlayerViewModel", "Setting MediaItem with URL: ${resolvedUrl.take(100)}...")
                val mediaItemBuilder = MediaItem.Builder()
                    .setUri(resolvedUrl)
                    .setMediaId(song.id)
                
                // HYPER-ACCELERATED: Skip format discovery
                val mimeType = when {
                    resolvedUrl.contains("manifest/dash") || resolvedUrl.contains(".mpd") || resolvedUrl.contains("/dash/") -> MimeTypes.APPLICATION_MPD
                    resolvedUrl.contains("mime=audio%2Fmp4") || resolvedUrl.contains("mime=audio/mp4") -> MimeTypes.AUDIO_MP4
                    resolvedUrl.contains("mime=audio%2Fwebm") || resolvedUrl.contains("mime=audio/webm") -> MimeTypes.AUDIO_WEBM
                    resolvedUrl.contains("googlevideo.com") -> MimeTypes.AUDIO_MP4
                    else -> null
                }
                
                if (mimeType != null) {
                    mediaItemBuilder.setMimeType(mimeType)
                }

                exoPlayer.setMediaItem(mediaItemBuilder.build())
                exoPlayer.prepare()
                exoPlayer.play()
                _playerState.update { it.copy(isPlaying = true) }
                _lyrics.value = "Playing..."
            } else {
                Log.e("PlayerViewModel", "All extraction methods failed for: ${song.title}")
                _lyrics.value = "Error: Video unavailable."
            }
        }
    }

    private suspend fun fetchLyrics(song: Song) {
        try {
            val durationSeconds = if (song.duration > 0) (song.duration / 1000).toInt() else null
            val result = lyricsRepository.getLyrics(
                songTitle = song.title,
                artist = song.artist,
                duration = durationSeconds,
                originalTitle = song.title,
                originalArtist = song.artist
            )
            _lyrics.value = result
            _syncedLyrics.value = LyricsParser.parse(result)
        } catch (e: Exception) {
            _lyrics.value = "Failed to load lyrics."
        }
    }

    private suspend fun fetchLastFmMetadata(song: Song) {
        try {
            val response = metadataRepository.getTrackInfo(song.artist, song.title)
            val images = response.track?.album?.image
            val largeImage = images?.find { it.size == "extralarge" } ?: images?.find { it.size == "large" }
            if (largeImage != null && !largeImage.url.isNullOrBlank() && !largeImage.url.contains("default_album")) {
                _albumCoverUrl.value = largeImage.url
            }
        } catch (e: Exception) {
            Log.e("PlayerViewModel", "LastFm error", e)
        }
    }

    private fun fetchRecommendations(videoId: String) {
        viewModelScope.launch {
            try {
                if (youtubeApiKey.isNotBlank()) {
                    val response = youtubeApi.search(
                        relatedToVideoId = videoId,
                        type = "video",
                        apiKey = youtubeApiKey
                    )
                    _queue.value = response.items.map { item ->
                        Song(
                            id = item.id.videoId,
                            title = item.snippet.title,
                            artist = item.snippet.channelTitle,
                            album = "Recommended",
                            duration = 0L,
                            thumbnailUrl = item.snippet.thumbnails.high.url,
                            streamUrl = "https://www.youtube.com/watch?v=${item.id.videoId}"
                        )
                    }
                }
            } catch (e: Exception) {
                Log.e("PlayerViewModel", "Recommendations error", e)
            }
        }
    }

    private fun checkIfFavorite(songId: String) {
        viewModelScope.launch {
            _isFavorite.value = playlistRepository.isSongInPlaylist(songId, favoritesPlaylistId)
        }
    }

    fun toggleFavorite() {
        val song = _playerState.value.currentSong ?: return
        viewModelScope.launch {
            if (_isFavorite.value) {
                playlistRepository.removeSongFromPlaylist(song.id, favoritesPlaylistId)
                _isFavorite.value = false
            } else {
                playlistRepository.addSongToPlaylist(song.id, favoritesPlaylistId)
                _isFavorite.value = true
            }
        }
    }

    fun togglePlayPause() {
        if (exoPlayer.isPlaying) {
            exoPlayer.pause()
        } else {
            if (exoPlayer.playbackState == Player.STATE_ENDED) {
                exoPlayer.seekTo(0)
            }
            exoPlayer.play()
        }
    }

    fun skipNext() {
        if (_queue.value.isNotEmpty()) {
            val nextSong = _queue.value.first()
            _queue.update { it.drop(1) }
            playSong(nextSong)
        }
    }

    fun skipPrevious() {
        if (exoPlayer.currentPosition > 5000) {
            exoPlayer.seekTo(0)
        } else {
            exoPlayer.seekTo(0)
        }
    }

    fun seekTo(position: Long) {
        exoPlayer.seekTo(position)
    }
    
    fun addToQueue(song: Song) {
        _queue.update { it + song }
    }
}
