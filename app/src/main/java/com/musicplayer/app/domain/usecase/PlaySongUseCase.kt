package com.musicplayer.app.domain.usecase

import com.musicplayer.app.data.model.Song
import com.musicplayer.app.ui.viewmodel.MusicViewModel
import javax.inject.Inject

class PlaySongUseCase @Inject constructor() {
    operator fun invoke(song: Song, musicViewModel: MusicViewModel) {
        musicViewModel.playSong(song)
    }
}
