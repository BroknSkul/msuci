package com.musicplayer.app.service

import android.app.Service
import android.content.Intent
import android.os.IBinder
import com.musicplayer.app.domain.usecase.GetMetadataUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class MetadataService : Service() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    @Inject
    lateinit var getMetadataUseCase: GetMetadataUseCase

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val artist = intent?.getStringExtra("artist")
        val track = intent?.getStringExtra("track")

        if (artist != null && track != null) {
            scope.launch {
                try {
                    getMetadataUseCase(artist, track)
                    // Handle metadata update
                } catch (e: Exception) {
                    // Handle error
                }
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        job.cancel()
        super.onDestroy()
    }
}
