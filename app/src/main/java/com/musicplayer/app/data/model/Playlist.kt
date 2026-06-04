package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Playlist(
    val id: String,
    val name: String,
    val description: String? = null,
    val thumbnailUrl: String? = null,
    val songCount: Int = 0,
    val isPinned: Boolean = false
)
