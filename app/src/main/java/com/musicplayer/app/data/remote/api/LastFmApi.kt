package com.musicplayer.app.data.remote.api

import com.musicplayer.app.data.remote.model.*
import retrofit2.http.GET
import retrofit2.http.Query

interface LastFmApi {
    @GET("?method=track.search&format=json")
    suspend fun searchTracks(
        @Query("track") query: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 30
    ): LastFmSearchResponse

    @GET("?method=track.getInfo&format=json")
    suspend fun getTrackInfo(
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("api_key") apiKey: String
    ): LastFmTrackResponse

    @GET("?method=artist.getInfo&format=json")
    suspend fun getArtistInfo(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String
    ): LastFmArtistInfoResponse

    @GET("?method=track.getSimilar&format=json")
    suspend fun getSimilarTracks(
        @Query("artist") artist: String,
        @Query("track") track: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): LastFmSimilarTracksResponse

    @GET("?method=artist.getTopTags&format=json")
    suspend fun getArtistTopTags(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String
    ): LastFmTopTagsResponse

    @GET("?method=artist.getSimilar&format=json")
    suspend fun getSimilarArtists(
        @Query("artist") artist: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 10
    ): LastFmSimilarArtistsResponse

    @GET("?method=tag.getTopTracks&format=json")
    suspend fun getTopTracksByTag(
        @Query("tag") tag: String,
        @Query("api_key") apiKey: String,
        @Query("limit") limit: Int = 20
    ): LastFmTopTracksResponse
}
