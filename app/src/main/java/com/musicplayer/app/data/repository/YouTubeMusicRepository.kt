package com.musicplayer.app.data.repository

import com.musicplayer.app.data.remote.api.YouTubeMusicApiService
import com.musicplayer.app.data.remote.api.YouTubeSearchRequestBody
import com.musicplayer.app.data.remote.api.YouTubeNextRequestBody
import com.musicplayer.app.data.remote.api.YouTubeBrowseRequestBody
import com.musicplayer.app.data.remote.api.YouTubeMusicRenderer
import com.musicplayer.app.data.model.*
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class YouTubeMusicRepository @Inject constructor(
    private val apiService: YouTubeMusicApiService
) {

    suspend fun searchSongs(query: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.searchSongs(YouTubeSearchRequestBody(query = query, params = "EgWKAQIIAWoKEAoIExIEEAEQCQ=="))
            val songs = mutableListOf<Song>()

            response.contents?.tabbedSearchResults?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                        extractSongFromRenderer(renderer)?.let { songs.add(it) }
                    }
                }

            Result.success(songs)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun searchArtists(query: String): Result<List<Artist>> = withContext(Dispatchers.IO) {
        try {
            // "EgWKAQIgAWoKEAoIExIEEAEQCQ==" is the filter for Artists in YT Music
            val response = apiService.searchSongs(YouTubeSearchRequestBody(query = query, params = "EgWKAQIgAWoKEAoIExIEEAEQCQ=="))
            val artists = mutableListOf<Artist>()

            response.contents?.tabbedSearchResults?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        val renderer = item.musicResponsiveListItemRenderer ?: return@forEach
                        val browseId = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                            ?.firstOrNull()?.navigationEndpoint?.browseEndpoint?.browseId ?: return@forEach

                        val name = renderer.flexColumns
                            ?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
                            ?.joinToString("") { it.text ?: "" } ?: "Unknown Artist"

                        // Pick highest quality thumbnail
                        val rawThumbnailUrl = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
                            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
                        
                        val thumbnailUrl = if (rawThumbnailUrl?.contains("googleusercontent.com") == true) {
                            rawThumbnailUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1000-h1000-l90-rj")
                        } else {
                            rawThumbnailUrl
                        }

                        artists.add(Artist(
                            id = browseId,
                            name = name.trim(),
                            imageUrl = thumbnailUrl,
                            songCount = 0
                        ))
                    }
                }
            Result.success(artists)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun getTrendingSongs(): List<Song> {
        val trendingTerms = listOf("Tamil Hits 2024", "Indian Pop 2024", "New Bollywood Songs")
        val randomTerm = trendingTerms.random()
        return searchSongs(randomTerm).getOrDefault(emptyList())
    }

    suspend fun getAlbumSongs(albumId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBrowse(YouTubeBrowseRequestBody(browseId = albumId))
            val songs = mutableListOf<Song>()
            
            response.contents?.singleColumnBrowseResults?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            songs
        } catch (e: Exception) {
            Log.e("YouTubeMusicRepository", "Failed to get album songs", e)
            emptyList()
        }
    }

    suspend fun getArtistSongs(artistId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBrowse(YouTubeBrowseRequestBody(browseId = artistId))
            val songs = mutableListOf<Song>()

            // Try different structures
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
            Log.e("YouTubeMusicRepository", "Failed to get artist songs", e)
            emptyList()
        }
    }

    suspend fun getPlaylistSongs(playlistId: String): Result<Pair<String, List<Song>>> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getBrowse(YouTubeBrowseRequestBody(browseId = "VL$playlistId"))
            val songs = mutableListOf<Song>()
            
            val playlistTitle = response.header?.musicVisualHeaderRenderer?.title?.runs?.firstOrNull()?.text ?: "YouTube Playlist"

            response.contents?.singleColumnBrowseResults?.tabs?.firstOrNull()
                ?.tabRenderer?.content?.sectionList?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            
            if (songs.isEmpty()) {
                response.contents?.sectionListRenderer?.contents?.forEach { section ->
                    section.musicShelf?.contents?.forEach { item ->
                        extractSongFromRenderer(item.musicResponsiveListItemRenderer)?.let { songs.add(it) }
                    }
                }
            }

            Result.success(playlistTitle to songs)
        } catch (e: Exception) {
            Log.e("YouTubeMusicRepository", "Failed to get playlist songs", e)
            Result.failure(e)
        }
    }

    suspend fun getLyrics(videoId: String): String = withContext(Dispatchers.IO) {
        try {
            val nextResponse = apiService.getNext(YouTubeNextRequestBody(videoId = videoId))
            val lyricsBrowseId = nextResponse.contents?.singleColumnMusicWatchNextResults?.tabbedRenderer
                ?.watchNextTabbedResults?.tabs?.find { tab ->
                    tab.tabRenderer?.title?.runs?.any { it.text == "LYRICS" } == true
                }?.tabRenderer?.endpoint?.browseEndpoint?.browseId ?: return@withContext ""

            val lyricsResponse = apiService.getBrowse(YouTubeBrowseRequestBody(browseId = lyricsBrowseId))
            val lyricsRawLines = mutableListOf<String>()
            
            lyricsResponse.contents?.sectionListRenderer?.contents?.forEach { section ->
                section.musicDescriptionShelf?.description?.runs?.forEach { run ->
                    run.text?.let { text -> lyricsRawLines.add(text) }
                }
            }
            
            lyricsRawLines.joinToString("\n")
        } catch (e: Exception) {
            Log.e("YouTubeMusicRepository", "Failed to get lyrics for $videoId", e)
            ""
        }
    }

    private fun extractSongFromRenderer(renderer: YouTubeMusicRenderer?): Song? {
        if (renderer == null) return null
        val videoId = renderer.playlistItemData?.videoId 
            ?: renderer.navigationEndpoint?.watchEndpoint?.videoId
            ?: renderer.flexColumns?.firstOrNull()?.musicResponsiveListItemFlexColumnRenderer?.text?.runs?.firstOrNull()?.navigationEndpoint?.watchEndpoint?.videoId
            ?: return null

        val title = renderer.flexColumns?.getOrNull(0)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs
            ?.joinToString("") { it.text ?: "" } ?: "Unknown"

        val artistRowRuns = renderer.flexColumns?.getOrNull(1)?.musicResponsiveListItemFlexColumnRenderer?.text?.runs ?: emptyList()
        var artist = "Unknown Artist"
        var album = "YouTube"
        
        val artistRowText = artistRowRuns.joinToString("") { it.text ?: "" }
        val parts = artistRowText.split(" • ")
        if (parts.isNotEmpty()) {
            artist = parts[0].trim()
            if (parts.size > 1 && !parts[1].contains(":")) album = parts[1].trim()
        }

        val durationText = parts.lastOrNull() ?: ""
        val durationMs: Long = if (durationText.contains(":")) {
            val timeParts = durationText.split(":").mapNotNull { it.trim().toLongOrNull() }
            when (timeParts.size) {
                2    -> (timeParts[0] * 60 + timeParts[1]) * 1000
                3    -> (timeParts[0] * 3600 + timeParts[1] * 60 + timeParts[2]) * 1000
                else -> 0L
            }
        } else 0L

        val rawThumbnailUrl = renderer.thumbnail?.musicThumbnailRenderer?.thumbnail?.thumbnails
            ?.maxByOrNull { (it.width ?: 0) * (it.height ?: 0) }?.url
            ?: "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
            
        val thumbnailUrl = if (rawThumbnailUrl.contains("googleusercontent.com")) {
            rawThumbnailUrl.replace(Regex("=w\\d+-h\\d+.*"), "=w1000-h1000-l90-rj")
        } else if (rawThumbnailUrl.contains("ytimg.com")) {
            "https://i.ytimg.com/vi/$videoId/maxresdefault.jpg"
        } else {
            rawThumbnailUrl
        }

        return Song(
            id          = videoId,
            title       = title.trim(),
            artist      = artist.trim(),
            album       = album.trim(),
            duration    = durationMs,
            thumbnailUrl = thumbnailUrl,
            streamUrl = "https://www.youtube.com/watch?v=$videoId"
        )
    }

    suspend fun getRecommendations(videoId: String): List<Song> = withContext(Dispatchers.IO) {
        try {
            val response = apiService.getNext(YouTubeNextRequestBody(videoId = videoId))
            val songs = mutableListOf<Song>()

            response.contents?.singleColumnMusicWatchNextResults?.results?.results?.contents?.forEach { content ->
                extractSongFromRenderer(content.musicResponsiveListItemRenderer)?.let { songs.add(it) }
            }

            songs
        } catch (e: Exception) {
            Log.e("YouTubeMusicRepository", "Failed to get recommendations for $videoId", e)
            emptyList()
        }
    }
}
