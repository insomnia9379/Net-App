package com.netapp.marketplacescanner.data.repo

import android.content.Context
import com.netapp.marketplacescanner.data.db.AppDatabase
import com.netapp.marketplacescanner.data.db.Listing
import com.netapp.marketplacescanner.data.db.SavedSearch
import com.netapp.marketplacescanner.data.model.ScannedListing
import com.netapp.marketplacescanner.data.net.DealScorer
import com.netapp.marketplacescanner.data.net.MarketplaceClient
import com.netapp.marketplacescanner.data.net.ScanResult
import com.netapp.marketplacescanner.data.net.SessionStore
import com.netapp.marketplacescanner.notify.Alert
import com.netapp.marketplacescanner.notify.AlertKind
import kotlinx.coroutines.flow.Flow

/**
 * Central coordinator: owns the DB, the network client and the session, and
 * turns a raw scan into persisted listings + the alerts that should fire.
 */
class ScannerRepository private constructor(
    context: Context,
    val session: SessionStore,
) {
    private val db = AppDatabase.get(context)
    private val searchDao = db.searchDao()
    private val listingDao = db.listingDao()
    private val client = MarketplaceClient(context, session)

    fun observeSearches(): Flow<List<SavedSearch>> = searchDao.observeAll()
    fun observeSearch(id: Long): Flow<SavedSearch?> = searchDao.observe(id)
    fun observeListings(searchId: Long): Flow<List<Listing>> = listingDao.observeForSearch(searchId)
    fun observeListingsByRank(searchId: Long): Flow<List<Listing>> =
        listingDao.observeForSearchByRank(searchId)
    fun observeUnreadCount(): Flow<Int> = listingDao.observeUnreadCount()
    fun observeSaved(): Flow<List<Listing>> = listingDao.observeSaved()

    suspend fun setSaved(listing: Listing, saved: Boolean) =
        listingDao.setSaved(listing.rowId, saved, "APP")

    suspend fun getSearch(id: Long): SavedSearch? = searchDao.byId(id)
    suspend fun saveSearch(search: SavedSearch): Long = searchDao.upsert(search)
    suspend fun updateSearch(search: SavedSearch) = searchDao.update(search)
    suspend fun deleteSearch(search: SavedSearch) {
        listingDao.deleteForSearch(search.id)
        searchDao.delete(search)
    }

    suspend fun markRead(searchId: Long) = listingDao.markSearchRead(searchId)

    suspend fun enabledSearches(): List<SavedSearch> = searchDao.enabledSearches()

    /**
     * Scans one search, persists results and returns the alerts that should be
     * shown. Returns null only when the session needs re-authentication, so the
     * caller can surface a single "please log in again" prompt.
     */
    suspend fun scan(search: SavedSearch): ScanOutcome {
        return when (val result = client.scan(search)) {
            is ScanResult.AuthRequired -> ScanOutcome.AuthRequired
            is ScanResult.Error -> ScanOutcome.Failed(result.message)
            is ScanResult.Success -> {
                val alerts = reconcile(search, result.listings)
                searchDao.markScanned(search.id, System.currentTimeMillis(), result.listings.size)
                ScanOutcome.Completed(result.listings.size, alerts)
            }
        }
    }

    /**
     * Ingests listings captured by the foreground (visible WebView) scanner:
     * scores them, persists them, records the scan, and returns the alerts.
     */
    suspend fun ingest(search: SavedSearch, scanned: List<ScannedListing>): ScanOutcome {
        val alerts = reconcile(search, scanned)
        searchDao.markScanned(search.id, System.currentTimeMillis(), scanned.size)
        return ScanOutcome.Completed(scanned.size, alerts)
    }

    /** Persists scanned listings and decides which ones warrant an alert. */
    private suspend fun reconcile(
        search: SavedSearch,
        scanned: List<ScannedListing>,
    ): List<Alert> {
        val keywords = search.mustIncludeKeywords
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        val excluded = search.excludeKeywords
            .split(",").map { it.trim().lowercase() }.filter { it.isNotEmpty() }

        // Keep only the listings relevant to this search; their prices are the
        // comparables that define "market value" for scoring.
        val matching = scanned.filter { matchesKeywords(it, keywords, excluded) }
        val prices = matching.mapNotNull { it.price }.filter { it > 0 }
        val marketValue = DealScorer.median(prices)

        val alerts = mutableListOf<Alert>()
        // `matching` is in Facebook's requested order (newest- or nearest-first),
        // so the index is the display rank.
        matching.forEachIndexed { itemRank, item ->
            val existing = listingDao.find(search.id, item.id)
            val now = System.currentTimeMillis()
            val lowest = minOfNullable(existing?.lowestPrice, item.price)

            val isNewListing = existing == null
            val priceDropped = existing?.price != null && item.price != null &&
                item.price < existing.price
            val underThreshold = search.alertUnderPrice?.let { threshold ->
                item.price != null && item.price <= threshold
            } ?: false

            val score = DealScorer.score(
                price = item.price,
                marketValue = marketValue,
                underThreshold = search.alertUnderPrice,
                comparableCount = prices.size,
            )

            val kind = when {
                priceDropped && search.alertOnPriceDrop -> AlertKind.PRICE_DROP
                isNewListing && underThreshold -> AlertKind.UNDER_PRICE
                isNewListing && search.alertOnNew && search.alertUnderPrice == null ->
                    AlertKind.NEW
                else -> null
            }
            val shouldAlert = kind != null

            listingDao.upsert(
                Listing(
                    rowId = existing?.rowId ?: 0,
                    searchId = search.id,
                    listingId = item.id,
                    title = item.title,
                    price = item.price,
                    priceText = item.priceText,
                    location = item.location,
                    imageUrl = item.imageUrl,
                    url = item.url,
                    lowestPrice = lowest,
                    score = score,
                    saved = existing?.saved ?: false,
                    savedSource = existing?.savedSource ?: "APP",
                    rank = itemRank,
                    firstSeenAt = existing?.firstSeenAt ?: now,
                    lastSeenAt = now,
                    isNew = isNewListing || (existing?.isNew ?: false) || shouldAlert,
                    notified = (existing?.notified ?: false) || shouldAlert,
                )
            )

            if (shouldAlert && kind != null) {
                alerts += Alert(
                    title = item.title,
                    priceText = item.priceText,
                    location = item.location,
                    url = item.url,
                    kind = kind,
                )
            }
        }
        return alerts
    }

    private fun matchesKeywords(
        item: ScannedListing,
        required: List<String>,
        excluded: List<String>,
    ): Boolean {
        val haystack = item.title.lowercase()
        if (excluded.any { it in haystack }) return false
        if (required.isEmpty()) return true
        return required.all { it in haystack }
    }

    private fun minOfNullable(a: Int?, b: Int?): Int? = when {
        a == null -> b
        b == null -> a
        else -> minOf(a, b)
    }

    companion object {
        @Volatile
        private var instance: ScannerRepository? = null

        fun get(context: Context): ScannerRepository =
            instance ?: synchronized(this) {
                instance ?: ScannerRepository(
                    context.applicationContext,
                    SessionStore(context.applicationContext)
                ).also { instance = it }
            }
    }
}

sealed interface ScanOutcome {
    data class Completed(val total: Int, val alerts: List<Alert>) : ScanOutcome
    data object AuthRequired : ScanOutcome
    data class Failed(val message: String) : ScanOutcome
}
