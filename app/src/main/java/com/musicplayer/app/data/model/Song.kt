package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val thumbnailUrl: String,
    val streamUrl: String? = null,
    val localPath: String? = null,
    val isDownloaded: Boolean = false,
    val genre: String? = null,
    val lyrics: String? = null
)
