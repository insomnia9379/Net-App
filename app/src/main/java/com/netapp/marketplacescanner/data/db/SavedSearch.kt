package com.netapp.marketplacescanner.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * A user-defined Marketplace search. Each search is scanned periodically and
 * generates alerts according to its configured triggers.
 */
@Entity(tableName = "saved_searches")
data class SavedSearch(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,

    /** Free-text query, e.g. "ikea desk". */
    val query: String,

    /** Optional category slug used by Marketplace, e.g. "furniture". Blank = all. */
    val category: String = "",

    /** Marketplace location/place id. Blank uses the account's default location. */
    val locationId: String = "",
    val locationLabel: String = "",

    /** Search radius in kilometers. */
    val radiusKm: Int = 40,

    val minPrice: Int? = null,
    val maxPrice: Int? = null,

    /**
     * Comma-separated keywords that must appear in the title/description for a
     * listing to count as a match. Blank = no keyword filtering.
     */
    val mustIncludeKeywords: String = "",

    /**
     * Comma-separated words that, if present in a listing's title, exclude it —
     * for filtering out results that have nothing to do with the search.
     */
    val excludeKeywords: String = "",

    /** Result ordering: [SORT_NEWEST] or [SORT_NEAREST]. */
    val sortBy: String = SORT_NEWEST,

    // --- Alert triggers ---
    val alertOnNew: Boolean = true,
    val alertOnPriceDrop: Boolean = true,
    /** When set, only alert if the price is at or below this value. */
    val alertUnderPrice: Int? = null,

    val enabled: Boolean = true,
    val createdAt: Long = System.currentTimeMillis(),
    val lastScannedAt: Long = 0L,
    val lastResultCount: Int = 0,
) {
    companion object {
        const val SORT_NEWEST = "NEWEST"
        const val SORT_NEAREST = "NEAREST"
    }
}
