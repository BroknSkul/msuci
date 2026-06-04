package com.musicplayer.app.data.remote.api

import com.musicplayer.app.data.remote.model.GeniusSearchResponse
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Query

interface GeniusApi {
    @GET("search")
    suspend fun search(
        @Query("q") query: String,
        @Header("Authorization") auth: String
    ): GeniusSearchResponse
}
