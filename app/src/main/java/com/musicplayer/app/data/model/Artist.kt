package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Artist(
    val id: String,
    val name: String,
    val imageUrl: String? = null,
    val songCount: Int = 0
)
