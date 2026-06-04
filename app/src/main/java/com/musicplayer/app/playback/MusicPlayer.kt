package com.musicplayer.app.playback

import android.content.ComponentName
import android.content.Context
import android.util.Log
import androidx.annotation.OptIn
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.CacheWriter
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.HttpDataSource
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import com.musicplayer.app.data.model.PlayerState
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.service.PlaybackService
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import javax.inject.Inject
import javax.inject.Singleton

@UnstableApi
@Singleton
class MusicPlayer @Inject constructor(
    @ApplicationContext private val context: Context,
    private val cache: Cache,
    private val httpDataSourceFactory: HttpDataSource.Factory
) {
    private val tag = "MusicPlayer"
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    
    private val prefetchScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    private val _playerState = MutableStateFlow(PlayerState())
    val playerState: StateFlow<PlayerState> = _playerState.asStateFlow()
    
    private val scope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private var progressJob: Job? = null
    private var isManuallyLoading = false

    private var pendingSongToPlay: Pair<Song, String>? = null

    private val streamUrlCache = mutableMapOf<String, String>()

    init {
        initializeController()
    }

    @OptIn(UnstableApi::class)
    private fun initializeController() {
        Log.d(tag, "Initializing MediaController...")
        val sessionToken = SessionToken(context, ComponentName(context, PlaybackService::class.java))
        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                mediaController?.addListener(playerListener)
                
                updateStateFromController()
                Log.d(tag, "MediaController initialized successfully")
                
                pendingSongToPlay?.let { (song, url) ->
                    playSongInternal(song, url)
                    pendingSongToPlay = null
                }
            } catch (e: Exception) {
                Log.e(tag, "Failed to initialize MediaController", e)
            }
        }, MoreExecutors.directExecutor())
    }

    private val playerListener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            _playerState.update { it.copy(isPlaying = isPlaying) }
            if (isPlaying) startProgressUpdate() else stopProgressUpdate()
        }

        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY || playbackState == Player.STATE_ENDED) {
                isManuallyLoading = false
            }
            updateStateFromController()
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            updateStateFromController()
            mediaController?.volume = 1f
        }

        override fun onTimelineChanged(timeline: Timeline, reason: Int) {
            updateStateFromController()
        }
        
        override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
            Log.e(tag, "Player error: ${error.message}", error)
            isManuallyLoading = false
            updateStateFromController()
        }
    }

    private fun updateStateFromController() {
        val controller = mediaController ?: return
        
        val currentQueue = mutableListOf<Song>()
        val timeline = controller.currentTimeline
        
        if (!timeline.isEmpty) {
            val window = Timeline.Window()
            for (i in 0 until timeline.windowCount) {
                timeline.getWindow(i, window)
                val mediaItem = window.mediaItem
                val metadata = mediaItem.mediaMetadata
                currentQueue.add(
                    Song(
                        id = mediaId(mediaItem),
                        title = metadata.title?.toString() ?: "Unknown",
                        artist = metadata.artist?.toString() ?: "Unknown",
                        album = "YouTube",
                        duration = window.durationMs,
                        thumbnailUrl = metadata.artworkUri?.toString() ?: "",
                        streamUrl = null
                    )
                )
            }
        }

        val currentIndex = controller.currentMediaItemIndex
        val upNext = if (currentQueue.isNotEmpty() && currentIndex < currentQueue.size) {
            currentQueue.drop(currentIndex + 1)
        } else {
            emptyList()
        }

        _playerState.update { state ->
            state.copy(
                isPlaying = controller.isPlaying,
                progress = controller.currentPosition,
                duration = controller.duration.coerceAtLeast(0L),
                queue = upNext,
                isLoading = isManuallyLoading || controller.playbackState == Player.STATE_BUFFERING,
                isShuffleEnabled = controller.shuffleModeEnabled,
                currentSong = controller.currentMediaItem?.let { item ->
                    val metadata = item.mediaMetadata
                    Song(
                        id = mediaId(item),
                        title = metadata.title?.toString() ?: "Unknown",
                        artist = metadata.artist?.toString() ?: "Unknown",
                        album = "YouTube",
                        duration = controller.duration.coerceAtLeast(0L),
                        thumbnailUrl = metadata.artworkUri?.toString() ?: "",
                        streamUrl = null
                    )
                } ?: state.currentSong
            )
        }

        if (controller.isPlaying) {
            startProgressUpdate()
        }
    }

    @OptIn(UnstableApi::class)
    private fun mediaId(item: MediaItem): String = item.mediaId

    fun stop() {
        isManuallyLoading = false
        pendingSongToPlay = null
        mediaController?.let {
            it.stop()
            it.clearMediaItems()
        }
        _playerState.update { it.copy(isPlaying = false, currentSong = null, progress = 0L, isLoading = false) }
    }

    fun setLoading(isLoading: Boolean) {
        isManuallyLoading = isLoading
        _playerState.update { it.copy(isLoading = isLoading) }
    }

    fun updateCurrentSong(song: Song) {
        _playerState.update { it.copy(currentSong = song) }
    }

    /**
     * Updates the metadata (title, artist, artwork) for a specific song in the current queue.
     */
    fun updateSongMetadata(song: Song) {
        val controller = mediaController ?: return
        val timeline = controller.currentTimeline
        if (timeline.isEmpty) return

        val window = Timeline.Window()
        for (i in 0 until timeline.windowCount) {
            timeline.getWindow(i, window)
            val mediaItem = window.mediaItem
            if (mediaItem.mediaId == song.id) {
                // If it's the current song or in queue, update it
                val updatedMetadata = mediaItem.mediaMetadata.buildUpon()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.thumbnailUrl.toUri())
                    .build()
                
                controller.replaceMediaItem(i, mediaItem.buildUpon()
                    .setMediaMetadata(updatedMetadata)
                    .build())
                break
            }
        }
    }

    private fun createMediaItem(song: Song, url: String): MediaItem {
        val builder = MediaItem.Builder()
            .setUri(url)
            .setMediaId(song.id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(song.title)
                    .setArtist(song.artist)
                    .setArtworkUri(song.thumbnailUrl.toUri())
                    .build()
            )
            
        // SNIFFING SUPPRESSION: Extract MimeType from URL to skip format discovery
        val mimeType = getMimeTypeFromUrl(url)
        if (mimeType != null) {
            builder.setMimeType(mimeType)
        }
        
        return builder.build()
    }

    private fun getMimeTypeFromUrl(url: String): String? {
        if (url.contains("manifest/dash") || url.contains(".mpd") || url.contains("/dash/")) {
            return MimeTypes.APPLICATION_MPD
        }
        if (url.contains("manifest/hls") || url.contains(".m3u8")) {
            return MimeTypes.APPLICATION_M3U8
        }
        
        // Check for common YouTube/GoogleVideo mime parameters
        if (url.contains("mime=audio%2Fmp4") || url.contains("mime=audio/mp4")) return MimeTypes.AUDIO_MP4
        if (url.contains("mime=audio%2Fwebm") || url.contains("mime=audio/webm")) return MimeTypes.AUDIO_WEBM
        if (url.contains("mime=video%2Fmp4") || url.contains("mime=video/mp4")) return MimeTypes.AUDIO_MP4
        if (url.contains("mime=video%2Fwebm") || url.contains("mime=video/webm")) return MimeTypes.AUDIO_WEBM
        
        if (url.contains("googlevideo.com")) {
            // Default YouTube fallback: most audio-only streams are M4A (mp4)
            return MimeTypes.AUDIO_MP4 
        }
        return null
    }

    fun playSong(song: Song) {
        val url = streamUrlCache[song.id] ?: song.streamUrl
        if (url == null) {
            isManuallyLoading = false
            updateStateFromController()
            return
        }

        val controller = mediaController
        if (controller == null) {
            pendingSongToPlay = song to url
            return
        }

        playSongInternal(song, url)
    }

    private fun playSongInternal(song: Song, url: String) {
        // SECTIONED LOADING: Start parallel prefetching of 4 sections
        primeCacheInSections(url)

        mediaController?.let { controller ->
            val mediaItem = createMediaItem(song, url)
            controller.setMediaItem(mediaItem)
            controller.prepare()
            controller.play()
            controller.volume = 1f
        }
    }

    private fun primeCacheInSections(url: String) {
        if (url.startsWith("http")) {
            prefetchScope.launch {
                // Parallel fetch key sections to saturate bandwidth and ensure smooth high-bitrate playback
                val sections = listOf(
                    0L..512000L,        // Section 1: Start (0-500KB)
                    512001L..1024000L,   // Section 2: Immediate Next (500KB-1MB)
                    2000000L..2512000L, // Section 3: Mid-buffer (2MB)
                    4000000L..4512000L, // Section 4: Deep-buffer (4MB)
                    8000000L..8512000L  // Section 5: Late-buffer (8MB)
                )
                
                sections.forEach { range ->
                    launch {
                        try {
                            val dataSpec = DataSpec.Builder()
                                .setUri(url.toUri())
                                .setPosition(range.first)
                                .setLength(range.last - range.first)
                                .setFlags(DataSpec.FLAG_ALLOW_CACHE_FRAGMENTATION)
                                .build()
                            
                            val cacheWriter = CacheWriter(
                                CacheDataSource(cache, httpDataSourceFactory.createDataSource()),
                                dataSpec,
                                null,
                                null
                            )
                            cacheWriter.cache()
                        } catch (e: Exception) {
                            // Suppress prefetch errors
                        }
                    }
                }
            }
        }
    }

    fun addToQueue(song: Song) {
        mediaController?.let { controller ->
            val url = streamUrlCache[song.id] ?: song.streamUrl ?: ""
            if (url.isNotEmpty()) {
                val mediaItem = createMediaItem(song, url)
                controller.addMediaItem(mediaItem)
            }
        }
    }

    fun addNext(song: Song) {
        mediaController?.let { controller ->
            val url = streamUrlCache[song.id] ?: song.streamUrl ?: ""
            if (url.isNotEmpty()) {
                val mediaItem = createMediaItem(song, url)
                val insertIndex = if (controller.mediaItemCount == 0) 0 else controller.currentMediaItemIndex + 1
                controller.addMediaItem(insertIndex, mediaItem)
            }
        }
    }

    fun moveQueueItem(fromIndex: Int, toIndex: Int) {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            val absoluteFrom = currentIndex + 1 + fromIndex
            val absoluteTo = currentIndex + 1 + toIndex
            if (absoluteFrom < controller.mediaItemCount && absoluteTo < controller.mediaItemCount) {
                controller.moveMediaItem(absoluteFrom, absoluteTo)
            }
        }
    }

    fun removeQueueItem(index: Int) {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            val absoluteIndex = currentIndex + 1 + index
            if (absoluteIndex < controller.mediaItemCount) {
                controller.removeMediaItem(absoluteIndex)
            }
        }
    }

    fun cacheStreamUrl(songId: String, url: String) {
        streamUrlCache[songId] = url
    }

    fun getCachedUrl(songId: String): String? = streamUrlCache[songId]

    fun clearStreamUrlCache() {
        streamUrlCache.clear()
    }

    fun skipToQueueItem(index: Int) {
        mediaController?.let { controller ->
            val currentIndex = controller.currentMediaItemIndex
            val absoluteIndex = currentIndex + 1 + index
            if (absoluteIndex < controller.mediaItemCount) {
                controller.seekToDefaultPosition(absoluteIndex)
                controller.play()
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { if (it.isPlaying) it.pause() else it.play() }
    }

    fun seekTo(position: Long) {
        mediaController?.seekTo(position)
    }

    fun skipNext() {
        mediaController?.seekToNext()
    }

    fun skipPrevious() {
        mediaController?.seekToPrevious()
    }

    fun setShuffleEnabled(enabled: Boolean) {
        mediaController?.shuffleModeEnabled = enabled
        _playerState.update { it.copy(isShuffleEnabled = enabled) }
    }

    fun setRepeatMode(mode: Int) {
        mediaController?.repeatMode = mode
        _playerState.update { it.copy(repeatMode = mode) }
    }

    fun toggleShuffle() {
        mediaController?.let {
            val nextShuffle = !it.shuffleModeEnabled
            it.shuffleModeEnabled = nextShuffle
            _playerState.update { state -> state.copy(isShuffleEnabled = nextShuffle) }
        }
    }

    private fun startProgressUpdate() {
        if (progressJob?.isActive == true) return
        progressJob = scope.launch {
            while (isActive) {
                updateStateFromController()
                delay(100)
            }
        }
    }

    private fun stopProgressUpdate() {
        progressJob?.cancel()
        progressJob = null
    }
}
