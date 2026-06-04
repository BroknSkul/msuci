package com.musicplayer.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.musicplayer.app.data.model.Song

@Entity(tableName = "songs")
data class SongEntity(
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
    val lyrics: String? = null
)

fun SongEntity.toDomain() = Song(
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
    lyrics = lyrics
)

fun Song.toEntity() = SongEntity(
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
    lyrics = lyrics
)
