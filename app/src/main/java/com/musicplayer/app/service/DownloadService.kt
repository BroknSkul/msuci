package com.musicplayer.app.service

import android.app.Notification
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Scheduler
import com.musicplayer.app.util.Constants

@OptIn(UnstableApi::class)
class MusicDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL,
    Constants.NOTIFICATION_CHANNEL_ID,
    0,
    0
) {
    override fun getDownloadManager(): DownloadManager {
        return (application as com.musicplayer.app.MusicApplication).getDownloadManager()
    }

    override fun getScheduler(): Scheduler? = null

    override fun getForegroundNotification(
        downloads: MutableList<Download>,
        notMetRequirements: Int
    ): Notification {
        return DownloadNotificationHelper(this, Constants.NOTIFICATION_CHANNEL_ID)
            .buildProgressNotification(
                this,
                android.R.drawable.stat_sys_download,
                null,
                null,
                downloads,
                notMetRequirements
            )
    }

    companion object {
        private const val NOTIFICATION_ID = 101
    }
}
