package com.musicplayer.app.data.repository

import com.musicplayer.app.data.remote.api.YouTubeMusicApiService
import com.musicplayer.app.data.remote.api.YouTubeSearchRequestBody
import com.musicplayer.app.data.remote.api.YouTubeBrowseRequestBody
import com.musicplayer.app.data.remote.api.YouTubeNextRequestBody
import com.musicplayer.app.data.remote.api.YouTubeMusicRenderer
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.util.YoutubeDlUtil
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import javax.inject.Inject
import javax.inject.Singleton
import android.util.Log
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withTimeoutOrNull

@Singleton
class SearchRepository @Inject constructor(
    private val metadataRepository: MetadataRepository,
    private val youtubeMusicApi: YouTubeMusicApiService
) {
    private val TAG = "SearchRepository"

    companion object {
        private val CLEAN_METADATA_REGEX = Regex("""(?i)\(\s*official.*?\s*\)|\[\s*official.*?\s*\]|\(\s*video.*?\s*\)|\[\s*video.*?\s*\]|\(\s*lyrics.*?\s*\)|\[\s*lyrics.*?\s*\]|\(\s*audio.*?\s*\)|\[\s*audio.*?\s*\]|\(\s*hd.*?\s*\)|\[\s*hd.*?\s*\]|\(\s*4k.*?\s*\)|\[\s*4k.*?\s*\]|official|video|music|lyrics|audio|hd|4k|ft\..+|feat\..+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
    }
    
    /**
     * Legacy search function for compatibility.
     */
    suspend fun searchSongs(query: String): List<Song> = searchSongsFlow(query).lastOrNull() ?: emptyList()

    /**
     * Optimized search flow that emits raw results instantly, then enriched ones.
     */
    fun searchSongsFlow(query: String): Flow<List<Song>> = flow {
        if (query.isBlank()) {
            emit(emptyList())
            return@flow
        }
        
        Log.d(TAG, "Searching for: $query")
        
        // Step 1: Try Fast InnerTube Search
        val ytMusicResults = try {
            withTimeoutOrNull(4000) {
                searchInnerTube(query)
            }
        } catch (e: Exception) {
            Log.e(TAG, "InnerTube search failed", e)
            null
        }

        if (!ytMusicResults.isNullOrEmpty()) {
            emit(ytMusicResults)
            // Enrichment
            val enriched = enrichMetadata(ytMusicResults, query)
            emit(enriched)
            return@flow
        }

        // Step 2: Fallback to YoutubeDlUtil (Optimized YT-DLP)
        try {
            val rawResults = withTimeoutOrNull(20000) { // Increased timeout to 20s
                YoutubeDlUtil.search(null, query)
            } ?: emptyList()

            if (rawResults.isNotEmpty()) {
                emit(rawResults)
                val enrichedResults = enrichMetadata(rawResults, query)
                emit(enrichedResults)
            } else {
                emit(emptyList())
            }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            Log.e(TAG, "Fallback search failed: ${e.message}")
            emit(emptyList())
        }
    }

    private suspend fun searchInnerTube(query: String): List<Song> = withContext(Dispatchers.IO) {
        val response = youtubeMusicApi.searchSongs(
            YouTubeSearchRequestBody(
                query = query,
                params = "EgWKAQIIAWoKEAoIExIEEAEQCQ==" // Filter for Songs
            )
        )
        
        val songs = mutableListOf<Song>()
        
        response.contents?.tabbedSearchResults?.tabs?.firstOrNull()
            ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                section.musicShelf?.contents?.forEach { item ->
                    extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                }
            }
            
        songs
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = youtubeMusicApi.getBrowse(YouTubeBrowseRequestBody(browseId = albumId))
            val songs = mutableListOf<Song>()
            
            response.contents?.singleColumnBrowseResults?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            songs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get album songs", e)
            emptyList()
        }
    }

    suspend fun getArtistSongs(artistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = youtubeMusicApi.getBrowse(YouTubeBrowseRequestBody(browseId = artistId))
            val songs = mutableListOf<Song>()

            // Try different possible structures in the InnerTube response
            val containers = listOfNotNull(
                response.contents?.tabbedSearchResults?.tabs,
                response.contents?.singleColumnBrowseResults?.tabs,
                response.contents?.sectionListRenderer?.contents?.map { null } // Just for loop iteration if needed
            )

            response.contents?.singleColumnBrowseResults?.tabs?.forEach { tab ->
                tab.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            }

            if (songs.isEmpty()) {
                response.contents?.sectionListRenderer?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            }

            songs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get artist songs", e)
            emptyList()
        }
    }

    suspend fun getRecommendations(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = youtubeMusicApi.getNext(YouTubeNextRequestBody(videoId = videoId))
            val songs = mutableListOf<Song>()

            response.contents?.singleColumnMusicWatchNextResults?.results?.results?.contents?.forEach { content ->
                extractSongFromRenderer(content.musicResponsiveListItemRenderer)?.let { songs.add(it) }
            }

            songs
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get recommendations", e)
            emptyList()
        }
    }

    suspend fun getLyrics(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            // 1. Get the lyrics browseId from the 'Next' endpoint
            val nextResponse = youtubeMusicApi.getNext(YouTubeNextRequestBody(videoId = videoId))
            val lyricsBrowseId = nextResponse.contents?.singleColumnMusicWatchNextResults?.tabbedRenderer
                ?.watchNextTabbedResults?.tabs?.find { tab ->
                    tab.tabRenderer?.title?.runs?.any { it.text == "LYRICS" } == true
                }?.tabRenderer?.endpoint?.browseEndpoint?.browseId ?: return@withContext ""

            // 2. Fetch the actual lyrics using the browseId
            val lyricsResponse = youtubeMusicApi.getBrowse(YouTubeBrowseRequestBody(browseId = lyricsBrowseId))
            
            val lyricsRawLines = mutableListOf<String>()
            
            lyricsResponse.contents?.sectionListRenderer?.contents?.forEach { section ->
                section.musicDescriptionShelf?.description?.runs?.forEach { run ->
                    run.text?.let { text ->
                        lyricsRawLines.add(text)
                    }
                }
            }
            
            lyricsRawLines.joinToString("\n")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get lyrics for $videoId", e)
            ""
        }
    }

    private fun extractSongFromRenderer(renderer: YouTubeMusicRenderer?): Song? {
        if (renderer == null) return null
        
        // Robust Video ID extraction
        val videoId = renderer.playlistItemData?.videoId 
            ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
            ?: renderer.flexColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null
        
        val title = renderer.flexColumns?.getOrNull(0)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.joinToString("") { it.text ?: "" } ?: "Unknown"
            
        val artistRowRuns = renderer.flexColumns?.getOrNull(1)
            ?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
        
        var artist = "Unknown Artist"
        var album = "YouTube"
        var durationMs = 0L
        
        val artistRowText = artistRowRuns.joinToString("") { it.text ?: "" }
        val parts = artistRowText.split(" • ")
        
        if (parts.isNotEmpty()) {
            artist = parts[0].trim()
            if (parts.size > 1) {
                // If the second part is not duration, it might be album
                if (!parts[1].contains(":")) {
                    album = parts[1].trim()
                }
            }
        }
        
        // Extract duration if present (usually at the end)
        val durationText = parts.lastOrNull() ?: ""
        if (durationText.contains(":")) {
            val timeParts = durationText.split(":").mapNotNull { it.trim().toLongOrNull() }
            durationMs = when (timeParts.size) {
                2 -> (timeParts[0] * 60 + timeParts[1]) * 1000
                3 -> (timeParts[0] * 3600 + timeParts[1] * 60 + timeParts[2]) * 1000
                else -> 0L
            }
        }

        // Thumbnail logic: prefer maxres, then hq, then whatever is available
        val thumbnails = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
        
        // Find the best quality among provided thumbnails
        val bestProvided = thumbnails?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
            ?: "https://i.ytimg.com/vi/$videoId/hqdefault.jpg"
            
        val thumbnailUrl = when {
            bestProvided.contains("googleusercontent.com") -> {
                // High res transform for Google User Content (YouTube Music album art)
                bestProvided.replace(Regex("=w\\d+-h\\d+.*"), "=w1200-h1200-l90-rj")
            }
            bestProvided.contains("ytimg.com") -> {
                // Force maximum resolution for YouTube thumbnails
                "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            }
            else -> bestProvided
        }

        return Song(
            id = videoId,
            title = title.trim(),
            artist = artist,
            album = album,
            duration = durationMs,
            thumbnailUrl = thumbnailUrl,
            streamUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    private fun isSongValid(song: Song, query: String): Boolean {
        val titleLower = song.title.lowercase()
        val artistLower = song.artist.lowercase()
        val combined = "$titleLower $artistLower"
        val queryLower = query.lowercase()

        // 1. Term restriction check: "slowed", "reverb", "remix"
        val restrictedTerms = listOf("slowed", "reverb", "remix")
        for (term in restrictedTerms) {
            // If the term is in the result but NOT in the query, it's invalid
            if (combined.contains(term) && !queryLower.contains(term)) {
                Log.d(TAG, "Filtering out restricted version: ${song.title} (Contains '$term' but query doesn't)")
                return false
            }
        }
        
        return true
    }

    private suspend fun enrichMetadata(songs: List<Song>, query: String = ""): List<Song> = coroutineScope {
        songs.filter { isSongValid(it, query) }.map { song ->
            async {
                // Slightly more relaxed timeout for search result enrichment (1200ms)
                withTimeoutOrNull(1200) {
                    val (realArtist, realTitle) = extractArtistAndTitle(song.artist, song.title)
                    val albumCover = metadataRepository.getAlbumCover(realArtist, realTitle)
                    if (!albumCover.isNullOrBlank()) {
                        song.copy(thumbnailUrl = albumCover, artist = realArtist, title = realTitle)
                    } else {
                        song
                    }
                } ?: song
            }
        }.awaitAll()
    }

    private fun extractArtistAndTitle(artist: String, title: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", " | ", ": ", " ~ ")
        val artistLower = artist.lowercase()
        
        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2)
                val part1 = parts[0].trim()
                val part2 = parts[1].trim()
                
                if (part1.isEmpty() || part2.isEmpty()) continue

                val part1Lower = part1.lowercase()
                val part2Lower = part2.lowercase()

                // Check if either part matches the uploader/artist name
                val p1MatchesArtist = part1Lower.contains(artistLower) || artistLower.contains(part1Lower)
                val p2MatchesArtist = part2Lower.contains(artistLower) || artistLower.contains(part2Lower)

                if (p1MatchesArtist && !p2MatchesArtist) {
                    return Pair(part1, cleanMetadata(part2))
                } else if (p2MatchesArtist && !p1MatchesArtist) {
                    return Pair(part2, cleanMetadata(part1))
                }

                // Title keywords heuristic
                val titleKeywords = listOf("song", "lyrics", "video", "official", "audio", "full", "hd", "4k", "lyrical")
                val p1HasTitleKeywords = titleKeywords.any { part1Lower.contains(it) }
                val p2HasTitleKeywords = titleKeywords.any { part2Lower.contains(it) }

                if (p2HasTitleKeywords && !p1HasTitleKeywords) {
                    return Pair(part1, cleanMetadata(part2))
                } else if (p1HasTitleKeywords && !p2HasTitleKeywords) {
                    return Pair(part2, cleanMetadata(part1))
                }

                // Default split logic
                if (!part1.contains(Regex("(?i)official|video|music|vevo|channel"))) {
                    return Pair(part1, cleanMetadata(part2))
                }
            }
        }
        return Pair(artist, cleanMetadata(title))
    }

    private fun cleanMetadata(text: String): String {
        return text.replace(CLEAN_METADATA_REGEX, "")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }
}
