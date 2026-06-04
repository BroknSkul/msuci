package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.repository.LyricsRepository
import javax.inject.Inject

class GetLyricsUseCase @Inject constructor(
    private val lyricsRepository: LyricsRepository
) {
    suspend operator fun invoke(
        songTitle: String,
        artist: String,
        duration: Int? = null,
        originalTitle: String? = null,
        originalArtist: String? = null
    ) = lyricsRepository.getLyrics(songTitle, artist, duration, originalTitle, originalArtist)
}
