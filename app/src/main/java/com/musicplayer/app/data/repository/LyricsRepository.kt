package com.musicplayer.app.data.repository

import android.util.Log
import com.musicplayer.app.BuildConfig
import com.musicplayer.app.data.remote.api.GeniusApi
import com.musicplayer.app.data.remote.api.LrcLibApi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import javax.inject.Inject
import javax.inject.Singleton
import java.io.IOException
import java.net.SocketException

@Singleton
class LyricsRepository @Inject constructor(
    private val geniusApi: GeniusApi,
    private val lrcLibApi: LrcLibApi,
    private val okHttpClient: OkHttpClient
) {
    private val geniusToken = "Bearer ${BuildConfig.GENIUS_ACCESS_TOKEN}"
    private val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    companion object {
        private val NOISE_REGEX = Regex("(?i)\\[.*?\\]|\\(.*?\\)|official|video|audio|music|lyric|hd|4k|ft|feat|prod|remix|high quality|full version")
        private val ARTIST_CLEAN_REGEX = Regex("(?i)- Topic|VEVO")
        private val LYRICS_HEADER_REGEX = Regex("(?s)^.*?\\d+\\s+Contributors.*?Lyrics", RegexOption.IGNORE_CASE)
        private val EMBED_CLEAN_REGEX = Regex("\\d*Embed$")
        private val SECTION_HEADER_REGEX = Regex("\\[(Verse|Chorus|Bridge|Outro|Intro|Pre-Chorus).*?\\]")
        private val WORD_JOIN_REGEX = Regex("(?<=[a-z])(?=[A-Z])")
        private val WHITESPACE_REGEX = Regex("\\n{3,}")
    }

    suspend fun getLyrics(
        songTitle: String,
        artist: String,
        duration: Int? = null,
        @Suppress("UNUSED_PARAMETER") originalTitle: String? = null,
        @Suppress("UNUSED_PARAMETER") originalArtist: String? = null
    ): String = withContext(Dispatchers.IO) {
        val cleanTitle = songTitle.replace(NOISE_REGEX, "").trim()
        val cleanArtist = artist.replace(ARTIST_CLEAN_REGEX, "").trim()

        // 1. Try LRCLIB first
        try {
            // Updated Regex: Removed the '.*' at the end which was truncating everything after "Video" or "Song"
            // This is critical for Tamil/Indian songs where the movie name follows "Video Song"
            val noiseRegex = Regex("(?i)\\b(official|video|audio|lyric|lyrics|hd|4k|ft|feat|prod|remix|full|ver|version|mv|song|visualizer)\\b")
            
            val cleanTitleForLrc = cleanTitle
                .replace(Regex("(?i)\\[.*?\\]|\\(.*?\\)"), "") // remove bracketed content
                .replace(noiseRegex, "")
                .replace(Regex("\\s+"), " ")
                .trim()
            
            val cleanArtistForLrc = cleanArtist
                .replace(Regex("(?i)\\s*-\\s*Topic$"), "")
                .replace(Regex("(?i)\\b(official|music|video|records|label|vevo|interactive|entertainment)\\b"), "")
                .trim()

            // Check if the artist is likely a music label (very common for Tamil songs)
            val labels = listOf("sony", "t-series", "zee", "aditya", "lahari", "tips", "venus", "think", "u1", "mass", "saregama", "ayngaran", "eros")
            val isLabel = labels.any { cleanArtistForLrc.lowercase().contains(it) }

            // Heuristic for non-English songs: Check if title contains non-Latin characters (like Tamil, Hindi, etc.)
            // Or if it's a known label, it's highly likely an Indian/Non-English track
            val hasNonLatin = cleanTitleForLrc.any { it.code > 127 }
            
            var processedTitleForLrc = cleanTitleForLrc
            if (hasNonLatin || isLabel) {
                // For non-English songs, strip common English descriptive words that confuse search
                // This leaves the core song name (often in native script or transliterated) and movie name
                val englishWordStripRegex = Regex("(?i)\\b(video|song|full|lyrics|lyrical|audio|movie|film|theme|bgm|track|official)\\b")
                processedTitleForLrc = processedTitleForLrc.replace(englishWordStripRegex, "").replace(Regex("\\s+"), " ").trim()
                Log.d("LyricsRepository", "Non-English/Label song detected. Simplified title: $processedTitleForLrc")
            }

            Log.d("LyricsRepository", "Attempting LRCLIB for: $processedTitleForLrc (Artist: $cleanArtistForLrc, isLabel: $isLabel)")
            
            // 1. Try precise GET first
            var lrcResponse = try { lrcLibApi.getLyrics(cleanArtistForLrc, processedTitleForLrc) } catch (e: Exception) {
                if (e is CancellationException) throw e
                null
            }
            
            // 2. If precise GET fails or has no synced lyrics, try SEARCH as a fallback
            if (lrcResponse?.syncedLyrics.isNullOrBlank()) {
                Log.d("LyricsRepository", "Precise LRCLIB failed or not synced, trying search...")
                
                // Tiered Search Strategy:
                // 1. First search with just the primary song name (first part before any separator like | or -)
                val primaryTitle = processedTitleForLrc.split(Regex("[|\\-]")).first().trim()
                
                val searchTiers = mutableListOf<String>()
                searchTiers.add(primaryTitle) // Tier 1: Just the song name (best for synced lyrics)
                if (isLabel) {
                    searchTiers.add(processedTitleForLrc) // Tier 2: Song + Movie
                } else {
                    searchTiers.add("$primaryTitle $cleanArtistForLrc") // Tier 2: Song + Artist
                    searchTiers.add("$processedTitleForLrc $cleanArtistForLrc") // Tier 3: Full messy info
                }

                for (query in searchTiers) {
                    Log.d("LyricsRepository", "Trying LRCLIB search tier: $query")
                    val searchResults = try { lrcLibApi.search(query) } catch (e: Exception) {
                        if (e is CancellationException) throw e
                        emptyList()
                    }
                    
                    if (searchResults.isNotEmpty()) {
                        val bestMatch = searchResults
                            .filter { !it.syncedLyrics.isNullOrBlank() }
                            .maxByOrNull { candidate ->
                                val titleMatch = if (candidate.trackName?.contains(primaryTitle, ignoreCase = true) == true) 2 else 0
                                val artistMatch = if (candidate.artistName?.contains(cleanArtistForLrc, ignoreCase = true) == true) 1 else 0
                                val durationMatch = if (duration != null && candidate.duration != null && Math.abs(candidate.duration - duration.toDouble()) < 5) 5 else 0
                                titleMatch + artistMatch + durationMatch
                            }
                        
                        if (bestMatch != null) {
                            lrcResponse = bestMatch
                            break // Found synced lyrics!
                        } else if (lrcResponse == null) {
                            lrcResponse = searchResults.firstOrNull() // Keep as fallback if nothing better found
                        }
                    }
                }
            }

            if (lrcResponse != null) {
                val lyrics = lrcResponse.syncedLyrics ?: lrcResponse.plainLyrics
                if (!lyrics.isNullOrBlank()) {
                    Log.d("LyricsRepository", "Found lyrics on LRCLIB (${if (lrcResponse.syncedLyrics != null) "synced" else "plain"})")
                    return@withContext lyrics
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is SocketException || e.message?.contains("Socket closed", ignoreCase = true) == true) {
                Log.w("LyricsRepository", "LRCLIB connection closed prematurely: ${e.message}")
            } else {
                Log.e("LyricsRepository", "LRCLIB failed: ${e.message}")
            }
        }

        // 2. Fallback to Genius
        try {
            Log.d("LyricsRepository", "Falling back to Genius for: $cleanTitle - $cleanArtist")
            
            Log.d("LyricsRepository", "Original: $songTitle | Cleaned: $cleanTitle")

            // 2. Search for the song on Genius
            var query = "$cleanTitle $cleanArtist"
            
            val response = try {
                geniusApi.search(query, geniusToken)
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                if (e is SocketException || e.message?.contains("Socket closed", ignoreCase = true) == true) {
                    Log.w("LyricsRepository", "Search failed due to closed socket: $query")
                } else {
                    Log.e("LyricsRepository", "Search failed for query: $query", e)
                }
                null
            }
            
            val searchResponse = if (response?.response?.hits.isNullOrEmpty()) {
                // Fallback: try searching with only the title if combined search fails
                query = cleanTitle
                try {
                    geniusApi.search(query, geniusToken)
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    if (e is SocketException || e.message?.contains("Socket closed", ignoreCase = true) == true) {
                        Log.w("LyricsRepository", "Fallback search failed due to closed socket: $query")
                    } else {
                        Log.e("LyricsRepository", "Fallback search failed for query: $query", e)
                    }
                    null
                }
            } else {
                response
            }

            val hits = searchResponse?.response?.hits ?: emptyList()
            // Filter by type "song" to ensure we are dealing with song results
            val songHits = hits.filter { it.type == "song" || it.type == null }

            val finalResult = songHits.find { hit ->
                val resultTitle = hit.result?.title?.lowercase() ?: ""
                val resultArtist = hit.result?.artistNames?.lowercase() ?: ""
                
                // Strict matching for Genius to avoid wrong lyrics for non-English songs
                // Check if cleaned title and artist overlap significantly with results
                val titleMatch = resultTitle.contains(cleanTitle.lowercase()) || cleanTitle.lowercase().contains(resultTitle)
                val artistMatch = resultArtist.contains(cleanArtist.lowercase()) || cleanArtist.lowercase().contains(resultArtist)
                
                titleMatch && artistMatch
            }?.result // Removed the risky fallback to songHits.firstOrNull()?.result
            
            val lyricsUrl = finalResult?.url
            if (lyricsUrl != null) {
                Log.d("LyricsRepository", "Found lyrics URL: $lyricsUrl")
                
                val request = Request.Builder()
                    .url(lyricsUrl)
                    .header("User-Agent", userAgent)
                    .build()
                
                val doc = try {
                    okHttpClient.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("Unexpected code $response")
                        Jsoup.parse(response.body?.string() ?: "", lyricsUrl)
                    }
                } catch (e: Exception) {
                    if (e is CancellationException) throw e
                    throw e
                }
                
                // 3. Precise HTML Selection
                // Targeting strictly the lyrics lines.
                val lyricsElements = doc.select("div[class^=Lyrics__Container]")
                
                var rawLyrics = ""
                if (lyricsElements.isNotEmpty()) {
                    rawLyrics = lyricsElements.joinToString("\n\n") { element ->
                        element.html()
                            .replace("<br>", "\n")
                            .replace("</div>", "\n")
                            .replace("<br />", "\n")
                            .let { Jsoup.parse(it).text() }
                    }
                } else {
                    // Fallback for older layout or specific variants
                    val legacyLyrics = doc.select(".lyrics, #lyrics-root").firstOrNull()
                    if (legacyLyrics != null) {
                        rawLyrics = legacyLyrics.text()
                    }
                }

                if (rawLyrics.isNotBlank()) {
                    // 4. Reliable Cleaning & Metadata Removal
                    val cleanedLyrics = rawLyrics
                        // Remove common Genius header junk: "[X] Contributors Translations ... [Song Title] Lyrics"
                        .replace(LYRICS_HEADER_REGEX, "")
                        // Remove "Embed" and trailing garbage often found at the end of Genius scrapes
                        .replace(EMBED_CLEAN_REGEX, "")
                        // Re-insert newlines before song section headers like [Verse 1]
                        .replace(SECTION_HEADER_REGEX, "\n\n$0\n")
                        // Fix instances where words are joined together because of tag removal (e.g. "endVerse" -> "end\nVerse")
                        .replace(WORD_JOIN_REGEX, "\n") 
                        // Clean up excessive whitespace
                        .replace(WHITESPACE_REGEX, "\n\n")
                        .trim()
                    
                    if (cleanedLyrics.isNotBlank()) {
                        return@withContext cleanedLyrics
                    }
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            if (e is SocketException || e.message?.contains("Socket closed", ignoreCase = true) == true) {
                Log.w("LyricsRepository", "Genius connection closed prematurely: ${e.message}")
            } else {
                Log.e("LyricsRepository", "Error fetching from Genius: ${e.javaClass.simpleName}", e)
            }
        }

        "Lyrics not found."
    }
}
