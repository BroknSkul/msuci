package com.musicplayer.app.data.local.database

import androidx.room.*
import com.musicplayer.app.data.local.database.entity.SearchHistoryEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchHistoryDao {
    @Query("SELECT * FROM search_history ORDER BY timestamp DESC LIMIT 10")
    fun getSearchHistory(): Flow<List<SearchHistoryEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSearchQuery(query: SearchHistoryEntity)

    @Query("DELETE FROM search_history WHERE query NOT IN (SELECT query FROM search_history ORDER BY timestamp DESC LIMIT 10)")
    suspend fun trimHistory()

    @Transaction
    suspend fun addSearchQuery(query: SearchHistoryEntity) {
        insertSearchQuery(query)
        trimHistory()
    }

    @Query("DELETE FROM search_history WHERE `query` = :query")
    suspend fun deleteSearchQuery(query: String)

    @Query("DELETE FROM search_history")
    suspend fun clearHistory()
}
