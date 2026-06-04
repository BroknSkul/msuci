package com.musicplayer.app.data.model

import kotlinx.serialization.Serializable

@Serializable
data class SearchResult(
    val songs: List<Song> = emptyList(),
    val playlists: List<Playlist> = emptyList()
)
