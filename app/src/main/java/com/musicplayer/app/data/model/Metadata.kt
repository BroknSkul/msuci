package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Metadata(
    val id: String,
    val genre: String? = null,
    val releaseDate: String? = null,
    val description: String? = null,
    val albumInfo: String? = null
)
