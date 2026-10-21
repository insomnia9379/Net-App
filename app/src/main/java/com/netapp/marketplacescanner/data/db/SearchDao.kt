package com.netapp.marketplacescanner.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface SearchDao {
    @Query("SELECT * FROM saved_searches ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<SavedSearch>>

    @Query("SELECT * FROM saved_searches WHERE enabled = 1")
    suspend fun enabledSearches(): List<SavedSearch>

    @Query("SELECT * FROM saved_searches WHERE id = :id")
    fun observe(id: Long): Flow<SavedSearch?>

    @Query("SELECT * FROM saved_searches WHERE id = :id")
    suspend fun byId(id: Long): SavedSearch?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(search: SavedSearch): Long

    @Update
    suspend fun update(search: SavedSearch)

    @Delete
    suspend fun delete(search: SavedSearch)

    @Query("UPDATE saved_searches SET lastScannedAt = :ts, lastResultCount = :count WHERE id = :id")
    suspend fun markScanned(id: Long, ts: Long, count: Int)
}
