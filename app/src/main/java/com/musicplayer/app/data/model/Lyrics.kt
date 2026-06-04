package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Lyrics(
    val songId: String,
    val content: String,
    val source: String? = null
)
