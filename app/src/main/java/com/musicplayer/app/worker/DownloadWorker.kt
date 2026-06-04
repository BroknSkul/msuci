package com.musicplayer.app.worker

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.ListenableWorker
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.musicplayer.app.R
import com.musicplayer.app.data.repository.LyricsRepository
import com.musicplayer.app.data.repository.SongRepository
import com.musicplayer.app.data.repository.NotificationRepository
import com.musicplayer.app.util.YoutubeDlUtil
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject
import kotlinx.coroutines.runBlocking
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import androidx.work.WorkManager

@HiltWorker
class DownloadWorker @AssistedInject constructor(
    @Assisted private val context: Context,
    @Assisted params: WorkerParameters,
    private val songRepository: SongRepository,
    private val lyricsRepository: LyricsRepository,
    private val notificationRepository: NotificationRepository
) : CoroutineWorker(context, params) {

    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val channelId = "download_channel"
    private var notificationId = 1001

    override suspend fun doWork(): ListenableWorker.Result {
        val songId = inputData.getString("song_id") ?: return ListenableWorker.Result.failure()
        notificationId = songId.hashCode()
        val songUrl = inputData.getString("song_url") ?: return ListenableWorker.Result.failure()
        
        Log.d("DownloadWorker", "Starting download for song: $songId")
        
        val song = songRepository.getSongById(songId) ?: return ListenableWorker.Result.failure()

        createNotificationChannel()
        try {
            setForeground(createForegroundInfo(song.title, 0))
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Failed to set foreground mode", e)
        }

        // Changed directory name to "music_storage" to avoid conflict with Media3 SimpleCache
        val destinationDir = File(applicationContext.getExternalFilesDir(null), "music_storage")
        if (!destinationDir.exists()) destinationDir.mkdirs()

        // 1. Download Audio
        val audioFilePath = YoutubeDlUtil.downloadAudio(applicationContext, songUrl, destinationDir, songId) { progress ->
            if (isStopped) return@downloadAudio
            
            runBlocking {
                // Ensure song is available in UI by setting progress
                setProgress(workDataOf("progress" to progress, "song_id" to songId))
                
                // Update system notification
                try {
                    notificationManager.notify(notificationId, createNotification(song.title, progress.toInt()))
                } catch (e: Exception) {}
            }
        }

        if (isStopped) {
            Log.d("DownloadWorker", "Download stopped/cancelled for song: $songId")
            cleanupFailedDownload(destinationDir, songId)
            return ListenableWorker.Result.failure()
        }

        if (audioFilePath == null) {
            Log.e("DownloadWorker", "Audio download failed for song: $songId")
            return ListenableWorker.Result.failure()
        }

        // 2. Download Album Cover (Thumbnail)
        if (song.thumbnailUrl.startsWith("http")) {
            try {
                val thumbnailFile = File(destinationDir, "$songId.jpg")
                val client = okhttp3.OkHttpClient.Builder()
                    .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .readTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
                    .build()
                val request = okhttp3.Request.Builder()
                    .url(song.thumbnailUrl)
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
                    .build()
                
                client.newCall(request).execute().use { response ->
                    if (response.isSuccessful) {
                        response.body?.byteStream()?.use { input ->
                            FileOutputStream(thumbnailFile).use { output ->
                                input.copyTo(output)
                            }
                        }
                        Log.d("DownloadWorker", "Thumbnail downloaded locally for $songId")
                    }
                }
            } catch (e: Exception) {
                Log.e("DownloadWorker", "Thumbnail download failed for $songId: ${e.message}")
            }
        }

        // 3. Download Lyrics
        var downloadedLyrics: String? = null
        try {
            val lyrics = lyricsRepository.getLyrics(song.title, song.artist)
            if (!lyrics.isNullOrBlank() && !lyrics.contains("Failed to load") && !lyrics.contains("not found")) {
                val lyricsFile = File(destinationDir, "$songId.lrc")
                lyricsFile.writeText(lyrics)
                downloadedLyrics = lyrics
            }
        } catch (e: Exception) {
            Log.e("DownloadWorker", "Lyrics download failed", e)
        }

        Log.d("DownloadWorker", "Download package complete: $audioFilePath")
        songRepository.insertSong(song.copy(
            localPath = audioFilePath, 
            isDownloaded = true,
            lyrics = downloadedLyrics ?: song.lyrics
        ))
        
        showDownloadCompleteNotification(song.title)
        notificationRepository.insertNotification("Download Complete", song.title, "download")
        
        return ListenableWorker.Result.success()
    }

    private fun cleanupFailedDownload(dir: File, songId: String) {
        listOf("$songId.mp3", "$songId.jpg", "$songId.lrc").forEach {
            val file = File(dir, it)
            if (file.exists()) file.delete()
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                "Song Downloads",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows progress of song downloads"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createForegroundInfo(songTitle: String, progress: Int): ForegroundInfo {
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
        } else {
            0
        }
        return ForegroundInfo(notificationId, createNotification(songTitle, progress), type)
    }

    private fun createNotification(songTitle: String, progress: Int): android.app.Notification {
        val cancelIntent = WorkManager.getInstance(context).createCancelPendingIntent(id)
        
        return NotificationCompat.Builder(context, channelId)
            .setContentTitle("Downloading $songTitle")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setColor(android.graphics.Color.BLACK)
            .setColorized(true)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "Cancel", cancelIntent)
            .build()
    }

    private fun showDownloadCompleteNotification(songTitle: String) {
        val notification = NotificationCompat.Builder(context, channelId)
            .setContentTitle("Download Complete")
            .setContentText(songTitle)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setAutoCancel(true)
            .setColor(android.graphics.Color.BLACK)
            .setColorized(true)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .build()
        notificationManager.notify(notificationId + 1, notification)
    }
}
