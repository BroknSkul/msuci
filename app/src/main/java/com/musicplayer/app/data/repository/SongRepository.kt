package com.musicplayer.app.data.repository

import com.musicplayer.app.data.local.database.SongDao
import com.musicplayer.app.data.local.database.RecentPlayedSongDao
import com.musicplayer.app.data.local.database.RecentSearchSongDao
import com.musicplayer.app.data.local.database.entity.SongEntity
import com.musicplayer.app.data.local.database.entity.toRecentPlayedEntity
import com.musicplayer.app.data.local.database.entity.toRecentSearchEntity
import com.musicplayer.app.data.local.database.entity.toDomain
import com.musicplayer.app.data.local.database.entity.toEntity
import com.musicplayer.app.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SongRepository @Inject constructor(
    private val songDao: SongDao,
    private val recentPlayedSongDao: RecentPlayedSongDao,
    private val recentSearchSongDao: RecentSearchSongDao
) {
    private val repositoryScope = CoroutineScope(Dispatchers.IO)

    val recentPlayedSongs: StateFlow<List<Song>> = recentPlayedSongDao.getRecentPlayedSongs()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    val recentSearchSongs: StateFlow<List<Song>> = recentSearchSongDao.getRecentSearchSongs()
        .map { entities -> entities.map { it.toDomain() } }
        .stateIn(repositoryScope, SharingStarted.Eagerly, emptyList())

    // For backward compatibility
    val recentSongs = recentPlayedSongs

    fun getAllSongs(): Flow<List<Song>> = songDao.getAllSongs().map { entities ->
        entities.map { it.toDomain() }
    }

    fun getDownloadedSongs(): Flow<List<Song>> = songDao.getDownloadedSongs().map { entities ->
        entities.map { it.toDomain() }
    }

    suspend fun getDownloadedSongsSync(): List<Song> = 
        songDao.getDownloadedSongsSync().map { it.toDomain() }

    suspend fun getSongById(id: String): Song? = songDao.getSongById(id)?.toDomain()

    suspend fun insertSong(song: Song) {
        val existingSong = songDao.getSongById(song.id)
        
        // Preserve high-res thumbnail and lyrics if incoming song has basic ones
        val thumbnailToSave = if (existingSong != null && isBetterThumbnail(existingSong.thumbnailUrl, song.thumbnailUrl)) {
            existingSong.thumbnailUrl
        } else {
            song.thumbnailUrl
        }
        
        val lyricsToSave = if (song.lyrics.isNullOrBlank() && !existingSong?.lyrics.isNullOrBlank()) {
            existingSong?.lyrics
        } else {
            song.lyrics
        }

        val entity = song.copy(thumbnailUrl = thumbnailToSave, lyrics = lyricsToSave).toEntity()
        if (songDao.insertSong(entity) == -1L) {
            songDao.updateSong(entity)
        }
        
        // Robust sync with recent tables
        repositoryScope.launch {
            if (thumbnailToSave.isNotBlank()) {
                recentPlayedSongDao.updateThumbnail(song.id, thumbnailToSave)
                recentSearchSongDao.updateThumbnail(song.id, thumbnailToSave)
            }
        }
    }

    suspend fun deleteSong(song: Song) {
        songDao.deleteSong(song.toEntity())
    }

    suspend fun clearAllDownloads() {
        songDao.clearAllDownloads()
    }

    fun addRecentPlayedSong(song: Song) {
        repositoryScope.launch {
            // Check if we already have better metadata in the main songs table
            val existingSong = songDao.getSongById(song.id)
            val songToSave = if (existingSong != null && isBetterThumbnail(existingSong.thumbnailUrl, song.thumbnailUrl)) {
                song.copy(
                    thumbnailUrl = existingSong.thumbnailUrl,
                    title = existingSong.title,
                    artist = existingSong.artist
                )
            } else {
                song
            }
            recentPlayedSongDao.addRecentPlayedSong(songToSave.toRecentPlayedEntity())
        }
    }

    fun addRecentSearchSong(song: Song) {
        repositoryScope.launch {
            val existingSong = songDao.getSongById(song.id)
            val songToSave = if (existingSong != null && isBetterThumbnail(existingSong.thumbnailUrl, song.thumbnailUrl)) {
                song.copy(
                    thumbnailUrl = existingSong.thumbnailUrl,
                    title = existingSong.title,
                    artist = existingSong.artist
                )
            } else {
                song
            }
            recentSearchSongDao.addRecentSearchSong(songToSave.toRecentSearchEntity())
        }
    }
    
    private fun isBetterThumbnail(savedUrl: String, incomingUrl: String): Boolean {
        if (savedUrl.isBlank()) return false
        if (incomingUrl.isBlank()) return true
        
        val isSavedLocal = savedUrl.startsWith("/") || savedUrl.startsWith("file:")
        val isIncomingLocal = incomingUrl.startsWith("/") || incomingUrl.startsWith("file:")
        
        val isSavedHighRes = savedUrl.startsWith("http") && !savedUrl.contains("ytimg.com") && !savedUrl.contains("youtube.com")
        val isIncomingHighRes = incomingUrl.startsWith("http") && !incomingUrl.contains("ytimg.com") && !incomingUrl.contains("youtube.com")
        
        // Priority: Local Path > High-res URL > YouTube Thumbnail
        
        if (isSavedLocal) return !isIncomingLocal // Keep saved local unless incoming is also local
        if (isSavedHighRes) return !isIncomingLocal && !isIncomingHighRes // Keep high-res unless incoming is local
        
        return false // Saved is YouTube, so incoming (whatever it is) is likely better or equal
    }
    
    fun addRecentSong(song: Song) = addRecentPlayedSong(song)

    suspend fun updateLyrics(songId: String, lyrics: String) {
        val song = songDao.getSongById(songId)
        if (song != null) {
            songDao.insertSong(song.copy(lyrics = lyrics))
        }
    }
}
