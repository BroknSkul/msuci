package com.musicplayer.app.util

import android.content.Context
import android.util.Log
import com.musicplayer.app.data.model.Song
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.yausername.youtubedl_android.mapper.VideoInfo
import kotlinx.coroutines.*
import java.io.File
import org.json.JSONObject

object YoutubeDlUtil {

    private const val TAG = "YoutubeDlUtil"
    private var isInitialized = false
    private var appContext: Context? = null

    @Synchronized
    fun init(context: Context) {
        if (isInitialized) return
        try {
            appContext = context.applicationContext
            YoutubeDL.getInstance().init(context)
            isInitialized = true
            Log.d(TAG, "YoutubeDL initialized")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to initialize YoutubeDL", e)
        }
    }

    suspend fun updateYoutubeDL(context: Context) = withContext(Dispatchers.IO) {
        try {
            YoutubeDL.getInstance().updateYoutubeDL(context)
            Log.d(TAG, "YoutubeDL updated")
        } catch (e: Throwable) {
            Log.e(TAG, "Failed to update YoutubeDL", e)
        }
    }

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    suspend fun search(context: Context?, query: String): List<Song> = withContext(Dispatchers.IO) {
        val t0 = System.currentTimeMillis()
        Log.d(TAG, "START search: $query")
        val targetContext = context ?: appContext
        if (!isInitialized && targetContext != null) init(targetContext)
        
        val results = mutableMapOf<String, Song>()
        
        // Refine query for music if it's not a direct URL/ID
        val refinedQuery = if (!query.contains("youtube.com") && !query.contains("youtu.be")) {
            "$query song"
        } else {
            query
        }
        
        try {
            // SEARCH: Fetching 10 items for better results
            val request = YoutubeDLRequest("ytsearch10:$refinedQuery")
            request.addOption("--dump-json")
            request.addOption("--flat-playlist")
            request.addOption("--no-playlist")
            request.addOption("--quiet")
            request.addOption("--no-warnings")
            request.addOption("--socket-timeout", "5")
            request.addOption("--extractor-args", "youtube:player_client=web")
            request.addOption("--match-filter", "duration < 1200 & duration > 60 & !is_live")

            val response = YoutubeDL.getInstance().execute(request, null)
            
            response.out?.split("\n")?.forEach { line ->
                if (line.trim().isEmpty()) return@forEach
                try {
                    val json = JSONObject(line)
                    val id = json.optString("id")
                    val title = json.optString("title", "Unknown")
                    val artist = json.optString("uploader", "Unknown Artist")
                    val duration = json.optLong("duration", 0)
                    
                    if (id.isNotEmpty() && !results.containsKey(id)) {
                        val tempSong = Song(
                            id = id,
                            title = title,
                            artist = artist,
                            album = "YouTube",
                            duration = duration * 1000L,
                            thumbnailUrl = "https://i.ytimg.com/vi/$id/maxresdefault.jpg",
                            streamUrl = "https://www.youtube.com/watch?v=$id"
                        )
                        if (isSongValidForMusic(tempSong)) {
                            results[id] = tempSong
                        }
                    }
                } catch (e: Exception) { }
            }
        } catch (e: Exception) { 
            Log.e(TAG, "Search failed", e)
        }
        
        Log.d(TAG, "YT-DLP search finished. Total results: ${results.size} at ${System.currentTimeMillis() - t0}ms")
        results.values.toList()
    }

    private fun isSongValidForMusic(song: Song): Boolean {
        val titleLower = song.title.lowercase()
        val artistLower = song.artist.lowercase()
        val combined = "$titleLower $artistLower"
        
        // 1. Blacklist specific words that indicate non-song content
        val blacklist = listOf(
            "vlog", "tutorial", "how to", "review", "reaction", "gaming", "gameplay", 
            "unboxing", "podcast", "interview", "full movie", "episode", "part 1", 
            "compilation", "best of 20", "top 10", "1 hour", "10 hours", "loop", 
            "stream highlights", "funny moments", "news", "documentary"
        )
        if (blacklist.any { combined.contains(it) }) return false
        
        // 2. Filter by duration (most songs are between 1 and 10 minutes)
        // 0 means unknown, we allow those to be safe
        if (song.duration > 0) {
            val minutes = song.duration / 1000 / 60
            if (minutes < 1 || minutes > 20) return false
        }
        
        // 3. Artist filter (case specific)
        if (artistLower.contains("d4vd")) return false 
        
        return true
    }

    fun getVideoInfo(context: Context, url: String): VideoInfo? {
        if (!isInitialized) init(context.applicationContext)
        
        // Simplified strategy list: Try the most reliable ones first
        val clientStrategies = listOf(
            "android,web",
            "ios,web",
            "tv,web"
        )
        
        for (strategy in clientStrategies) {
            try {
                // IMPROVED: Format string prioritizes high bitrate Opus (webm) then AAC (m4a)
                val formatString = "bestaudio[abr>=160][ext=webm]/bestaudio[abr>=128][ext=m4a]/bestaudio/best"
                
                val request = YoutubeDLRequest(url).apply {
                    addOption("-f", formatString)
                    addOption("--no-warnings")
                    addOption("--socket-timeout", "10")
                    addOption("--geo-bypass")
                    addOption("--skip-download")
                    addOption("--no-cache-dir")
                    addOption("--extractor-args", "youtube:player_client=$strategy")
                }
                val info = YoutubeDL.getInstance().getInfo(request)
                if (info.url != null) {
                    return info
                }
            } catch (e: Exception) { }
        }
        
        return null
    }

    suspend fun downloadAudio(
        context: Context, 
        url: String, 
        destDir: File, 
        songId: String,
        onProgress: ((Float) -> Unit)? = null
    ): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) init(context.applicationContext)
        val fileName = "$songId.mp3"
        val file = File(destDir, fileName)
        
        // Strategies for download
        val strategies = listOf("android,web", "ios,web", "")

        for (strategy in strategies) {
            try {
                if (file.exists()) file.delete()
                val request = YoutubeDLRequest(url).apply {
                    addOption("-o", file.absolutePath)
                    addOption("-f", "bestaudio/best")
                    addOption("--extract-audio")
                    addOption("--audio-format", "mp3")
                    addOption("--audio-quality", "0") // BEST QUALITY (320kbps if possible)
                    addOption("--no-cache-dir")
                    addOption("--geo-bypass")
                    addOption("--socket-timeout", "15")
                    if (strategy.isNotEmpty()) {
                        addOption("--extractor-args", "youtube:player_client=$strategy")
                    }
                }
                
                YoutubeDL.getInstance().execute(request) { progress, _, _ ->
                    onProgress?.invoke(progress)
                }
                
                if (file.exists() && file.length() > 0) {
                    return@withContext file.absolutePath
                }
            } catch (e: Exception) {
                Log.w(TAG, "Download attempt failed (strategy=$strategy): ${e.message}")
            }
        }

        null
    }
}
