package com.musicplayer.app.data.repository

import com.musicplayer.app.BuildConfig
import com.musicplayer.app.data.remote.api.GeniusApi
import com.musicplayer.app.data.remote.api.LastFmApi
import com.musicplayer.app.data.remote.model.LastFmTag
import com.musicplayer.app.data.remote.model.LastFmSimilarTrack
import com.musicplayer.app.data.remote.model.LastFmArtistInfo
import com.musicplayer.app.data.remote.model.LastFmTrackInfo
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.net.URLEncoder
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.async
import kotlinx.coroutines.selects.select

@Singleton
class MetadataRepository @Inject constructor(
    private val lastFmApi: LastFmApi,
    private val geniusApi: GeniusApi,
    private val okHttpClient: OkHttpClient
) {
    private val lastFmApiKey = BuildConfig.LAST_FM_API_KEY
    private val geniusAccessToken = "Bearer ${BuildConfig.GENIUS_ACCESS_TOKEN}"
    
    // In-memory cache to prevent redundant network calls during the same session
    private val coverCache = LruCache<String, String>(100)
    private val artistCache = LruCache<String, String>(50)

    suspend fun getTrackInfo(artist: String, track: String) = 
        lastFmApi.getTrackInfo(artist, track, lastFmApiKey)

    suspend fun getArtistInfo(artist: String): LastFmArtistInfo? {
        return try {
            val response = lastFmApi.getArtistInfo(artist, lastFmApiKey)
            response.artist
        } catch (e: Exception) {
            null
        }
    }

    suspend fun getSimilarTracks(artist: String, track: String): List<LastFmSimilarTrack> {
        return try {
            val response = lastFmApi.getSimilarTracks(artist, track, lastFmApiKey)
            response.similartracks.track
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTrackTags(artist: String, track: String): List<LastFmTag> {
        return try {
            val response = lastFmApi.getTrackInfo(artist, track, lastFmApiKey)
            response.track?.toptags?.tag ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getArtistTopTags(artist: String): List<LastFmTag> {
        return try {
            val response = lastFmApi.getArtistTopTags(artist, lastFmApiKey)
            response.toptags?.tag ?: emptyList()
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getSimilarArtists(artist: String): List<LastFmArtistInfo> {
        return try {
            val response = lastFmApi.getSimilarArtists(artist, lastFmApiKey)
            response.similarartists.artist
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun getTopTracksByTag(tag: String): List<LastFmTrackInfo> {
        return try {
            val response = lastFmApi.getTopTracksByTag(tag, lastFmApiKey)
            response.tracks.track
        } catch (e: Exception) {
            emptyList()
        }
    }

    /**
     * Attempts to find a high-quality album cover from multiple sources in parallel.
     */
    suspend fun getAlbumCover(artist: String, title: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = "$artist-$title".lowercase()
        coverCache.get(cacheKey)?.let { return@withContext it }

        // Start all searches in parallel for maximum speed
        val iTunesJob = async { try { iTunesSearch("$artist $title") ?: iTunesSearch(title) } catch (e: Exception) { null } }
        val lastFmJob = async {
            try {
                val response = lastFmApi.getTrackInfo(artist, title, lastFmApiKey)
                val img = response.track?.album?.image?.find { it.size == "mega" }?.url
                    ?: response.track?.album?.image?.find { it.size == "extralarge" }?.url
                if (!img.isNullOrBlank() && isRealImage(img)) img else null
            } catch (e: Exception) { null }
        }
        val geniusJob = async {
            try {
                val response = geniusApi.search("$artist $title", geniusAccessToken)
                val hit = response.response?.hits?.firstOrNull { 
                    val songTitle = it.result?.title ?: ""
                    songTitle.contains(title, ignoreCase = true) || title.contains(songTitle, ignoreCase = true)
                }
                val img = hit?.result?.songArtThumbnailUrl ?: hit?.result?.primaryArtist?.headerImageUrl
                if (!img.isNullOrBlank() && isRealImage(img)) img else null
            } catch (e: Exception) { null }
        }

        // Race them! Use select to return the FIRST one that finds a valid result
        val result = select<String?> {
            iTunesJob.onAwait { it }
            lastFmJob.onAwait { it }
            geniusJob.onAwait { it }
        } ?: iTunesJob.await() ?: lastFmJob.await() ?: geniusJob.await()

        if (result != null) {
            coverCache.put(cacheKey, result)
        }
        result
    }

    private fun iTunesSearch(query: String): String? {
        try {
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = "https://itunes.apple.com/search?term=$encodedQuery&media=music&limit=1"
            val request = Request.Builder().url(url).build()
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string() ?: ""
                    val json = JSONObject(body)
                    val results = json.optJSONArray("results")
                    if (results != null && results.length() > 0) {
                        val result = results.getJSONObject(0)
                        val artwork = result.optString("artworkUrl100", "")
                        if (artwork.isNotBlank()) {
                            return artwork.replace("100x100", "1000x1000")
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.w("MetadataRepo", "iTunes cover fetch failed for $query")
        }
        return null
    }

    private fun isRealImage(url: String): Boolean {
        val placeholders = listOf(
            "default_album",
            "default_cover",
            "2a96cbd8b46e442fc41c2b86b821562f", // Last.fm star
            "noimage",
            "blank"
        )
        return placeholders.none { url.contains(it) }
    }

    suspend fun getArtistImage(artist: String): String? = withContext(Dispatchers.IO) {
        val cacheKey = artist.lowercase()
        artistCache.get(cacheKey)?.let { return@withContext it }

        val lastFmJob = async {
            try {
                val response = lastFmApi.getArtistInfo(artist, lastFmApiKey)
                val img = response.artist?.image?.find { it.size == "extralarge" }?.url
                    ?: response.artist?.image?.find { it.size == "large" }?.url
                    ?: response.artist?.image?.firstOrNull { it.url.isNotBlank() }?.url
                if (!img.isNullOrBlank() && isRealImage(img)) img else null
            } catch (e: Exception) { null }
        }

        val geniusJob = async {
            try {
                val response = geniusApi.search(artist, geniusAccessToken)
                val hit = response.response?.hits?.firstOrNull { 
                    it.result?.primaryArtist?.name?.equals(artist, ignoreCase = true) == true 
                } ?: response.response?.hits?.firstOrNull {
                    it.result?.primaryArtist?.name?.contains(artist, ignoreCase = true) == true
                }
                val img = hit?.result?.primaryArtist?.imageUrl
                if (!img.isNullOrBlank() && isRealImage(img)) img else null
            } catch (e: Exception) { null }
        }

        val result = select<String?> {
            lastFmJob.onAwait { it }
            geniusJob.onAwait { it }
        } ?: lastFmJob.await() ?: geniusJob.await()

        if (result != null) {
            artistCache.put(cacheKey, result)
        }
        result
    }
}
