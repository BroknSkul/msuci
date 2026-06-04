package com.musicplayer.app.di

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.util.UnstableApi
import androidx.media3.database.DatabaseProvider
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.HttpDataSource
import androidx.media3.datasource.cache.Cache
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.extractor.DefaultExtractorsFactory
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.Dns
import okhttp3.OkHttpClient
import java.io.File
import java.net.InetAddress
import java.net.Inet6Address
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@OptIn(UnstableApi::class)
@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    
    // Custom DNS to prioritize IPv4 and avoid the 20-second IPv6 fallback delay
    private val fastDns = object : Dns {
        override fun lookup(hostname: String): List<InetAddress> {
            return try {
                val addresses = Dns.SYSTEM.lookup(hostname)
                addresses.sortedBy { it is Inet6Address }
            } catch (e: Exception) {
                emptyList()
            }
        }
    }

    @Provides
    @Singleton
    fun provideAudioAttributes(): AudioAttributes =
        AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

    @Provides
    @Singleton
    fun provideHttpDataSourceFactory(okHttpClient: OkHttpClient): HttpDataSource.Factory {
        val playbackClient = okHttpClient.newBuilder()
            .connectionPool(ConnectionPool(100, 5, TimeUnit.MINUTES)) 
            .dns(fastDns)
            .connectTimeout(5, TimeUnit.SECONDS) 
            .readTimeout(10, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()

        return OkHttpDataSource.Factory(playbackClient)
            .setUserAgent("com.google.android.youtube/19.05.36 (Linux; U; Android 11; en_US)")
            .setDefaultRequestProperties(mapOf(
                "Referer" to "https://www.youtube.com/",
                "Origin" to "https://www.youtube.com",
                "X-YouTube-Client-Name" to "3",
                "X-YouTube-Client-Version" to "19.05.36"
            ))
    }

    @Provides
    @Singleton
    fun provideDatabaseProvider(@ApplicationContext context: Context): DatabaseProvider =
        StandaloneDatabaseProvider(context)

    @Provides
    @Singleton
    fun provideCache(@ApplicationContext context: Context, databaseProvider: DatabaseProvider): Cache {
        val cacheDir = File(context.cacheDir, "media_cache")
        if (!cacheDir.exists()) cacheDir.mkdirs()
        return SimpleCache(cacheDir, LeastRecentlyUsedCacheEvictor(250 * 1024 * 1024), databaseProvider)
    }

    @Provides
    @Singleton
    fun provideDataSourceFactory(
        @ApplicationContext context: Context,
        httpDataSourceFactory: HttpDataSource.Factory,
        cache: Cache
    ): DataSource.Factory {
        val upstreamFactory = DefaultDataSource.Factory(context, httpDataSourceFactory)
        return CacheDataSource.Factory()
            .setCache(cache)
            .setUpstreamDataSourceFactory(upstreamFactory)
            .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
    }

    @Provides
    @Singleton
    fun provideExoPlayer(
        @ApplicationContext context: Context,
        audioAttributes: AudioAttributes,
        dataSourceFactory: DataSource.Factory
    ): ExoPlayer {
        val extractorsFactory = DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            
        val mediaSourceFactory = DefaultMediaSourceFactory(context, extractorsFactory)
            .setDataSourceFactory(dataSourceFactory)

        // HYPER-AGGRESSIVE BUFFERING: Play as soon as first 300ms segment is ready
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                1000,   // minBufferMs (Start buffering 1s)
                30000,  // maxBufferMs (Increased to 30s for high bitrate tracks)
                500,    // bufferForPlaybackMs (Play after 0.5s buffered)
                1000    // bufferForPlaybackAfterRebufferMs
            )
            .setBackBuffer(10000, true) // Keep 10s in back buffer for instant seeking
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        return ExoPlayer.Builder(context)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setSeekParameters(androidx.media3.exoplayer.SeekParameters.CLOSEST_SYNC)
            .build()
    }
}
