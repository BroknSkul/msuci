package com.musicplayer.app.data.repository

import android.util.Log
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.model.Artist
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AggregatedMusicRepository @Inject constructor(
    private val searchRepository: SearchRepository,
    private val metadataRepository: MetadataRepository
) {
    
    /**
     * Search method: YouTube search (for best discovery) + Professional Metadata Enrichment
     */
    suspend fun searchSongs(query: String): List<Song> {
        return try {
            Log.d("AggregatedMusicRepo", "Starting YouTube-first search for: $query")
            
            // Step 1: Get YouTube results (primary source for playback)
            val youtubeResults = searchRepository.searchSongs(query)
            
            if (youtubeResults.isEmpty()) {
                return emptyList()
            }
            
            Log.d("AggregatedMusicRepo", "YouTube returned ${youtubeResults.size} results. Enriching...")
            
            // Step 2: Enrich results with clean metadata
            val enrichedSongs = enrichYoutubeResults(youtubeResults)
            
            Log.d("AggregatedMusicRepo", "✅ Search complete: ${enrichedSongs.size} songs enriched")
            enrichedSongs
            
        } catch (e: Exception) {
            Log.e("AggregatedMusicRepo", "Aggregated search failed: ${e.message}", e)
            emptyList()
        }
    }

    private fun cleanTitle(title: String): String {
        return title
            // Remove common suffixes like "feat.", "ft." and everything after
            .replace(Regex("(?i)\\s(feat\\.?|ft\\.?)\\s.*"), "")
            // Remove video-specific noise (Official Video, 4K, etc.)
            .replace(Regex("(?i)\\s(\\(|\\-)\\s?(Official|Music|Video|Lyrics|Audio|HD|4K|Visualizer|Lyrical|Song|Full|HD|4K|8K|Clip|Trailer|Teaser).*"), "")
            // Remove movie-specific markers often found in Indian/Tamil titles
            .replace(Regex("(?i)(Video Song|Lyrical Video|Full Song|From \".*?\"|Movie Song|OST)"), "")
            // Remove year patterns like (2024)
            .replace(Regex("\\(\\d{4}\\)"), "")
            // Remove bracketed content like [1080p] or [Slowed]
            .replace(Regex("\\[.*?\\]"), "")
            // Replace common separators with spaces to help search matching
            .replace(Regex("[|•|\\-–—]"), " ")
            // Final trim and whitespace normalization
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private suspend fun enrichYoutubeResults(
        youtubeResults: List<Song>
    ): List<Song> = coroutineScope {
        // Increased from 5 to 10 to provide better quality for more search results
        val (toEnrich, others) = if (youtubeResults.size > 10) {
            youtubeResults.take(10) to youtubeResults.drop(10)
        } else {
            youtubeResults to emptyList()
        }

        val enriched = toEnrich.map { ytSong ->
            async {
                try {
                    // Overall timeout for enrichment of a single song
                    kotlinx.coroutines.withTimeoutOrNull(3000) {
                        val cleanedTitle = cleanTitle(ytSong.title)
                        val cleanedArtist = cleanTitle(ytSong.artist)
                        
                        val albumCover = metadataRepository.getAlbumCover(cleanedArtist, cleanedTitle)
                        if (albumCover != null) {
                            ytSong.copy(thumbnailUrl = albumCover)
                        } else {
                            ytSong
                        }
                    } ?: ytSong
                } catch (e: Exception) {
                    ytSong
                }
            }
        }.map { it.await() }
        
        enriched + others
    }
}
