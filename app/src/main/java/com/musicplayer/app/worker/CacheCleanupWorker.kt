package com.musicplayer.app.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class CacheCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            // Only cleanup the standard cache directory, NOT the files/downloads directory
            val cacheDir = applicationContext.cacheDir
            cacheDir.listFiles()?.forEach { file ->
                if (file.name != "downloads") { // Protective check
                    file.deleteRecursively()
                }
            }
            Result.success()
        } catch (e: Exception) {
            Result.failure()
        }
    }
}
