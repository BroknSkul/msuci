package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.repository.SearchRepository
import javax.inject.Inject

class SearchSongsUseCase @Inject constructor(
    private val searchRepository: SearchRepository
) {
    suspend operator fun invoke(query: String): List<Song> {
        return searchRepository.searchSongs(query)
    }
}
