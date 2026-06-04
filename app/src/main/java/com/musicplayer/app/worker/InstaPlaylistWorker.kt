package com.musicplayer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SearchRepository
import com.musicplayer.app.data.repository.SongRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.firstOrNull
import java.util.UUID

import androidx.work.workDataOf

@HiltWorker
class InstaPlaylistWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val searchRepository: SearchRepository,
    private val songRepository: SongRepository,
    private val playlistRepository: PlaylistRepository
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "insta_playlist_channel"
    private val notificationId = 2001

    override suspend fun doWork(): Result {
        val playlistName = inputData.getString("playlist_name") ?: "New Playlist"
        val songListText = inputData.getString("song_list") ?: return Result.failure()
        
        createNotificationChannel()
        
        // Try to set foreground but catch exceptions to prevent crashes on Android 12+ background restrictions
        try {
            setForeground(createForegroundInfo("Starting playlist creation..."))
        } catch (e: Exception) {
            Log.e("InstaPlaylistWorker", "Failed to set foreground mode", e)
        }

        try {
            val playlistId = UUID.randomUUID().toString()
            playlistRepository.createPlaylist(playlistName, id = playlistId)
            
            val lines = songListText.split("\n").filter { it.isNotBlank() }.map { it.trim() }
            val total = lines.size
            var addedCount = 0
            val failedQueries = mutableListOf<String>()

            lines.forEachIndexed { index, query ->
                if (isStopped) return Result.failure()
                
                val currentProgress = (index * 100) / total
                val statusMessage = "Processing: ${index + 1}/$total"
                updateNotification("$statusMessage\nAdded: $addedCount", currentProgress)
                
                // Update WorkManager progress for UI
                setProgress(workDataOf(
                    "progress" to currentProgress,
                    "status" to statusMessage,
                    "added" to addedCount,
                    "total" to total,
                    "playlist_name" to playlistName
                ))
                
                try {
                    val results = searchRepository.searchSongsFlow(query).firstOrNull { it.isNotEmpty() } ?: emptyList()
                    val topResult = results.firstOrNull()
                    
                    if (topResult != null) {
                        songRepository.insertSong(topResult)
                        playlistRepository.addSongToPlaylist(topResult.id, playlistId)
                        addedCount++
                    } else {
                        failedQueries.add(query)
                    }
                } catch (e: Exception) {
                    Log.e("InstaPlaylistWorker", "Error processing: $query", e)
                    failedQueries.add(query)
                }
                
                delay(800) // Rate limit protection
            }

            // Retry failed ones
            if (failedQueries.isNotEmpty()) {
                failedQueries.toList().forEachIndexed { index, query ->
                    if (isStopped) return Result.failure()
                    val statusMessage = "Retrying failed songs: ${index + 1}/${failedQueries.size}"
                    updateNotification(statusMessage, 90)
                    
                    setProgress(workDataOf(
                        "progress" to 90,
                        "status" to statusMessage,
                        "added" to addedCount,
                        "total" to total,
                        "playlist_name" to playlistName
                    ))

                    try {
                        val results = searchRepository.searchSongs(query)
                        val topResult = results.firstOrNull()
                        if (topResult != null) {
                            songRepository.insertSong(topResult)
                            playlistRepository.addSongToPlaylist(topResult.id, playlistId)
                            addedCount++
                        }
                        delay(1500)
                    } catch (e: Exception) {}
                }
            }

            showCompleteNotification(playlistName, addedCount, total)
            return Result.success()
        } catch (e: Exception) {
            Log.e("InstaPlaylistWorker", "Failed to create insta-playlist", e)
            return Result.failure()
        }
    }

    private fun createNotificationChannel() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            val channel = NotificationChannel(channelId, "Playlist Creation", NotificationManager.IMPORTANCE_LOW)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(message: String): ForegroundInfo {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Creating Insta-Playlist")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setOngoing(true)
            .build()
        
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        
        return if (type != 0) {
            ForegroundInfo(notificationId, notification, type)
        } else {
            ForegroundInfo(notificationId, notification)
        }
    }

    private fun updateNotification(message: String, progress: Int) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Creating Insta-Playlist")
            .setContentText(message)
            .setSmallIcon(android.R.drawable.stat_notify_sync)
            .setProgress(100, progress, false)
            .setOngoing(true)
            .build()
        notificationManager.notify(notificationId, notification)
    }

    private fun showCompleteNotification(name: String, added: Int, total: Int) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Playlist Created")
            .setContentText("Added $added/$total songs to '$name'")
            .setSmallIcon(android.R.drawable.stat_notify_sync_noanim)
            .setAutoCancel(true)
            .build()
        notificationManager.notify(notificationId + 1, notification)
    }
}
