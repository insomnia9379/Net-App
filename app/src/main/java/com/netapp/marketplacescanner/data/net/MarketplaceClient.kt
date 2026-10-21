package com.netapp.marketplacescanner.data.net

import android.content.Context
import android.webkit.CookieManager
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
 * Fetches Marketplace search results for a saved search.
 *
 * Primary path: an off-screen [WebViewScanner], which shares the cookie jar
 * with the login WebView and runs Facebook's JavaScript, so it sees the same
 * listings the user would in a browser.
 *
 * Fallback path: a plain HTTP GET with the captured cookie, parsed by
 * [MarketplaceParser]. Facebook rarely embeds listings in that response any
 * more, but it is cheap and occasionally still works, so it is tried only when
 * the WebView comes back empty.
 */
class MarketplaceClient(
    context: Context,
    private val session: SessionStore,
) {
    private val appContext = context.applicationContext
    private val webScanner = WebViewScanner(appContext)

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun scan(search: SavedSearch): ScanResult {
        val cookie = session.cookie()
        if (cookie.isNullOrBlank() || !cookie.contains("c_user")) {
            return ScanResult.AuthRequired
        }
        seedCookieJar(cookie)

        val url = buildUrl(search)
        val userAgent = session.userAgent()

        val primary = try {
            webScanner.scan(url, userAgent)
        } catch (e: Exception) {
            ScanResult.Error(e.message ?: "WebView scan failed")
        }
        if (primary is ScanResult.Success && primary.listings.isNotEmpty()) return primary
        if (primary is ScanResult.AuthRequired) return primary

        // Empty or errored — see if the raw page happens to carry the listings.
        val fallback = httpScan(url, cookie, userAgent)
        return when {
            fallback is ScanResult.Success && fallback.listings.isNotEmpty() -> fallback
            primary is ScanResult.Success -> primary // empty but legitimate
            else -> primary
        }
    }

    /**
     * The login screen stores the cookie string in DataStore; the WebView cookie
     * jar normally still has it too, but if the WebView data was cleared (or the
     * app was restored from backup) this puts the session back so the scan
     * WebView is logged in.
     */
    private fun seedCookieJar(cookie: String) {
        try {
            val jar = CookieManager.getInstance()
            val existing = jar.getCookie(FB_ORIGIN).orEmpty()
            if (existing.contains("c_user")) return
            cookie.split(';')
                .map { it.trim() }
                .filter { it.contains('=') }
                .forEach { pair ->
                    jar.setCookie(FB_ORIGIN, "$pair; Domain=.facebook.com; Path=/; Secure")
                }
            jar.flush()
        } catch (_: Throwable) {
        }
    }

    private suspend fun httpScan(url: String, cookie: String, userAgent: String): ScanResult =
        withContext(Dispatchers.IO) {
            val request = Request.Builder()
                .url(url)
                .header("Cookie", cookie)
                .header("User-Agent", userAgent)
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
            val sort = if (search.sortBy == SavedSearch.SORT_NEAREST) {
                "distance_ascend"
            } else {
                "creation_time_descend"
            }
            add("sortBy=$sort")
            add("exact=false")
        }
        return base.append("?").append(params.joinToString("&")).toString()
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private companion object {
        const val FB_ORIGIN = "https://www.facebook.com"
    }
}
