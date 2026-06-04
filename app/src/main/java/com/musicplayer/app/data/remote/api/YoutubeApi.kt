package com.musicplayer.app.data.remote.api

import com.musicplayer.app.data.remote.model.YoutubeSearchResponse
import com.musicplayer.app.data.remote.model.YoutubeVideoListResponse
import retrofit2.http.GET
import retrofit2.http.Query

interface YoutubeApi {
    @GET("search")
    suspend fun search(
        @Query("part") part: String = "snippet",
        @Query("q") query: String? = null,
        @Query("relatedToVideoId") relatedToVideoId: String? = null,
        @Query("type") type: String = "video",
        @Query("videoCategoryId") categoryId: String? = null,
        @Query("maxResults") maxResults: Int = 20,
        @Query("key") apiKey: String
    ): YoutubeSearchResponse

    @GET("videos")
    suspend fun getVideoDetails(
        @Query("part") part: String = "contentDetails,snippet",
        @Query("id") ids: String,
        @Query("key") apiKey: String
    ): YoutubeVideoListResponse
}
