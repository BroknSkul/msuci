package com.musicplayer.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musicplayer.app.data.model.Song

@Entity(tableName = "recent_played_songs")
data class RecentPlayedSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val thumbnailUrl: String,
    val streamUrl: String?,
    val localPath: String?,
    val isDownloaded: Boolean,
    val genre: String?,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "recent_search_songs")
data class RecentSearchSongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val thumbnailUrl: String,
    val streamUrl: String?,
    val localPath: String?,
    val isDownloaded: Boolean,
    val genre: String?,
    val timestamp: Long = System.currentTimeMillis()
)

fun RecentPlayedSongEntity.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    streamUrl = streamUrl,
    localPath = localPath,
    isDownloaded = isDownloaded,
    genre = genre
)

fun Song.toRecentPlayedEntity() = RecentPlayedSongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    streamUrl = streamUrl,
    localPath = localPath,
    isDownloaded = isDownloaded,
    genre = genre,
    timestamp = System.currentTimeMillis()
)

fun RecentSearchSongEntity.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    streamUrl = streamUrl,
    localPath = localPath,
    isDownloaded = isDownloaded,
    genre = genre
)

fun Song.toRecentSearchEntity() = RecentSearchSongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    duration = duration,
    thumbnailUrl = thumbnailUrl,
    streamUrl = streamUrl,
    localPath = localPath,
    isDownloaded = isDownloaded,
    genre = genre,
    timestamp = System.currentTimeMillis()
)
