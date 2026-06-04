package com.musicplayer.app.data.remote.api

import com.musicplayer.app.data.remote.model.LrcLibResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface LrcLibApi {
    @GET("get")
    suspend fun getLyrics(
        @Query("artist_name") artist: String,
        @Query("track_name") title: String,
        @Query("album_name") album: String? = null,
        @Query("duration") duration: Int? = null
    ): LrcLibResponse?

    @GET("search")
    suspend fun search(
        @Query("q") query: String
    ): List<LrcLibResponse>
}
