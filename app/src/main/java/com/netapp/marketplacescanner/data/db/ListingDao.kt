package com.netapp.marketplacescanner.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ListingDao {
    @Query("SELECT * FROM listings WHERE searchId = :searchId ORDER BY lastSeenAt DESC")
    fun observeForSearch(searchId: Long): Flow<List<Listing>>

    /** Nearest-first: the scan is requested sorted by distance, so rank encodes it. */
    @Query("SELECT * FROM listings WHERE searchId = :searchId ORDER BY rank ASC")
    fun observeForSearchByRank(searchId: Long): Flow<List<Listing>>

    @Query("SELECT * FROM listings ORDER BY firstSeenAt DESC LIMIT :limit")
    fun observeRecent(limit: Int = 200): Flow<List<Listing>>

    @Query("SELECT * FROM listings WHERE searchId = :searchId AND listingId = :listingId LIMIT 1")
    suspend fun find(searchId: Long, listingId: String): Listing?

    @Query("SELECT COUNT(*) FROM listings WHERE isNew = 1")
    fun observeUnreadCount(): Flow<Int>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(listing: Listing): Long

    @Query("UPDATE listings SET isNew = 0 WHERE searchId = :searchId")
    suspend fun markSearchRead(searchId: Long)

    @Query("UPDATE listings SET notified = 1 WHERE rowId IN (:rowIds)")
    suspend fun markNotified(rowIds: List<Long>)

    @Query("DELETE FROM listings WHERE searchId = :searchId")
    suspend fun deleteForSearch(searchId: Long)
}
