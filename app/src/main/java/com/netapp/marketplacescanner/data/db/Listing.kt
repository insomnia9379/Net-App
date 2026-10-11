package com.netapp.marketplacescanner.data.db

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * A listing seen for a given search. The composite of (searchId, listingId) is
 * unique so we can dedupe across scans and track price history for drop alerts.
 */
@Entity(
    tableName = "listings",
    indices = [Index(value = ["searchId", "listingId"], unique = true)]
)
data class Listing(
    @PrimaryKey(autoGenerate = true) val rowId: Long = 0,

    val searchId: Long,
    /** Marketplace listing id. */
    val listingId: String,

    val title: String,
    val price: Int?,
    val priceText: String,
    val location: String,
    val imageUrl: String,
    val url: String,

    /** Lowest price we have ever observed for this listing. */
    val lowestPrice: Int?,

    /** Position of this listing in the most recent scan's result order. */
    val rank: Int = 0,

    val firstSeenAt: Long = System.currentTimeMillis(),
    val lastSeenAt: Long = System.currentTimeMillis(),

    /** True until the user opens/acknowledges it. Drives the unread badge. */
    val isNew: Boolean = true,
    val notified: Boolean = false,
)
