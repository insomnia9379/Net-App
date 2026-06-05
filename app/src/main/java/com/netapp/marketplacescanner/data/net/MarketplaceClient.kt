package com.netapp.marketplacescanner.data.net

import com.netapp.marketplacescanner.data.db.SavedSearch
import com.netapp.marketplacescanner.data.model.ScannedListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

/** Outcome of a single search fetch. */
sealed interface ScanResult {
    data class Success(val listings: List<ScannedListing>) : ScanResult
    /** Session is missing or expired — the user must re-login in the WebView. */
    data object AuthRequired : ScanResult
    data class Error(val message: String) : ScanResult
}

/**
 * Fetches Marketplace search pages using the session cookie captured at login.
 * The cookie is sent only to facebook.com.
 */
class MarketplaceClient(private val session: SessionStore) {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun scan(search: SavedSearch): ScanResult = withContext(Dispatchers.IO) {
        val cookie = session.cookie()
        if (cookie.isNullOrBlank() || !cookie.contains("c_user")) {
            return@withContext ScanResult.AuthRequired
        }

        val request = Request.Builder()
            .url(buildUrl(search))
            .header("Cookie", cookie)
            .header("User-Agent", session.userAgent())
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
            .header("Accept-Language", "en-US,en;q=0.9")
            .header("sec-fetch-mode", "navigate")
            .build()

        try {
            http.newCall(request).execute().use { resp ->
                if (resp.code == 302 || resp.code == 401 || resp.code == 403) {
                    return@withContext ScanResult.AuthRequired
                }
                if (!resp.isSuccessful) {
                    return@withContext ScanResult.Error("HTTP ${resp.code}")
                }
                val body = resp.body?.string().orEmpty()
                // A logged-out response redirects to a login wall instead of results.
                if (body.contains("login_form") && !body.contains("marketplace_listing_title")) {
                    return@withContext ScanResult.AuthRequired
                }
                ScanResult.Success(MarketplaceParser.parse(body))
            }
        } catch (e: Exception) {
            ScanResult.Error(e.message ?: "Network error")
        }
    }

    private fun buildUrl(search: SavedSearch): String {
        val base = StringBuilder("https://www.facebook.com/marketplace/")
        if (search.locationId.isNotBlank()) base.append(search.locationId).append("/")
        if (search.category.isNotBlank()) base.append("category/${enc(search.category)}/")
        else base.append("search/")

        val params = buildList {
            if (search.query.isNotBlank()) add("query=${enc(search.query)}")
            search.minPrice?.let { add("minPrice=$it") }
            search.maxPrice?.let { add("maxPrice=$it") }
            add("radius_km=${search.radiusKm}")
            add("sortBy=creation_time_descend")
            add("exact=false")
        }
        return base.append("?").append(params.joinToString("&")).toString()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")
}
