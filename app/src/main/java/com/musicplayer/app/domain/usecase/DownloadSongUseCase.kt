package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.model.Song
import javax.inject.Inject

class DownloadSongUseCase @Inject constructor() {
    suspend operator fun invoke(song: Song) {
        // Implementation for downloading song using WorkManager
    }
}
