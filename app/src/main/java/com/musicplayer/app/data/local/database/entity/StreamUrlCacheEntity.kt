package com.musicplayer.app.data.local.database.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "stream_url_cache")
data class StreamUrlCacheEntity(
    @PrimaryKey val videoId: String,
    val url: String,
    val timestamp: Long
)
