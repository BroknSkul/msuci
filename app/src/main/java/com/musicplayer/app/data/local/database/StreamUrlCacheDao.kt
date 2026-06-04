package com.musicplayer.app.data.local.database

import androidx.room.*
import com.musicplayer.app.data.local.database.entity.StreamUrlCacheEntity

@Dao
interface StreamUrlCacheDao {
    @Query("SELECT * FROM stream_url_cache WHERE videoId = :videoId")
    suspend fun getCachedUrl(videoId: String): StreamUrlCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCachedUrl(cache: StreamUrlCacheEntity)

    @Query("DELETE FROM stream_url_cache WHERE videoId = :videoId")
    suspend fun deleteCachedUrl(videoId: String)

    @Query("DELETE FROM stream_url_cache WHERE timestamp < :expiryTime")
    suspend fun clearExpired(expiryTime: Long)
}
