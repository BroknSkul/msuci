package com.musicplayer.app.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.repository.MetadataRepository
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SongRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

data class ArtistDisplayData(
    val name: String,
    val imageUrl: String? = null
)

@HiltViewModel
class HomeViewModel @Inject constructor(
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val metadataRepository: MetadataRepository
) : ViewModel() {

    private val FAVORITES_PLAYLIST_ID = "favorites_playlist"

    val favoriteSongs: StateFlow<List<Song>> = playlistRepository.getSongsInPlaylist(FAVORITES_PLAYLIST_ID)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .map { list -> list.filter { it.id != "downloads_playlist" } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val recentSongs: StateFlow<List<Song>> = songRepository.recentPlayedSongs
    val recentSearchSongs: StateFlow<List<Song>> = songRepository.recentSearchSongs

    private val _recentArtistsData = MutableStateFlow<List<ArtistDisplayData>>(emptyList())
    val recentArtistsData: StateFlow<List<ArtistDisplayData>> = _recentArtistsData.asStateFlow()

    private val fetchingArtists = mutableSetOf<String>()
    private val enrichedSongs = mutableSetOf<String>()

    init {
        // Observe recent songs and update artist data with images
        viewModelScope.launch {
            recentSongs.collect { songs ->
                val artistNames = songs.map { extractArtist(it.title, it.artist) }
                    .filter { it.isNotBlank() }
                    .distinct()
                    .take(10)
                
                // Merge current image data with new artist list
                val currentDataMap = _recentArtistsData.value.associateBy { it.name }
                val updatedData = artistNames.map { name ->
                    currentDataMap[name] ?: ArtistDisplayData(name)
                }
                
                _recentArtistsData.value = updatedData
                
                // Fetch missing images for each artist
                updatedData.forEach { artist ->
                    if (artist.imageUrl == null && !fetchingArtists.contains(artist.name)) {
                        fetchArtistImage(artist.name)
                    }
                }

                // Check for songs needing album cover enrichment
                songs.forEach { song ->
                    if (shouldEnrichSong(song)) {
                        enrichRecentSong(song)
                    }
                }
            }
        }
    }

    private fun shouldEnrichSong(song: Song): Boolean {
        if (enrichedSongs.contains(song.id)) return false
        val url = song.thumbnailUrl
        // Check if it's a YouTube thumbnail
        return url.contains("ytimg.com") || url.contains("youtube.com") || url.isBlank()
    }

    private fun enrichRecentSong(song: Song) {
        viewModelScope.launch {
            enrichedSongs.add(song.id)
            try {
                val (artist, title) = extractArtistAndTitle(song.artist, song.title)
                val albumCover = metadataRepository.getAlbumCover(artist, title)
                if (!albumCover.isNullOrBlank()) {
                    // Update the song in the repository, which will update the DB and trigger Flow updates
                    songRepository.insertSong(song.copy(thumbnailUrl = albumCover))
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error enriching recent song metadata", e)
            }
        }
    }

    private fun extractArtistAndTitle(artist: String, title: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", " | ", ": ", " ~ ")
        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2)
                val extractedArtist = parts[0].trim()
                val extractedTitle = cleanMetadata(parts[1].trim())
                if (extractedArtist.isNotEmpty() && !extractedArtist.contains(Regex("(?i)official|video|music|vevo|channel"))) {
                    return Pair(extractedArtist, extractedTitle)
                }
            }
        }
        return Pair(artist, cleanMetadata(title))
    }

    private fun cleanMetadata(text: String): String {
        val cleanRegex = Regex("""(?i)\(\s*official.*?\s*\)|\[\s*official.*?\s*\]|\(\s*video.*?\s*\)|\[\s*video.*?\s*\]|\(\s*lyrics.*?\s*\)|\[\s*lyrics.*?\s*\]|\(\s*audio.*?\s*\)|\[\s*audio.*?\s*\]|\(\s*hd.*?\s*\)|\[\s*hd.*?\s*\]|\(\s*4k.*?\s*\)|\[\s*4k.*?\s*\]|official|video|music|lyrics|audio|hd|4k|ft\..+|feat\..+""")
        return text.replace(cleanRegex, "")
            .trim()
            .replace(Regex("""\s+"""), " ")
    }

    private fun fetchArtistImage(name: String) {
        if (name.isBlank()) return
        
        viewModelScope.launch {
            fetchingArtists.add(name)
            try {
                val imageUrl = metadataRepository.getArtistImage(name)
                if (!imageUrl.isNullOrBlank()) {
                    _recentArtistsData.update { currentList ->
                        currentList.map { 
                            if (it.name == name) it.copy(imageUrl = imageUrl) else it 
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("HomeViewModel", "Error fetching artist image", e)
            }
        }
    }

    fun addRecentSong(song: Song) {
        songRepository.addRecentPlayedSong(song)
    }

    private fun extractArtist(title: String, originalArtist: String): String {
        val separators = listOf(" - ", " – ", " — ", " | ", " : ")
        val byIndex = title.lowercase().indexOf(" by ")
        if (byIndex != -1) {
            val potentialArtist = title.substring(byIndex + 4).trim()
            if (potentialArtist.isNotEmpty()) return potentialArtist
        }
        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep)
                if (parts.isNotEmpty()) {
                    val part = parts[0].trim()
                    if (part.isNotEmpty()) return part
                }
            }
        }
        if (title.contains("-")) {
            val parts = title.split("-")
            if (parts.isNotEmpty()) {
                val part = parts[0].trim()
                if (part.isNotEmpty()) return part
            }
        }
        return originalArtist.ifBlank { "Unknown Artist" }
    }
}
