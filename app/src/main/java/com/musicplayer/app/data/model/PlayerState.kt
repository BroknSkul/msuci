package com.musicplayer.app.data.model

data class PlayerState(
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Long = 0L,
    val duration: Long = 0L,
    val queue: List<Song> = emptyList(),
    val repeatMode: Int = 0, // 0: Off, 1: One, 2: All
    val isShuffleEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val isGlassMode: Boolean = false,
    val wallpaperPath: String? = null
)
