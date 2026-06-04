package com.musicplayer.app.ui.viewmodel

import android.app.Application
import android.util.Log
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.asFlow
import androidx.lifecycle.viewModelScope
import androidx.media3.common.util.UnstableApi
import androidx.work.Data
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.musicplayer.app.data.local.database.StreamUrlCacheDao
import com.musicplayer.app.data.local.database.entity.StreamUrlCacheEntity
import com.musicplayer.app.data.model.Playlist
import com.musicplayer.app.data.model.Song
import com.musicplayer.app.data.model.LyricLine
import com.musicplayer.app.data.model.PlayerState
import com.musicplayer.app.data.repository.LyricsRepository
import com.musicplayer.app.data.repository.MetadataRepository
import com.musicplayer.app.data.repository.PlaylistRepository
import com.musicplayer.app.data.repository.SongRepository
import com.musicplayer.app.data.repository.NotificationRepository
import com.musicplayer.app.playback.MusicPlayer
import com.musicplayer.app.util.YoutubeDlUtil
import com.musicplayer.app.util.LyricsParser
import com.musicplayer.app.worker.DownloadWorker
import com.google.mlkit.nl.languageid.LanguageIdentification
import com.google.mlkit.nl.languageid.LanguageIdentifier
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.resume
import java.util.UUID
import java.io.File
import javax.inject.Inject

@OptIn(UnstableApi::class)
@HiltViewModel
class MusicViewModel @Inject constructor(
    application: Application,
    private val musicPlayer: MusicPlayer,
    private val lyricsRepository: LyricsRepository,
    private val metadataRepository: MetadataRepository,
    private val playlistRepository: PlaylistRepository,
    private val songRepository: SongRepository,
    private val notificationRepository: NotificationRepository,
    private val streamUrlCacheDao: StreamUrlCacheDao
) : AndroidViewModel(application) {

    companion object {
        private val METADATA_CLEAN_REGEX = Regex("""(?i)\(\s*official.*?\s*\)|\[\s*official.*?\s*\]|\(\s*video.*?\s*\)|\[\s*video.*?\s*\]|\(\s*lyrics.*?\s*\)|\[\s*lyrics.*?\s*\]|\(\s*audio.*?\s*\)|\[\s*audio.*?\s*\]|\(\s*hd.*?\s*\)|\[\s*hd.*?\s*\]|\(\s*4k.*?\s*\)|\[\s*4k.*?\s*\]|official|video|music|lyrics|audio|hd|4k|ft\..+|feat\..+""")
        private val WHITESPACE_REGEX = Regex("""\s+""")
        
        private val verificationClient = okhttp3.OkHttpClient.Builder()
            .connectTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .readTimeout(1500, java.util.concurrent.TimeUnit.MILLISECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }

    private val instanceId = UUID.randomUUID().toString().take(4)

    val playerState: StateFlow<PlayerState> = musicPlayer.playerState

    private val _lyrics = MutableStateFlow<String?>(null)
    val lyrics: StateFlow<String?> = _lyrics.asStateFlow()

    private val _syncedLyrics = MutableStateFlow<List<LyricLine>?>(null)
    val syncedLyrics: StateFlow<List<LyricLine>?> = _syncedLyrics.asStateFlow()

    val currentLyricIndex: StateFlow<Int> = combine(playerState, _syncedLyrics) { state, lines ->
        if (lines.isNullOrEmpty()) -1
        else {
            // Offset progress by 200ms to compensate for UI/Animation lag
            val adjustedProgress = state.progress + 200
            lines.indexOfLast { it.time <= adjustedProgress }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), -1)

    private val _albumCoverUrl = MutableStateFlow<Any?>(null)
    val albumCoverUrl: StateFlow<Any?> = _albumCoverUrl.asStateFlow()

    private val _isFavorite = MutableStateFlow(false)
    val isFavorite: StateFlow<Boolean> = _isFavorite.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val _isDownloading = MutableStateFlow<Set<String>>(emptySet())
    val isDownloading: StateFlow<Set<String>> = _isDownloading.asStateFlow()

    private val _downloadMessage = MutableSharedFlow<String>()
    val downloadMessage = _downloadMessage.asSharedFlow()

    private val favoritesPlaylistId = "favorites_playlist"
    private val downloadsPlaylistId = "downloads_playlist"

    private var metadataFetchJob: Job? = null
    private var discoveryJob: Job? = null
    private var playlistLoadingJob: Job? = null
    private var playbackJob: Job? = null
    
    private var activePlaylist: List<Song>? = null
    private var lastDiscoverySeedId: String? = null
    private val playedSongIds = mutableListOf<String>()
    private val processingIds = MutableStateFlow<Set<String>>(emptySet())
    private val fetchingMetadataIds = MutableStateFlow<Set<String>>(emptySet())
    private val prefetchJobMap = mutableMapOf<String, Job>()
    private val prefetchMutex = Mutex()
    private val languageIdentifier: LanguageIdentifier = LanguageIdentification.getClient()

    @kotlin.OptIn(ExperimentalCoroutinesApi::class)
    private val extractionDispatcher = Dispatchers.IO.limitedParallelism(1)
    private val pipedVideoCache = mutableMapOf<String, Song>()

    private val _sleepTimerRemaining = MutableStateFlow<Long?>(null)
    val sleepTimerRemaining: StateFlow<Long?> = _sleepTimerRemaining.asStateFlow()

    private val backgroundQueueJobs = mutableMapOf<String, Job>()

    private fun cancelBackgroundQueuing() {
        Log.d("MusicViewModel", "Cancelling all background queuing and discovery")
        backgroundQueueJobs.values.forEach { it.cancel() }
        backgroundQueueJobs.clear()
        
        prefetchJobMap.values.forEach { it.cancel() }
        prefetchJobMap.clear()
        
        discoveryJob?.cancel()
        discoveryJob = null
        playlistLoadingJob?.cancel()
        playlistLoadingJob = null
    }

    private suspend fun detectLanguage(text: String): String? = suspendCancellableCoroutine { continuation ->
        languageIdentifier.identifyLanguage(text)
            .addOnSuccessListener { languageCode ->
                if (languageCode == "und") {
                    continuation.resume(null)
                } else {
                    continuation.resume(languageCode)
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }

    private fun mapLanguageCodeToName(code: String): String? {
        return when (code) {
            "en" -> "english"
            "ta" -> "tamil"
            "hi" -> "hindi"
            "te" -> "telugu"
            "ml" -> "malayalam"
            "pa" -> "punjabi"
            "bn" -> "bengali"
            "mr" -> "marathi"
            "kn" -> "kannada"
            "ko" -> "korean"
            "ja" -> "japanese"
            "zh" -> "chinese"
            "es" -> "spanish"
            "fr" -> "french"
            "de" -> "german"
            "it" -> "italian"
            "pt" -> "portuguese"
            "ar" -> "arabic"
            "tr" -> "turkish"
            "ru" -> "russian"
            "th" -> "thai"
            "vi" -> "vietnamese"
            "id" -> "indonesian"
            else -> null
        }
    }

    private fun getTitleKey(artist: String, title: String): String {
        val separators = listOf(" - ", " – ", " — ", " | ", ": ", " ~ ")
        var firstPart = title
        for (sep in separators) {
            if (title.contains(sep)) {
                firstPart = title.split(sep, limit = 2)[0].trim()
                break
            }
        }
        
        // Clean metadata thoroughly for the final identity check
        var cleaned = firstPart.lowercase()
        
        // Remove specific noise words requested
        val noiseWords = listOf(
            "8k", "4k", "hd", "uhd", "full video", "video song", "lyrical video", 
            "lyrics song", "tamil song", "hindi song", "telugu song", "official", 
            "lyrics", "video", "audio", "song", "1080p", "720p"
        )
        
        for (word in noiseWords) {
            cleaned = cleaned.replace(word, "")
        }

        // Final polishing: remove special chars and extra spaces
        return cleaned.replace(Regex("[^a-z0-9\\s]"), "")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    private fun isSongValidForQueuing(song: Song): Boolean {
        val titleLower = song.title.lowercase()
        val artistLower = song.artist.lowercase()
        val combined = "$titleLower $artistLower"
        
        if (artistLower.contains("d4vd")) return false
        
        val blacklist = listOf("top 10", "trending", "1 hour", "remix", "slowed", "reverb", "full album", "compilation", "playlist", "parody", "version", "live", "bollywood", "kollywood")
        return blacklist.none { combined.contains(it) }
    }

    val favoriteSongIds: StateFlow<Set<String>> = playlistRepository.getSongsInPlaylist(favoritesPlaylistId)
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val allPlaylists: StateFlow<List<Playlist>> = playlistRepository.getAllPlaylists()
        .map { list -> list.filter { it.id != downloadsPlaylistId } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val downloadedSongIds: StateFlow<Set<String>> = songRepository.getDownloadedSongs()
        .map { songs -> songs.map { it.id }.toSet() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    init {
        Log.d("MusicViewModel", "Initializing MusicViewModel [$instanceId]")
        
        combine(
            playerState.map { it.currentSong?.id }.distinctUntilChanged(),
            favoriteSongIds
        ) { currentId, favoriteIds ->
            currentId != null && favoriteIds.contains(currentId)
        }.onEach { isFav ->
            _isFavorite.value = isFav
        }.launchIn(viewModelScope)
        
        viewModelScope.launch {
            playlistRepository.createPlaylist("Favorite Songs", "Your liked tracks", favoritesPlaylistId)
            playlistRepository.createPlaylist("Downloads", "Your downloaded tracks", downloadsPlaylistId)
        }

        playerState.map { it.currentSong }
            .distinctUntilChanged { old, new -> old?.id == new?.id }
            .onEach { song ->
                metadataFetchJob?.cancel()
                if (song != null) {
                    songRepository.addRecentSong(song)
                    _lyrics.value = "Searching for lyrics..."
                    _syncedLyrics.value = null
                    
                    // Add to played history (keep last 20 to be safe, though user asked for 10)
                    playedSongIds.add(0, song.id)
                    if (playedSongIds.size > 20) {
                        playedSongIds.removeAt(playedSongIds.size - 1)
                    }
                    
                    // Check if we already have a high-res thumbnail in the DB or a local one
                    val savedSong = songRepository.getSongById(song.id)
                    
                    // Offline check: Priority to local file if it exists
                    val localThumb = File(getApplication<Application>().getExternalFilesDir(null), "music_storage/${song.id}.jpg")
                    
                    val initialThumbnail: Any? = if (localThumb.exists()) {
                        localThumb
                    } else if (savedSong != null && isHighRes(savedSong.thumbnailUrl)) {
                        savedSong.thumbnailUrl
                    } else {
                        val original = song.thumbnailUrl
                        if (original.isBlank() && song.id.length == 11) {
                            "https://img.youtube.com/vi/${song.id}/hqdefault.jpg"
                        } else {
                            original.replace("default.jpg", "hqdefault.jpg")
                                    .replace("sddefault.jpg", "hqdefault.jpg")
                        }
                    }
                    
                    _albumCoverUrl.value = initialThumbnail
                    
                    metadataFetchJob = viewModelScope.launch {
                        launch { fetchLyrics(song) }
                        launch { fetchSongMetadata(song) }
                    }
                } else {
                    _lyrics.value = null
                    _syncedLyrics.value = null
                    _albumCoverUrl.value = null
                }
            }.launchIn(viewModelScope)

        playerState.map { it.currentSong?.id to it.isLoading }
            .distinctUntilChanged()
            .onEach { (songId, isLoading) ->
                if (songId == null || isLoading) return@onEach
                
                val currentSong = playerState.value.currentSong ?: return@onEach
                val currentQueue = playerState.value.queue

                if (activePlaylist != null) {
                    val playlist = activePlaylist!!
                    val currentIndex = playlist.indexOfFirst { it.id == songId }
                    
                    if (currentIndex != -1) {
                        val songsToLoad = mutableListOf<Song>()
                        var lookAhead = 1
                        while (songsToLoad.size < 6 && currentIndex + lookAhead < playlist.size) {
                            val nextSong = playlist[currentIndex + lookAhead]
                            if (currentQueue.none { it.id == nextSong.id }) {
                                songsToLoad.add(nextSong)
                            }
                            lookAhead++
                        }
                        
                        val remainingInPlaylist = playlist.size - 1 - currentIndex
                        if (remainingInPlaylist < 2 && currentQueue.size < 5 && lastDiscoverySeedId != songId) {
                            lastDiscoverySeedId = songId
                            discoveryJob?.cancel()
                            discoveryJob = viewModelScope.launch {
                                performSmartOneByOneDiscovery(currentSong)
                            }
                        }

                        if (songsToLoad.isNotEmpty()) {
                            viewModelScope.launch {
                                for (nextSongToLoad in songsToLoad) {
                                    loadAndQueueNext(nextSongToLoad, bypassValidation = true)
                                }
                            }
                        }
                    }
                } else if (lastDiscoverySeedId != songId) {
                    lastDiscoverySeedId = songId
                    discoveryJob?.cancel()
                    discoveryJob = viewModelScope.launch {
                        performSmartOneByOneDiscovery(currentSong)
                    }
                }
            }.launchIn(viewModelScope)
            
        viewModelScope.launch {
            WorkManager.getInstance(getApplication())
                .getWorkInfosByTagLiveData("download_task")
                .asFlow()
                .collect { workInfos: List<WorkInfo> ->
                    val progressMap = mutableMapOf<String, Float>()
                    val activeIds = mutableSetOf<String>()
                    
                    for (info in workInfos) {
                        val songId = info.tags.find { it.startsWith("id_") }?.removePrefix("id_") ?: continue
                        
                        when (info.state) {
                            WorkInfo.State.RUNNING, WorkInfo.State.ENQUEUED -> {
                                activeIds.add(songId)
                                val progress = info.progress.getFloat("progress", 0f)
                                progressMap[songId] = progress
                            }
                            WorkInfo.State.SUCCEEDED -> {
                                if (_isDownloading.value.contains(songId)) {
                                    _downloadMessage.emit("the song has been downloaded")
                                }
                            }
                            else -> {}
                        }
                    }
                    _downloadProgress.value = progressMap
                    _isDownloading.value = activeIds
                }
        }
    }

    fun isHighRes(url: String): Boolean {
        return url.startsWith("http") && !url.contains("ytimg.com") && !url.contains("youtube.com")
    }

    private fun loadAndQueueNext(song: Song, bypassValidation: Boolean = false) {
        if (processingIds.value.contains(song.id)) return
        if (playerState.value.queue.any { it.id == song.id }) return
        if (playerState.value.queue.size >= 50) return
        
        if (!bypassValidation) {
            if (!isSongValidForQueuing(song)) return
            
            val candidateKey = getTitleKey(song.artist, song.title)
            // Check against current song
            playerState.value.currentSong?.let { current ->
                if (getTitleKey(current.artist, current.title) == candidateKey) {
                    Log.d("Qwali4", "Queue Filter: Skipping variant of current song: ${song.title}")
                    return
                }
            }
            // Check against existing queue
            if (playerState.value.queue.any { getTitleKey(it.artist, it.title) == candidateKey }) {
                Log.d("Qwali4", "Queue Filter: Skipping duplicate in queue: ${song.title}")
                return
            }
        }
        
        processingIds.update { it + song.id }
        
        val job = viewModelScope.launch(Dispatchers.IO) {
            try {
                // Background queuing should wait if something is already loading to prioritize bandwidth
                // But we don't want to hold a mutex while delaying
                while (playerState.value.isLoading) {
                    delay(500)
                    yield() // Check for cancellation
                }

                val (realArtist, realTitle) = extractArtistAndTitle(song.artist, song.title)
                val cleanedSong = song.copy(artist = realArtist, title = realTitle)
                
                val streamUrl = resolveStreamUrl(cleanedSong)
                if (streamUrl != null) {
                    musicPlayer.cacheStreamUrl(song.id, streamUrl)
                    withContext(Dispatchers.Main) {
                        musicPlayer.addToQueue(cleanedSong)
                    }
                }
            } catch (e: CancellationException) {
                Log.d("MusicViewModel", "Background queue job cancelled for: ${song.title}")
            } catch (e: Exception) {
                Log.e("Qwali4", "[$instanceId] Error resolving next song", e)
            } finally {
                processingIds.update { it - song.id }
            }
        }
        
        backgroundQueueJobs[song.id] = job
        job.invokeOnCompletion { backgroundQueueJobs.remove(song.id) }
    }

    fun prefetchStreamUrl(song: Song) {
        if (musicPlayer.getCachedUrl(song.id) != null) return
        if (prefetchJobMap.containsKey(song.id)) return

        prefetchJobMap[song.id] = viewModelScope.launch(Dispatchers.IO) {
            try {
                Log.d("MusicViewModel", "Prefetching stream for: ${song.title}")
                val url = resolveStreamUrl(song)
                if (url != null) {
                    musicPlayer.cacheStreamUrl(song.id, url)
                    Log.d("MusicViewModel", "Prefetch successful: ${song.title}")
                }
            } catch (e: Exception) {
                Log.w("MusicViewModel", "Prefetch failed for ${song.title}")
            } finally {
                prefetchJobMap.remove(song.id)
            }
        }
    }

    private suspend fun performSmartOneByOneDiscovery(seedSong: Song) = supervisorScope {
        try {
            val candidates = mutableSetOf<Song>()
            val mutex = Mutex()
            
            // USE BOTH ORIGINAL AND EXTRACTED METADATA FOR LANGUAGE DETECTION
            val (realArtist, realTitle) = extractArtistAndTitle(seedSong.artist, seedSong.title)
            val fullContextText = "${seedSong.artist} ${seedSong.title} $realArtist $realTitle".lowercase()

            Log.d("Qwali4", "Starting Deep Discovery for: $realTitle by $realArtist")

            // Detect language from tags
            val artistTags = metadataRepository.getArtistTopTags(realArtist)
            val trackTags = metadataRepository.getTrackTags(realArtist, realTitle)
            val allTags = (artistTags + trackTags).map { it.name.lowercase() }
            
            val commonLanguages = listOf(
                "tamil", "hindi", "telugu", "malayalam", "punjabi", "bengali", "marathi", "kannada",
                "korean", "japanese", "chinese", "k-pop", "j-pop",
                "spanish", "french", "german", "italian", "portuguese",
                "arabic", "turkish", "russian", "th" to "thai", "vi" to "vietnamese", "id" to "indonesian"
            )
            
            // Priority 1: Check tags
            var detectedLanguage = commonLanguages.map { if (it is Pair<*, *>) it.second.toString() else it.toString() }
                .firstOrNull { lang -> allTags.contains(lang) }

            // Priority 2: Check for explicit language/region markers in the FULL original title/artist
            if (detectedLanguage == null) {
                val regionMarkers = mapOf(
                    "tamil" to listOf("tamil", "kollywood", "u1", "anirudh", "rahman", "mrt music"),
                    "hindi" to listOf("hindi", "bollywood", "t-series", "tseries"),
                    "telugu" to listOf("telugu", "tollywood", "aditya music"),
                    "malayalam" to listOf("malayalam", "mollywood"),
                    "punjabi" to listOf("punjabi", "bhangra")
                )
                
                detectedLanguage = regionMarkers.entries.firstOrNull { entry ->
                    entry.value.any { marker -> fullContextText.contains(marker) }
                }?.key
            }
            
            // TIER 0: ML Kit Detection (On the most likely song title part)
            if (detectedLanguage == null) {
                val mlCode = detectLanguage(seedSong.title)
                if (mlCode != null) {
                    val name = mapLanguageCodeToName(mlCode)
                    if (name != "english") {
                        detectedLanguage = name
                    }
                }
            }

            if (detectedLanguage != null) {
                Log.d("Qwali4", "Language Lock Enabled: $detectedLanguage")
            } else {
                Log.d("Qwali4", "Global Mode (English/Universal)")
            }

            // TIER 1: Last.fm Similar Tracks (Direct Hits)
            val similar = metadataRepository.getSimilarTracks(realArtist, realTitle)
            val found = searchLastFmTracks(similar.take(10).map { it.name to it.artist.name }, detectedLanguage)
            mutex.withLock { candidates.addAll(found) }

            // TIER 2: Language-based Discovery (Personalized)
            if (detectedLanguage != null && candidates.size < 15) {
                try {
                    // Include real artist/title to get related content instead of just generic top charts
                    val langQuery = "songs like $realTitle $realArtist $detectedLanguage"
                    val results = YoutubeDlUtil.search(getApplication(), langQuery)
                    mutex.withLock { 
                        candidates.addAll(results.filter { it.id != seedSong.id && isSongValidForQueuing(it) }.take(15)) 
                    }
                } catch (e: Exception) {}
            }

            // TIER 3: Similar Artists (Expansion)
            if (candidates.size < 15) {
                Log.d("Qwali4", "Tier results low (${candidates.size}), launching Tier 3 (Similar Artists)")
                try {
                    val similarArtists = metadataRepository.getSimilarArtists(realArtist)
                    // Take more artists and more songs per artist for better variety
                    val artistTracks = similarArtists.take(5).flatMap { artist ->
                        try {
                            val searchQuery = if (detectedLanguage != null) "${artist.name} songs $detectedLanguage" else "top songs by ${artist.name}"
                            val results = YoutubeDlUtil.search(getApplication(), searchQuery)
                            results.filter { isSongValidForQueuing(it) }.shuffled().take(5)
                        } catch (e: Exception) { emptyList() }
                    }
                    mutex.withLock { candidates.addAll(artistTracks) }
                } catch (e: Exception) {}
            }

            // TIER 4: Genre/Tag Discovery (Broad context)
            if (candidates.size < 5) {
                Log.d("Qwali4", "Tier 3 low results (${candidates.size}), launching Tier 4 (Genre/Tags)")
                try {
                    val topTag = artistTags.firstOrNull { it.name.lowercase() != detectedLanguage }?.name ?: detectedLanguage ?: "pop"
                    val tagTracks = metadataRepository.getTopTracksByTag(topTag)
                    val found = searchLastFmTracks(tagTracks.take(15).map { it.name to it.artist.name }, detectedLanguage)
                    mutex.withLock { candidates.addAll(found) }
                } catch (e: Exception) {}
            }

            // FINAL FALLBACK: Popular in detected language or general
            if (candidates.isEmpty()) {
                Log.d("Qwali4", "All tiers failed, using Popular fallback")
                try {
                    val fallbackQuery = if (detectedLanguage != null) "popular $detectedLanguage music" else "popular music 2024"
                    val fallback = YoutubeDlUtil.search(getApplication(), fallbackQuery)
                    mutex.withLock { candidates.addAll(fallback.filter { isSongValidForQueuing(it) }.shuffled().take(20)) }
                } catch (e: Exception) {}
            }

            val currentQueueIds = playerState.value.queue.map { it.id }.toSet()
            val recentPlayedIds = playedSongIds.take(20).toSet()
            
            // For fuzzy duplicate checking: create a set of "Artist - Title" strings already in queue
            val existingSongKeys = playerState.value.queue.map { 
                getTitleKey(it.artist, it.title)
            }.toMutableSet()
            
            // Add currently playing seed song to the "do not repeat" set
            existingSongKeys.add(getTitleKey(seedSong.artist, seedSong.title))

            val intermediatePool = candidates.filter { 
                it.id != seedSong.id && 
                !currentQueueIds.contains(it.id) && 
                !recentPlayedIds.contains(it.id) &&
                isSongValidForQueuing(it)
            }.map { song ->
                // Use the refined title/artist order before processing
                val (artist, title) = extractArtistAndTitle(song.artist, song.title)
                song.copy(artist = artist, title = title)
            }.filter { song ->
                // FUZZY DUPLICATE CHECK: 
                // Prevent adding different versions (IDs) of the same Song Title + Artist
                val key = getTitleKey(song.artist, song.title)
                if (existingSongKeys.contains(key)) {
                    Log.d("Qwali4", "Duplicate Filter: Skipping variant of ${song.title} (Key: $key)")
                    false
                } else {
                    existingSongKeys.add(key)
                    true
                }
            }
            
            val finalPool = intermediatePool.map { candidate ->
                async {
                    if (detectedLanguage == null || isSongInLanguage(candidate, detectedLanguage)) {
                        candidate
                    } else null
                }
            }.awaitAll().filterNotNull()
            
            val finalSelection = finalPool.shuffled().take(10)
            Log.d("Qwali4", "Discovery finished for ${seedSong.title}. Pool: ${finalPool.size}, Selected: ${finalSelection.size}")

            for (song in finalSelection) {
                loadAndQueueNext(song)
                delay(400)
            }
        } catch (e: Exception) {
            Log.e("Qwali4", "Deep discovery failed completely", e)
        }
    }

    private suspend fun searchLastFmTracks(tracks: List<Pair<String, String>>, language: String? = null): List<Song> = coroutineScope {
        tracks.map { (name, artist) ->
            async {
                try {
                    val query = if (language != null) "$artist $name $language" else "$artist $name"
                    val results = YoutubeDlUtil.search(getApplication(), query)
                    
                    // Prioritize results that pass the language check
                    val bestMatch = if (language != null) {
                        results.filter { isSongValidForQueuing(it) }
                            .firstOrNull { isSongInLanguage(it, language) }
                            ?: results.firstOrNull { isSongValidForQueuing(it) }
                    } else {
                        results.firstOrNull { isSongValidForQueuing(it) }
                    }
                    
                    bestMatch ?: results.firstOrNull()
                } catch (e: Exception) { null }
            }
        }.awaitAll().filterNotNull()
    }

    private suspend fun isSongInLanguage(song: Song, targetLanguage: String): Boolean {
        val combined = "${song.title} ${song.artist}".lowercase()
        
        // 1. Quick check: explicit mention
        if (combined.contains(targetLanguage)) return true
        if (targetLanguage == "hindi" && combined.contains("bollywood")) return true

        // 2. ML Kit check: The sophisticated brain
        val mlCode = detectLanguage("${song.title} ${song.artist}")
        val detectedName = mlCode?.let { mapLanguageCodeToName(it) }
        
        // If ML Kit is confident it's the target language, allow it
        if (detectedName == targetLanguage) return true
        
        // If ML Kit detected a DIFFERENT specific language, we reject it (Strict termination)
        if (detectedName != null) {
            Log.d("Qwali4", "Strict Filter: Terminating ${song.title} (Detected: $detectedName, Target: $targetLanguage)")
            return false
        }

        // 3. Indian language specific heuristics (for cases where ML Kit is unsure 'und')
        val indianLanguages = listOf("tamil", "hindi", "telugu", "malayalam", "punjabi", "bengali", "marathi", "kannada")
        if (indianLanguages.contains(targetLanguage)) {
            // Blacklist known western artists
            val westernArtists = listOf("katy perry", "ariana grande", "michael jackson", "justin bieber", "taylor swift", "ed sheeran", "rihanna", "drake", "bruno mars", "the weeknd", "eminem", "lady gaga", "coldplay", "maroon 5")
            if (westernArtists.any { combined.contains(it) }) return false
            
            // Allow if it has Indian label indicators
            val indianIndicators = listOf("music", "series", "zee", "sony", "aditya", "lahari", "tips", "venus", "speed records", "desimusic", "mrt", "mass", "think", "thinkmusic", "u1", "vijay", "sun music")
            if (indianIndicators.any { combined.contains(it) }) return true

            // If we got here and it's an Indian language target, and we are not sure, reject to be safe
            return false 
        }
        
        return true
    }

    private fun extractArtistAndTitle(artist: String, title: String): Pair<String, String> {
        val separators = listOf(" - ", " – ", " — ", " | ", ": ", " ~ ")
        val artistLower = artist.lowercase()
        
        // If uploader is a known studio, don't use it for matching parts
        val isStudio = listOf("music", "series", "zee", "sony", "aditya", "lahari", "tips", "venus", "mrt", "mass", "think", "u1", "channel", "official").any { artistLower.contains(it) }

        for (sep in separators) {
            if (title.contains(sep)) {
                val parts = title.split(sep, limit = 2)
                val part1 = parts[0].trim()
                val part2 = parts[1].trim()
                
                if (part1.isEmpty() || part2.isEmpty()) continue

                val part1Lower = part1.lowercase()
                val part2Lower = part2.lowercase()

                // Check for uploader match (only if not a studio)
                if (!isStudio) {
                    val p1MatchesArtist = part1Lower.contains(artistLower) || artistLower.contains(part1Lower)
                    val p2MatchesArtist = part2Lower.contains(artistLower) || artistLower.contains(part2Lower)

                    if (p1MatchesArtist && !p2MatchesArtist) return Pair(part1, cleanMetadata(part2))
                    if (p2MatchesArtist && !p1MatchesArtist) return Pair(part2, cleanMetadata(part1))
                }

                // Keyword heuristic: Check if one part is clearly a title metadata (Official Video, 8K, etc.)
                val titleKeywords = listOf("song", "video", "official", "audio", "full", "hd", "4k", "lyrical", "movie", "film", "1080p")
                val p1HasTitleKeywords = titleKeywords.any { part1Lower.contains(it) }
                val p2HasTitleKeywords = titleKeywords.any { part2Lower.contains(it) }

                if (p2HasTitleKeywords && !p1HasTitleKeywords) {
                    // Part 2 looks like "HD Video Song", so Part 1 is the Title
                    // We return Artist=artist (uploader) and Title=Part 1
                    return Pair(artist, cleanMetadata(part1))
                } else if (p1HasTitleKeywords && !p2HasTitleKeywords) {
                    // Part 1 looks like metadata, so Part 2 is the Title
                    return Pair(artist, cleanMetadata(part2))
                }
            }
        }
        return Pair(artist, cleanMetadata(title))
    }

    private fun cleanMetadata(text: String): String {
        return text.replace(METADATA_CLEAN_REGEX, "")
            .trim()
            .replace(WHITESPACE_REGEX, " ")
    }

    fun downloadSong(song: Song) = viewModelScope.launch(Dispatchers.IO) {
        _downloadMessage.emit("Song added to download queue")
        notificationRepository.insertNotification("Download Queued", song.title)

        songRepository.insertSong(song)
        // Add to downloads playlist immediately if not already there
        if (!playlistRepository.isSongInPlaylist(song.id, downloadsPlaylistId)) {
            playlistRepository.addSongToPlaylist(song.id, downloadsPlaylistId)
        }
        
        val inputData = Data.Builder()
            .putString("song_id", song.id)
            .putString("song_url", song.streamUrl ?: "https://www.youtube.com/watch?v=${song.id}")
            .build()
            
        val downloadRequest = OneTimeWorkRequestBuilder<DownloadWorker>()
            .setInputData(inputData)
            .addTag("download_task")
            .addTag("id_${song.id}")
            .build()
            
        // Using APPEND ensures that multiple requests form a chain.
        // It runs in the background via WorkManager even if the app is closed.
        WorkManager.getInstance(getApplication()).enqueueUniqueWork(
            "song_download_queue",
            ExistingWorkPolicy.APPEND,
            downloadRequest
        )
    }

    fun downloadAllFavorites() = viewModelScope.launch {
        playlistRepository.getSongsInPlaylist(favoritesPlaylistId).first().forEach { song ->
            if (!downloadedSongIds.value.contains(song.id)) {
                downloadSong(song)
            }
        }
    }

    fun deleteSongs(songs: Set<Song>) {
        viewModelScope.launch {
            songs.forEach { song ->
                songRepository.deleteSong(song)
                // Delete physical files
                song.localPath?.let { File(it).delete() }
                // Delete thumbnail if it's local
                if (song.thumbnailUrl.startsWith("/")) File(song.thumbnailUrl).delete()
                // Delete lyrics file
                val lyricsFile = File(getApplication<Application>().getExternalFilesDir(null), "music_storage/${song.id}.lrc")
                if (lyricsFile.exists()) lyricsFile.delete()
            }
            _downloadMessage.emit("Deleted ${songs.size} items")
        }
    }

    fun deleteDownload(song: Song) {
        viewModelScope.launch(Dispatchers.IO) {
            val savedSong = songRepository.getSongById(song.id) ?: song
            
            // 1. Delete the physical audio file
            val localPath = savedSong.localPath
            if (localPath != null) {
                val file = File(localPath)
                if (file.exists()) file.delete()
            }
            
            // 2. Clear local data but KEEP the song in database for playlists
            val updatedSong = savedSong.copy(
                localPath = null,
                isDownloaded = false
            )
            songRepository.insertSong(updatedSong)
            
            // 3. Remove from the "Downloads" system playlist
            playlistRepository.removeSongFromPlaylist(song.id, downloadsPlaylistId)
            
            _downloadMessage.emit("Download removed from device")
            notificationRepository.insertNotification("Storage", "Removed ${song.title} from device")
        }
    }

    fun cancelDownload(songId: String) {
        WorkManager.getInstance(getApplication()).cancelUniqueWork("download_$songId")
    }

    fun playSong(song: Song) {
        activePlaylist = null
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch { playSongInternal(song) }
    }

    private suspend fun playSongInternal(song: Song) {
        cancelBackgroundQueuing()
        musicPlayer.stop()
        musicPlayer.setLoading(true)
        _lyrics.value = "Searching for lyrics..."
        _syncedLyrics.value = null
        
        val savedSong = songRepository.getSongById(song.id)
        val initialThumbnail = if (savedSong != null && isHighRes(savedSong.thumbnailUrl)) {
            savedSong.thumbnailUrl
        } else {
            val original = song.thumbnailUrl
            if (original.isBlank() && song.id.length == 11) {
                "https://img.youtube.com/vi/${song.id}/hqdefault.jpg"
            } else {
                original.replace("default.jpg", "hqdefault.jpg")
                        .replace("sddefault.jpg", "hqdefault.jpg")
            }
        }
        
        _albumCoverUrl.value = initialThumbnail
        
        musicPlayer.updateCurrentSong(song)

        val cachedUrl = musicPlayer.getCachedUrl(song.id)
        if (cachedUrl != null) {
            musicPlayer.playSong(song)
        } else {
            val streamUrl = resolveStreamUrl(song, isPriority = true)
            if (streamUrl != null) {
                musicPlayer.cacheStreamUrl(song.id, streamUrl)
                musicPlayer.playSong(song)
            } else {
                _lyrics.value = "Error: Video unavailable."
                musicPlayer.setLoading(false)
            }
        }
    }

    fun playPlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            playlistLoadingJob?.cancel()
            activePlaylist = songs
            
            val firstSong = songs.first()
            playSongInternal(firstSong)
            
            for (song in songs.drop(1).take(8)) {
                loadAndQueueNext(song, bypassValidation = true)
            }
        }
    }

    fun playPlaylistFromSong(songs: List<Song>, song: Song) {
        if (songs.isEmpty()) return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            playlistLoadingJob?.cancel()
            activePlaylist = songs
            
            // Starting song is always allowed if user clicked it
            playSongInternal(song)
            
            val index = songs.indexOfFirst { it.id == song.id }
            if (index != -1) {
                for (nextSong in songs.drop(index + 1).take(8)) {
                    loadAndQueueNext(nextSong, bypassValidation = true)
                }
            }
        }
    }

    fun shufflePlaylist(songs: List<Song>) {
        if (songs.isEmpty()) return
        playbackJob?.cancel()
        playbackJob = viewModelScope.launch {
            playlistLoadingJob?.cancel()
            val shuffled = songs.shuffled()
            activePlaylist = shuffled
            
            val randomFirst = shuffled.first()
            playSongInternal(randomFirst)
            
            for (song in shuffled.drop(1).take(8)) {
                loadAndQueueNext(song, bypassValidation = true)
            }
        }
    }

    fun addToQueue(song: Song) = viewModelScope.launch {
        val streamUrl = musicPlayer.getCachedUrl(song.id) ?: resolveStreamUrl(song)
        if (streamUrl != null) {
            musicPlayer.cacheStreamUrl(song.id, streamUrl)
            musicPlayer.addToQueue(song)
        }
    }

    fun toggleFavorite(song: Song) = viewModelScope.launch {
        val isFav = favoriteSongIds.value.contains(song.id)
        if (isFav) {
            playlistRepository.removeSongFromPlaylist(song.id, favoritesPlaylistId)
            notificationRepository.insertNotification("Favorites", "Removed ${song.title} from favorites")
        } else {
            songRepository.insertSong(song)
            playlistRepository.addSongToPlaylist(song.id, favoritesPlaylistId)
            notificationRepository.insertNotification("Favorites", "Added ${song.title} to favorites")
            // Proactively fetch high-res metadata
            if (!isHighRes(song.thumbnailUrl)) {
                launch { fetchSongMetadata(song) }
            }
        }
    }

    fun addSongToPlaylist(song: Song, playlistId: String) = viewModelScope.launch {
        songRepository.insertSong(song)
        playlistRepository.addSongToPlaylist(song.id, playlistId)
        _downloadMessage.emit("Added to playlist")
        notificationRepository.insertNotification("Playlist Update", "Added ${song.title} to playlist")
        // Proactively fetch high-res metadata
        if (!isHighRes(song.thumbnailUrl)) {
            launch { fetchSongMetadata(song) }
        }
    }

    fun createPlaylist(name: String, description: String? = null, songs: Set<Song> = emptySet()) = viewModelScope.launch {
        val playlistId = java.util.UUID.randomUUID().toString()
        playlistRepository.createPlaylist(name, description, playlistId)
        _downloadMessage.emit("Playlist '$name' created")
        songs.forEach { song ->
            addSongToPlaylist(song, playlistId)
        }
    }

    private suspend fun resolveStreamUrl(song: Song, isPriority: Boolean = false): String? = withContext(Dispatchers.IO) {
        // 0. Check for local path (either in the passed object or the database)
        val savedSong = songRepository.getSongById(song.id)
        val localPath = savedSong?.localPath ?: song.localPath
        
        if (localPath != null && File(localPath).exists()) {
            Log.d("MusicViewModel", "Serving local file for ${song.id}")
            return@withContext localPath
        }

        // 1. Check DB Cache first
        val cached = streamUrlCacheDao.getCachedUrl(song.id)
        if (cached != null && System.currentTimeMillis() - cached.timestamp < 30 * 60 * 1000) {
            Log.d("MusicViewModel", "Serving stream from DB cache for ${song.id}")
            if (verifyUrl(cached.url)) return@withContext cached.url
            else streamUrlCacheDao.deleteCachedUrl(song.id)
        }

        try {
            val t0 = System.currentTimeMillis()
            Log.d("TIMING", "resolveStreamUrl START for ${song.id} (priority=$isPriority)")
            
            // If priority is true, we bypass the limited dispatcher to avoid being queued behind background tasks
            val dispatcher = if (isPriority) Dispatchers.IO else extractionDispatcher
            
            val streamUrl = withContext(dispatcher) {
                YoutubeDlUtil.getVideoInfo(getApplication(), song.streamUrl ?: "https://www.youtube.com/watch?v=${song.id}")?.url
            }
            Log.d("TIMING", "resolveStreamUrl END: ${System.currentTimeMillis() - t0}ms")
            
            if (streamUrl != null) {
                // Save to DB cache
                streamUrlCacheDao.insertCachedUrl(
                    StreamUrlCacheEntity(
                        videoId = song.id,
                        url = streamUrl,
                        timestamp = System.currentTimeMillis()
                    )
                )
                return@withContext streamUrl
            }
        } catch (e: Exception) {
            if (e is CancellationException) {
                Log.d("MusicViewModel", "YT-DLP resolution cancelled for ${song.id}")
            } else {
                Log.e("MusicViewModel", "YT-DLP resolution failed for ${song.id}", e)
            }
        }
        
        null
    }

    private suspend fun verifyUrl(url: String?): Boolean = withContext(Dispatchers.IO) {
        if (url == null) return@withContext false
        try {
            val request = okhttp3.Request.Builder()
                .url(url)
                .header("Range", "bytes=0-1")
                .header("User-Agent", "Mozilla/5.0")
                .build()
            verificationClient.newCall(request).execute().use { it.isSuccessful }
        } catch (e: Exception) {
            false
        }
    }

    private suspend fun fetchLyrics(song: Song) {
        try {
            val savedSong = songRepository.getSongById(song.id)
            if (savedSong?.lyrics != null && savedSong.lyrics.isNotBlank()) {
                _lyrics.value = savedSong.lyrics
                _syncedLyrics.value = LyricsParser.parse(savedSong.lyrics)
                return
            }

            val (artist, title) = extractArtistAndTitle(song.artist, song.title)
            val durationSeconds = if (song.duration > 0) (song.duration / 1000).toInt() else null
            val lyricsResult = lyricsRepository.getLyrics(
                songTitle = title,
                artist = artist,
                duration = durationSeconds,
                originalTitle = song.title,
                originalArtist = song.artist
            )
            
            _lyrics.value = lyricsResult
            _syncedLyrics.value = LyricsParser.parse(lyricsResult)

            if (lyricsResult.isNotBlank() && !lyricsResult.startsWith("Error") && !lyricsResult.contains("not found")) {
                songRepository.insertSong(song.copy(lyrics = lyricsResult))
            }
        } catch (e: Exception) { 
            _lyrics.value = "Failed to load lyrics." 
        }
    }

    suspend fun fetchSongMetadata(song: Song) {
        if (fetchingMetadataIds.value.contains(song.id)) return
        fetchingMetadataIds.update { it + song.id }
        try {
            val (artist, title) = extractArtistAndTitle(song.artist, song.title)
            
            // PRIORITY 1: High Quality Album Art (Parallel Race: iTunes, Last.fm, Genius)
            val albumCover = metadataRepository.getAlbumCover(artist, title)
            if (!albumCover.isNullOrBlank()) {
                withContext(Dispatchers.Main) {
                    _albumCoverUrl.value = albumCover
                }
                // Persist the high-res URL to the DB and update player state
                val current = songRepository.getSongById(song.id) ?: song
                val updatedSong = current.copy(thumbnailUrl = albumCover)
                songRepository.insertSong(updatedSong)
                
                // Only update musicPlayer if it's the current song
                if (playerState.value.currentSong?.id == song.id) {
                    musicPlayer.updateCurrentSong(updatedSong)
                    musicPlayer.updateSongMetadata(updatedSong)
                }
                return
            }
            
            // PRIORITY 2: Artist Image fallback (Only if no album art found)
            val artistImage = metadataRepository.getArtistImage(artist)
            if (!artistImage.isNullOrBlank() && !artistImage.contains("default_album")) {
                withContext(Dispatchers.Main) {
                    _albumCoverUrl.value = artistImage
                }
                // Also persist fallback artist image so Home Screen shows it
                val current = songRepository.getSongById(song.id) ?: song
                val updatedSong = current.copy(thumbnailUrl = artistImage)
                songRepository.insertSong(updatedSong)
                
                // Only update musicPlayer if it's the current song
                if (playerState.value.currentSong?.id == song.id) {
                    musicPlayer.updateCurrentSong(updatedSong)
                    musicPlayer.updateSongMetadata(updatedSong)
                }
            }
        } catch (e: Exception) {
            Log.e("MusicViewModel", "Metadata fetch failed for ${song.title}", e)
        } finally {
            fetchingMetadataIds.update { it - song.id }
        }
    }

    fun togglePlayPause() = musicPlayer.togglePlayPause()
    fun skipNext() = musicPlayer.skipNext()
    fun skipPrevious() = musicPlayer.skipPrevious()
    fun skipToQueueItem(index: Int) = musicPlayer.skipToQueueItem(index)
    fun playNext(song: Song) {
        musicPlayer.addNext(song)
    }
    fun seekTo(position: Long) = musicPlayer.seekTo(position)
    fun setRepeatMode(mode: Int) = musicPlayer.setRepeatMode(mode)
    fun toggleShuffle() = musicPlayer.toggleShuffle()
    fun moveQueueItem(from: Int, to: Int) = musicPlayer.moveQueueItem(from, to)
    fun removeQueueItem(index: Int) = musicPlayer.removeQueueItem(index)

    fun startSleepTimer(minutes: Int) {
        viewModelScope.launch {
            var remainingMillis = minutes * 60 * 1000L
            while (remainingMillis > 0) {
                _sleepTimerRemaining.value = remainingMillis
                delay(1000)
                remainingMillis -= 1000
            }
            _sleepTimerRemaining.value = null
            if (playerState.value.isPlaying) musicPlayer.togglePlayPause() 
        }
    }
    fun cancelSleepTimer() {
        _sleepTimerRemaining.value = null
    }

    /**
     * Retrieves a song's detailed information by its ID.
     */
    fun retrieveSongById(songId: String, callback: (Song?) -> Unit) {
        viewModelScope.launch {
            val localSong = songRepository.getSongById(songId)
            if (localSong != null) {
                callback(localSong)
                return@launch
            }

            try {
                val results = YoutubeDlUtil.search(getApplication(), songId)
                val song = results.find { it.id == songId }
                if (song != null) {
                    launch { fetchSongMetadata(song) }
                    callback(song)
                } else {
                    callback(null)
                }
            } catch (e: Throwable) {
                callback(null)
            }
        }
    }

    /**
     * Retrieves a song from a given URL.
     */
    fun retrieveSongFromUrl(url: String, callback: (Song?) -> Unit) {
        viewModelScope.launch {
            val t0 = System.currentTimeMillis()
            Log.d("TIMING", "START: $url")
            try {
                val videoId = extractVideoId(url)
                
                // 1. Check Cache
                pipedVideoCache[videoId]?.let {
                    Log.d("TIMING", "CACHE HIT: ${System.currentTimeMillis() - t0}ms")
                    callback(it)
                    return@launch
                }

                // 2. Fallback to YT-DLP
                Log.d("TIMING", "Calling YT-DLP for info")
                val videoInfo = withContext(Dispatchers.IO) {
                    Log.d("TIMING", "BEFORE yt-dlp: ${System.currentTimeMillis() - t0}ms")
                    YoutubeDlUtil.getVideoInfo(getApplication(), url)
                }
                Log.d("TIMING", "AFTER yt-dlp: ${System.currentTimeMillis() - t0}ms")

                if (videoInfo != null && videoInfo.id != null) {
                    val song = Song(
                        id = videoInfo.id!!,
                        title = videoInfo.title ?: "Unknown",
                        artist = videoInfo.uploader ?: "Unknown Artist",
                        album = "YouTube",
                        duration = videoInfo.duration.toLong() * 1000L,
                        thumbnailUrl = "https://img.youtube.com/vi/${videoInfo.id}/hqdefault.jpg",
                        streamUrl = videoInfo.url
                    )
                    Log.d("TIMING", "SONG CREATED: ${System.currentTimeMillis() - t0}ms")

                    // Proactively cache the stream URL we just got to avoid double yt-dlp calls
                    videoInfo.url?.let { musicPlayer.cacheStreamUrl(song.id, it) }

                    launch { fetchSongMetadata(song) }
                    callback(song)
                    Log.d("TIMING", "CALLBACK DONE (YT-DLP): ${System.currentTimeMillis() - t0}ms")
                } else {
                    callback(null)
                }
            } catch (e: Throwable) {
                Log.e("TIMING", "ERROR", e)
                callback(null)
            }
        }
    }

    private fun extractVideoId(url: String): String {
        return if (url.contains("v=")) {
            url.substringAfter("v=").substringBefore("&")
        } else if (url.contains("youtu.be/")) {
            url.substringAfter("youtu.be/").substringBefore("?")
        } else {
            url.substringAfterLast("/")
        }
    }
}
