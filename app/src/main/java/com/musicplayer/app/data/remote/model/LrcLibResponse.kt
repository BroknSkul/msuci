package com.musicplayer.app.data.remote.model

import com.google.gson.annotations.SerializedName

data class LrcLibResponse(
    @SerializedName("id")
    val id: Long? = null,
    @SerializedName("name")
    val name: String? = null,
    @SerializedName("trackName")
    val trackName: String? = null,
    @SerializedName("artistName")
    val artistName: String? = null,
    @SerializedName("albumName")
    val albumName: String? = null,
    @SerializedName("duration")
    val duration: Double? = null,
    @SerializedName("instrumental")
    val instrumental: Boolean? = null,
    @SerializedName("plainLyrics")
    val plainLyrics: String? = null,
    @SerializedName("syncedLyrics")
    val syncedLyrics: String? = null
)
