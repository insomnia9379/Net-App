package com.netapp.marketplacescanner.data.net

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import com.netapp.marketplacescanner.data.model.ScannedListing
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Drives a *visible* WebView to scan Marketplace. Unlike a hidden background
 * WebView, an on-screen one with a desktop user-agent actually renders
 * Facebook's results grid, so this is what reliably returns listings.
 *
 * The caller owns the WebView (it lives in the Scan screen); this class just
 * configures it, loads the URL, scrolls to pull in more cards, and reads the
 * listings out of the DOM. All methods must be called on the main thread.
 */
class ForegroundScanner(private val web: WebView) {

    @SuppressLint("SetJavaScriptEnabled")
    fun configure() {
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            databaseEnabled = true
            useWideViewPort = true
            loadWithOverviewMode = true
            userAgentString = MarketplaceUrl.DESKTOP_UA
        }
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(web, true)
        }
    }

    private var pageFinished = false

    /**
     * Loads [url], scrolls [scrollPasses] times to lazy-load more results, and
     * extracts the listings. Reports human-readable progress through [onLog].
     */
    suspend fun scan(
        url: String,
        scrollPasses: Int = 6,
        onLog: (String) -> Unit,
    ): ScanResult {
        pageFinished = false
        web.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, u: String?) { pageFinished = true }
            override fun shouldOverrideUrlLoading(v: WebView?, r: android.webkit.WebResourceRequest?) = false
        }
        onLog("Loading Marketplace…")
        web.loadUrl(url)

        var waited = 0
        while (!pageFinished && waited < 30_000) { delay(500); waited += 500 }
        delay(4_000) // let the grid render

        if (needsLogin()) {
            onLog("Not logged in — sign into Facebook above, then tap Scan again.")
            return ScanResult.AuthRequired
        }

        repeat(scrollPasses) { i ->
            onLog("Scrolling for more… (${i + 1}/$scrollPasses)")
            evalJs(SCROLL_JS)
            delay(2_200)
        }

        onLog("Reading listings…")
        val raw = evalJs(EXTRACT_JS)
        val items = parse(raw)
        onLog("Found ${items.size} listings.")
        return ScanResult.Success(items)
    }

    private suspend fun needsLogin(): Boolean = evalJs(
        "(function(){return (document.querySelector('form[action*=\"login\"]')||" +
            "document.querySelector('input[name=\"email\"]')||" +
            "location.href.indexOf('login')>-1||location.href.indexOf('checkpoint')>-1)" +
            "?'YES':'NO';})()"
    ).contains("YES")

    private suspend fun evalJs(js: String): String = suspendCancellableCoroutine { cont ->
        try {
            web.evaluateJavascript(js) { v -> if (cont.isActive) cont.resume(v ?: "null") }
        } catch (e: Throwable) {
            if (cont.isActive) cont.resume("null")
        }
    }

    private fun unquote(value: String): String = try {
        org.json.JSONArray("[$value]").getString(0)
    } catch (_: Throwable) {
        value.trim('"')
    }

    private fun parse(raw: String): List<ScannedListing> = try {
        val obj = JSONObject(unquote(raw))
        val arr = obj.optJSONArray("items")
        val out = LinkedHashMap<String, ScannedListing>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val id = it.optString("id")
                if (id.isBlank() || out.containsKey(id)) continue
                val texts = mutableListOf<String>()
                it.optJSONArray("texts")?.let { t ->
                    for (j in 0 until t.length()) {
                        val s = t.optString(j).trim()
                        if (s.isNotEmpty() && s !in texts) texts += s
                    }
                }
                buildListing(id, texts, it.optString("img"), it.optString("alt"))?.let { l -> out[id] = l }
            }
        }
        out.values.toList()
    } catch (_: Throwable) {
        emptyList()
    }

    private fun buildListing(id: String, texts: List<String>, img: String, alt: String): ScannedListing? {
        val priceText = texts.firstOrNull { PRICE.matches(it) }.orEmpty()
        val location = texts.firstOrNull { LOCATION.matches(it) && !PRICE.matches(it) }.orEmpty()
        val title = texts.firstOrNull {
            it != priceText && it != location && !PRICE.matches(it) && !MILEAGE.matches(it) && it.length > 1
        } ?: alt.takeIf { it.isNotBlank() && !it.startsWith("May be") }
        if (title.isNullOrBlank()) return null
        val price = when {
            priceText.equals("free", ignoreCase = true) -> 0
            else -> priceText.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        return ScannedListing(
            id = id,
            title = title,
            price = price,
            priceText = priceText,
            location = location,
            imageUrl = if (img.startsWith("http")) img else "",
            url = "https://www.facebook.com/marketplace/item/$id/",
        )
    }

    companion object {
        private val PRICE = Regex("^(?:[A-Z]{0,3}\\$\\s?[\\d,]+(?:\\.\\d+)?|Free|FREE)$")
        private val LOCATION = Regex("^[A-Za-z][A-Za-z .'\\-]{1,40}, [A-Z]{2}$")
        private val MILEAGE = Regex("^[\\d,.]+K? (?:miles|km)$", RegexOption.IGNORE_CASE)

        private val EXTRACT_JS = """
            (function () {
              var seen = {}, items = [];
              var anchors = document.querySelectorAll('a[href*="/marketplace/item/"]');
              for (var i = 0; i < anchors.length; i++) {
                var a = anchors[i];
                var m = (a.getAttribute('href') || '').match(/\/marketplace\/item\/(\d+)/);
                if (!m || seen[m[1]]) continue;
                seen[m[1]] = true;
                var texts = [];
                var spans = a.querySelectorAll('span, div[dir="auto"]');
                for (var j = 0; j < spans.length; j++) {
                  var el = spans[j];
                  if (el.children.length && el.querySelector('span, div[dir="auto"]')) continue;
                  var t = (el.innerText || el.textContent || '').trim();
                  if (t && texts.indexOf(t) < 0) texts.push(t);
                }
                var img = a.querySelector('img');
                items.push({
                  id: m[1], texts: texts,
                  img: img ? (img.currentSrc || img.src || '') : '',
                  alt: img ? (img.alt || '') : ''
                });
              }
              return JSON.stringify({ items: items });
            })();
        """.trimIndent()

        private val SCROLL_JS = """
            (function () {
              window.scrollTo(0, document.body.scrollHeight);
              var d = document.scrollingElement || document.documentElement;
              d.scrollTop = d.scrollHeight;
              return 1;
            })();
        """.trimIndent()
    }
}
