package com.musicplayer.app.di

import android.util.Log
import com.musicplayer.app.data.remote.api.GeniusApi
import com.musicplayer.app.data.remote.api.LrcLibApi
import com.musicplayer.app.data.remote.api.LastFmApi
import com.musicplayer.app.data.remote.api.YoutubeApi
import com.musicplayer.app.data.remote.api.YouTubeMusicApiService
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import okhttp3.ConnectionPool
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor { message ->
            if (message.contains("Response") || message.contains("Content-Type") || message.contains("http")) {
                Log.d("OkHttp", message)
            }
        }
        logging.level = HttpLoggingInterceptor.Level.HEADERS
        
        return OkHttpClient.Builder()
            .addInterceptor(logging)
            .followRedirects(true)
            .followSslRedirects(true)
            // Increased connection pool for massive parallel stream resolution
            .connectionPool(ConnectionPool(30, 3, TimeUnit.MINUTES))
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    @Provides
    @Singleton
    fun provideLastFmApi(okHttpClient: OkHttpClient): LastFmApi {
        return Retrofit.Builder()
            .baseUrl("https://ws.audioscrobbler.com/2.0/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LastFmApi::class.java)
    }

    @Provides
    @Singleton
    fun provideLrcLibApi(okHttpClient: OkHttpClient): LrcLibApi {
        return Retrofit.Builder()
            .baseUrl("https://lrclib.net/api/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(LrcLibApi::class.java)
    }

    @Provides
    @Singleton
    fun provideGeniusApi(okHttpClient: OkHttpClient): GeniusApi {
        return Retrofit.Builder()
            .baseUrl("https://api.genius.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(GeniusApi::class.java)
    }

    @Provides
    @Singleton
    fun provideYoutubeApi(okHttpClient: OkHttpClient): YoutubeApi {
        return Retrofit.Builder()
            .baseUrl("https://www.googleapis.com/youtube/v3/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YoutubeApi::class.java)
    }

    @Provides
    @Singleton
    fun provideYouTubeMusicApiService(okHttpClient: OkHttpClient): YouTubeMusicApiService {
        return Retrofit.Builder()
            .baseUrl("https://music.youtube.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(YouTubeMusicApiService::class.java)
    }
}
