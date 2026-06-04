package com.musicplayer.app

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.media3.database.DatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.work.Configuration
import com.musicplayer.app.util.YoutubeDlUtil
import com.musicplayer.app.util.FFmpegUtil
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.Executors
import javax.inject.Inject

@HiltAndroidApp
class MusicApplication : Application(), Configuration.Provider, ImageLoaderFactory {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var dataSourceFactory: DataSource.Factory
    
    @Inject
    lateinit var cache: Cache

    @Inject
    lateinit var databaseProvider: DatabaseProvider

    private lateinit var downloadManager: DownloadManager
    private val appScope = CoroutineScope(Dispatchers.IO)

    override fun getWorkManagerConfiguration(): Configuration =
        Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        
        // Cleanup old abandoned cache folder
        appScope.launch {
            val oldCacheDir = File(getExternalFilesDir(null), "downloads")
            if (oldCacheDir.exists()) {
                oldCacheDir.deleteRecursively()
            }
        }

        // Initialize and Warm Up Extractors
        try {
            YoutubeDlUtil.init(this)
            FFmpegUtil.init(this)
        } catch (e: Throwable) {
            e.printStackTrace()
        }
        
        // Update YoutubeDL in the background
        appScope.launch {
            YoutubeDlUtil.updateYoutubeDL(this@MusicApplication)
        }

        try {
            initializeDownloadManager()
        } catch (e: Throwable) {
            e.printStackTrace()
        }
    }

    override fun newImageLoader(): ImageLoader {
        return ImageLoader.Builder(this)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(100L * 1024 * 1024) // 100MB
                    .build()
            }
            .respectCacheHeaders(false) // Increase speed for images with bad headers
            .build()
    }

    private fun initializeDownloadManager() {
        // Use the singleton cache and database provider provided by Hilt to avoid "Another SimpleCache instance" crash
        downloadManager = DownloadManager(
            this,
            databaseProvider,
            cache,
            dataSourceFactory,
            Executors.newFixedThreadPool(3)
        )
    }

    fun getDownloadManager(): DownloadManager = downloadManager
}
