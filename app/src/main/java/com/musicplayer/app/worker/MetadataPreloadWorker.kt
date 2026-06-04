package com.musicplayer.app.worker

import android.content.Context
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.musicplayer.app.data.repository.MetadataRepository
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

@HiltWorker
class MetadataPreloadWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val metadataRepository: MetadataRepository
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val artist = inputData.getString("artist") ?: return Result.failure()
        val track = inputData.getString("track") ?: return Result.failure()

        return try {
            metadataRepository.getTrackInfo(artist, track)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
