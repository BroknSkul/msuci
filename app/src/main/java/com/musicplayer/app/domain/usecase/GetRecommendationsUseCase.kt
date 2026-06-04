package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.repository.MetadataRepository
import javax.inject.Inject

class GetRecommendationsUseCase @Inject constructor(
    private val metadataRepository: MetadataRepository
) {
    suspend operator fun invoke(artist: String, track: String) =
        metadataRepository.getSimilarTracks(artist, track)
}
