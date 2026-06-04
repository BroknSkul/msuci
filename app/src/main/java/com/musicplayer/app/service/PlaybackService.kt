package com.musicplayer.app.service

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.RemoteViews
import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands
import androidx.media3.session.SessionResult
import coil.Coil
import coil.request.ImageRequest
import coil.transform.CircleCropTransformation
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.musicplayer.app.MainActivity
import com.musicplayer.app.R
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SongRepository
import com.musicplayer.app.widget.MusicWidgetLargeProvider
import com.musicplayer.app.widget.MusicWidgetMediumProvider
import com.musicplayer.app.widget.MusicWidgetProvider
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import javax.inject.Inject

@UnstableApi
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {

    @Inject
    lateinit var player: ExoPlayer

    @Inject
    lateinit var songRepository: SongRepository

    @Inject
    lateinit var playlistRepository: PlaylistRepository

    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())
    private val favoritesPlaylistId = "favorites_playlist"

    private var favoriteStatusJob: Job? = null
    private var isCurrentFavorite = false

    companion object {
        const val ACTION_FAVORITE = "com.musicplayer.app.ACTION_FAVORITE"
        const val ACTION_REPEAT = "com.musicplayer.app.ACTION_REPEAT"
        const val ACTION_PLAY_PAUSE = "com.musicplayer.app.ACTION_PLAY_PAUSE"
        const val ACTION_PREV = "com.musicplayer.app.ACTION_PREV"
        const val ACTION_NEXT = "com.musicplayer.app.ACTION_NEXT"
        const val UPDATE_WIDGETS = "com.musicplayer.app.UPDATE_WIDGETS"

        // Modernized colors
        const val COLOR_FAV_ACTIVE = 0xFFFF2D55.toInt()
        const val COLOR_WHITE = 0xFFFFFFFF.toInt()
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("PlaybackService", "PlaybackService onCreate")
        
        val sessionCallback = object : MediaSession.Callback {
            override fun onConnect(
                session: MediaSession,
                controller: MediaSession.ControllerInfo
            ): MediaSession.ConnectionResult {
                val sessionCommands = SessionCommands.Builder()
                    .add(SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
                    .add(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
                    .build()
                
                return MediaSession.ConnectionResult.accept(
                    sessionCommands,
                    MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS
                )
            }

            override fun onPostConnect(session: MediaSession, controller: MediaSession.ControllerInfo) {
                updateNotificationLayout()
                updateWidgets()
            }

            override fun onCustomCommand(
                session: MediaSession,
                controller: MediaSession.ControllerInfo,
                customCommand: SessionCommand,
                args: Bundle
            ): ListenableFuture<SessionResult> {
                when (customCommand.customAction) {
                    ACTION_FAVORITE -> toggleFavorite()
                    ACTION_REPEAT -> toggleRepeatMode()
                }
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
        }

        mediaSession = MediaSession.Builder(this, player)
            .setCallback(sessionCallback)
            .setSessionActivity(getActivityPendingIntent())
            .build()

        player.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                observeFavoriteStatus(mediaItem?.mediaId)
                updateWidgets()
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                updateNotificationLayout()
                updateWidgets()
            }

            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateNotificationLayout()
                updateWidgets()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateWidgets()
            }

            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_REPEAT_MODE_CHANGED,
                        Player.EVENT_PLAYBACK_STATE_CHANGED,
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED
                    )
                ) {
                    updateNotificationLayout()
                    updateWidgets()
                }
            }
        })
        
        observeFavoriteStatus(player.currentMediaItem?.mediaId)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> if (player.isPlaying) player.pause() else player.play()
            ACTION_PREV -> player.seekToPrevious()
            ACTION_NEXT -> player.seekToNext()
            UPDATE_WIDGETS -> updateWidgets()
            ACTION_FAVORITE -> toggleFavorite()
            ACTION_REPEAT -> toggleRepeatMode()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun getActivityPendingIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java).apply {
            action = "OPEN_PLAYER"
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
    }

    private fun observeFavoriteStatus(songId: String?) {
        favoriteStatusJob?.cancel()
        if (songId == null) {
            isCurrentFavorite = false
            updateNotificationLayout()
            updateWidgets()
            return
        }
        favoriteStatusJob = serviceScope.launch {
            playlistRepository.getSongsInPlaylist(favoritesPlaylistId)
                .map { songs -> songs.any { it.id == songId } }
                .distinctUntilChanged()
                .collect { favorite ->
                    isCurrentFavorite = favorite
                    updateNotificationLayout()
                    updateWidgets()
                }
        }
    }

    private fun toggleFavorite() {
        val mediaItem = player.currentMediaItem ?: return
        val songId = mediaItem.mediaId
        
        serviceScope.launch {
            try {
                if (isCurrentFavorite) {
                    playlistRepository.removeSongFromPlaylist(songId, favoritesPlaylistId)
                } else {
                    val existingSong = songRepository.getSongById(songId)
                    if (existingSong == null) {
                        val metadata = mediaItem.mediaMetadata
                        val newSong = Song(
                            id = songId,
                            title = metadata.title?.toString() ?: "Unknown",
                            artist = metadata.artist?.toString() ?: "Unknown Artist",
                            album = metadata.albumTitle?.toString() ?: "YouTube",
                            duration = player.duration,
                            thumbnailUrl = metadata.artworkUri?.toString() ?: "",
                            isDownloaded = false
                        )
                        songRepository.insertSong(newSong)
                    }
                    playlistRepository.addSongToPlaylist(songId, favoritesPlaylistId)
                }
            } catch (e: Exception) {
                Log.e("PlaybackService", "Error toggling favorite", e)
            }
        }
    }

    private fun toggleRepeatMode() {
        val nextMode = when (player.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        player.repeatMode = nextMode
    }

    private fun getCustomLayout(): List<CommandButton> {
        val favoriteButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_FAVORITE, Bundle.EMPTY))
            .setIconResId(if (isCurrentFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline)
            .setDisplayName("Favorite")
            .setEnabled(true)
            .build()

        val repeatIcon = if (player.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
        val repeatButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand(ACTION_REPEAT, Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setDisplayName("Repeat")
            .setEnabled(true)
            .build()

        return listOf(favoriteButton, repeatButton)
    }

    private fun updateNotificationLayout() {
        mediaSession?.setCustomLayout(ImmutableList.copyOf(getCustomLayout()))
    }

    private fun updateWidgets() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        updateWidgetType(appWidgetManager, MusicWidgetProvider::class.java, R.layout.widget_small)
        updateWidgetType(appWidgetManager, MusicWidgetMediumProvider::class.java, R.layout.widget_medium)
        updateWidgetType(appWidgetManager, MusicWidgetLargeProvider::class.java, R.layout.widget_large)
    }

    private fun updateWidgetType(manager: AppWidgetManager, providerClass: Class<*>, layoutId: Int) {
        val component = ComponentName(this, providerClass)
        val ids = manager.getAppWidgetIds(component)
        if (ids.isEmpty()) return

        for (appWidgetId in ids) {
            val views = RemoteViews(packageName, layoutId)
            val metadata = player.mediaMetadata
            val options = manager.getAppWidgetOptions(appWidgetId)
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)

            views.setTextViewText(R.id.widget_title, metadata.title ?: "Not Playing")
            
            // App click intent
            views.setOnClickPendingIntent(R.id.widget_root, getActivityPendingIntent())

            if (layoutId != R.layout.widget_small) {
                views.setTextViewText(R.id.widget_artist, metadata.artist ?: "Unknown Artist")
                
                val artworkUri = metadata.artworkUri
                if (artworkUri != null) {
                    serviceScope.launch {
                        try {
                            val imageLoader = Coil.imageLoader(this@PlaybackService)
                            val request = ImageRequest.Builder(this@PlaybackService)
                                .data(artworkUri)
                                .transformations(CircleCropTransformation())
                                .size(200)
                                .build()
                            val result = imageLoader.execute(request)
                            val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                            if (bitmap != null) {
                                views.setImageViewBitmap(R.id.widget_album_art, bitmap)
                                manager.updateAppWidget(appWidgetId, views)
                            }
                        } catch (e: Exception) {
                            Log.e("PlaybackService", "Widget art load failed", e)
                        }
                    }
                } else {
                    views.setImageViewResource(R.id.widget_album_art, R.drawable.ic_widget_placeholder)
                }
            }

            // Modernized Controls: Clean White Icons on Transparent Backgrounds
            val playPauseIcon = if (player.isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            views.setImageViewResource(R.id.widget_play_pause, playPauseIcon)
            views.setInt(R.id.widget_play_pause, "setColorFilter", COLOR_WHITE)
            
            views.setOnClickPendingIntent(R.id.widget_play_pause, getWidgetPendingIntent(ACTION_PLAY_PAUSE))
            views.setOnClickPendingIntent(R.id.widget_prev, getWidgetPendingIntent(ACTION_PREV))
            views.setOnClickPendingIntent(R.id.widget_next, getWidgetPendingIntent(ACTION_NEXT))

            // Dynamic visibility
            val isLarge = layoutId == R.layout.widget_large
            val showExtra = isLarge || width > 200
            val extraVis = if (showExtra) View.VISIBLE else View.GONE
            
            views.setViewVisibility(R.id.widget_favorite, extraVis)
            views.setViewVisibility(R.id.widget_repeat, extraVis)

            if (showExtra) {
                val favIcon = if (isCurrentFavorite) R.drawable.ic_heart_filled else R.drawable.ic_heart_outline
                views.setImageViewResource(R.id.widget_favorite, favIcon)
                views.setInt(R.id.widget_favorite, "setColorFilter", if (isCurrentFavorite) COLOR_FAV_ACTIVE else COLOR_WHITE)
                views.setOnClickPendingIntent(R.id.widget_favorite, getWidgetPendingIntent(ACTION_FAVORITE))

                val isActive = player.repeatMode != Player.REPEAT_MODE_OFF
                val repeatIcon = if (player.repeatMode == Player.REPEAT_MODE_ONE) R.drawable.ic_repeat_one else R.drawable.ic_repeat
                
                views.setImageViewResource(R.id.widget_repeat, repeatIcon)
                views.setInt(R.id.widget_repeat, "setColorFilter", COLOR_WHITE)
                views.setInt(R.id.widget_repeat, "setAlpha", if (isActive) 255 else 100)

                views.setOnClickPendingIntent(R.id.widget_repeat, getWidgetPendingIntent(ACTION_REPEAT))
            }

            if (layoutId == R.layout.widget_large) {
                val currentIndex = player.currentMediaItemIndex
                val queueCount = player.mediaItemCount
                
                if (queueCount > currentIndex + 1) {
                    val nextItem = player.getMediaItemAt(currentIndex + 1)
                    views.setViewVisibility(R.id.queue_item_1, View.VISIBLE)
                    views.setTextViewText(R.id.queue_title_1, nextItem.mediaMetadata.title ?: "Next Track")
                    views.setTextViewText(R.id.queue_artist_1, nextItem.mediaMetadata.artist ?: "Unknown")
                    
                    val nextArtworkUri = nextItem.mediaMetadata.artworkUri
                    if (nextArtworkUri != null) {
                        serviceScope.launch {
                             val imageLoader = Coil.imageLoader(this@PlaybackService)
                             val request = ImageRequest.Builder(this@PlaybackService).data(nextArtworkUri).size(100).build()
                             val result = imageLoader.execute(request)
                             val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                             if (bitmap != null) {
                                 views.setImageViewBitmap(R.id.queue_art_1, bitmap)
                                 manager.updateAppWidget(appWidgetId, views)
                             }
                        }
                    } else {
                        views.setImageViewResource(R.id.queue_art_1, R.drawable.ic_widget_placeholder)
                    }

                    if (queueCount > currentIndex + 2) {
                        val nextItem2 = player.getMediaItemAt(currentIndex + 2)
                        views.setViewVisibility(R.id.queue_item_2, View.VISIBLE)
                        views.setTextViewText(R.id.queue_title_2, nextItem2.mediaMetadata.title ?: "Next Track")
                        views.setTextViewText(R.id.queue_artist_2, nextItem2.mediaMetadata.artist ?: "Unknown")
                        
                        val nextArtworkUri2 = nextItem2.mediaMetadata.artworkUri
                        if (nextArtworkUri2 != null) {
                            serviceScope.launch {
                                 val imageLoader = Coil.imageLoader(this@PlaybackService)
                                 val request = ImageRequest.Builder(this@PlaybackService).data(nextArtworkUri2).size(100).build()
                                 val result = imageLoader.execute(request)
                                 val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                 if (bitmap != null) {
                                     views.setImageViewBitmap(R.id.queue_art_2, bitmap)
                                     manager.updateAppWidget(appWidgetId, views)
                                 }
                            }
                        } else {
                            views.setImageViewResource(R.id.queue_art_2, R.drawable.ic_widget_placeholder)
                        }

                        if (queueCount > currentIndex + 3) {
                            val nextItem3 = player.getMediaItemAt(currentIndex + 3)
                            views.setViewVisibility(R.id.queue_item_3, View.VISIBLE)
                            views.setTextViewText(R.id.queue_title_3, nextItem3.mediaMetadata.title ?: "Next Track")
                            views.setTextViewText(R.id.queue_artist_3, nextItem3.mediaMetadata.artist ?: "Unknown")
                            
                            val nextArtworkUri3 = nextItem3.mediaMetadata.artworkUri
                            if (nextArtworkUri3 != null) {
                                serviceScope.launch {
                                     val imageLoader = Coil.imageLoader(this@PlaybackService)
                                     val request = ImageRequest.Builder(this@PlaybackService).data(nextArtworkUri3).size(100).build()
                                     val result = imageLoader.execute(request)
                                     val bitmap = (result.drawable as? BitmapDrawable)?.bitmap
                                     if (bitmap != null) {
                                         views.setImageViewBitmap(R.id.queue_art_3, bitmap)
                                         manager.updateAppWidget(appWidgetId, views)
                                     }
                                }
                            } else {
                                views.setImageViewResource(R.id.queue_art_3, R.drawable.ic_widget_placeholder)
                            }
                        } else {
                            views.setViewVisibility(R.id.queue_item_3, View.GONE)
                        }
                    } else {
                        views.setViewVisibility(R.id.queue_item_2, View.GONE)
                        views.setViewVisibility(R.id.queue_item_3, View.GONE)
                    }
                } else {
                    views.setViewVisibility(R.id.queue_item_1, View.GONE)
                    views.setViewVisibility(R.id.queue_item_2, View.GONE)
                    views.setViewVisibility(R.id.queue_item_3, View.GONE)
                }
            }

            manager.updateAppWidget(appWidgetId, views)
        }
    }

    private fun getWidgetPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlaybackService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(this, action.hashCode(), intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        Log.d("PlaybackService", "onDestroy")
        favoriteStatusJob?.cancel()
        serviceScope.cancel()
        mediaSession?.let { session ->
            session.player.release()
            session.release()
            mediaSession = null
        }
        super.onDestroy()
    }
}
