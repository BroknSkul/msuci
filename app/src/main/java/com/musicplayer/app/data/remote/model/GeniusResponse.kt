package com.musicplayer.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class GeniusSearchResponse(
    @SerializedName("response")
    val response: GeniusResponseData? = null
)

data class GeniusResponseData(
    @SerializedName("hits")
    val hits: List<GeniusHit>? = null
)

data class GeniusHit(
    @SerializedName("type")
    val type: String? = null,
    @SerializedName("result")
    val result: GeniusSong? = null
)

data class GeniusSong(
    @SerializedName("id")
    val id: Long? = null,
    @SerializedName("title")
    val title: String? = null,
    @SerializedName("artist_names")
    val artistNames: String? = null,
    @SerializedName("song_art_image_thumbnail_url")
    val songArtThumbnailUrl: String? = null,
    @SerializedName("primary_artist")
    val primaryArtist: GeniusArtist? = null,
    @SerializedName("url")
    val url: String? = null
)

data class GeniusArtist(
    @SerializedName("id")
    val id: Long? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("image_url")
    val imageUrl: String? = null,
    @SerializedName("header_image_url")
    val headerImageUrl: String? = null
)
