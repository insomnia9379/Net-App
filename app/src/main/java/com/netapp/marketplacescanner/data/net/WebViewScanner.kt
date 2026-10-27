package com.netapp.marketplacescanner.data.net

import android.annotation.SuppressLint
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import com.netapp.marketplacescanner.data.model.ScannedListing
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import org.json.JSONObject
import kotlin.coroutines.resume

/**
 * Loads a Marketplace search page in an off-screen WebView and reads the
 * listings out of the rendered page.
 *
 * Why not a plain HTTP GET? Facebook no longer puts the listings in the HTML it
 * serves to a bare request — the page is an empty React shell and the listings
 * arrive through JavaScript after load. A WebView runs that JavaScript, and it
 * already holds the cookies from the in-app login, so it sees exactly what the
 * user would see in the browser.
 *
 * Extraction is two-layered:
 *  1. DOM: every `a[href*="/marketplace/item/<id>"]` card, with its text spans
 *     (price / title / location) and image.
 *  2. Fallback: if the DOM has no cards, the full page HTML (which includes the
 *     preloaded GraphQL payloads in `<script>` tags) is handed to
 *     [MarketplaceParser], the original regex parser.
 */
class WebViewScanner(context: Context) {

    private val appContext = context.applicationContext
    private val main = Handler(Looper.getMainLooper())

    suspend fun scan(url: String, userAgent: String): ScanResult {
        val result = withTimeoutOrNull(TOTAL_TIMEOUT_MS) {
            withContext(Dispatchers.Main.immediate) { run(url, userAgent) }
        }
        return result ?: ScanResult.Error("Timed out loading Marketplace")
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun run(url: String, userAgent: String): ScanResult {
        val web = try {
            WebView(appContext)
        } catch (e: Throwable) {
            return ScanResult.Error("WebView unavailable: ${e.message}")
        }
        try {
            CookieManager.getInstance().apply {
                setAcceptCookie(true)
                setAcceptThirdPartyCookies(web, true)
            }
            web.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                databaseEnabled = true
                loadWithOverviewMode = true
                useWideViewPort = true
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                blockNetworkImage = true // we only need image URLs, not pixels
                userAgentString = userAgent
            }
            // Give the page a real viewport so Facebook lays out the result grid.
            web.measure(
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_W, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(VIEWPORT_H, View.MeasureSpec.EXACTLY),
            )
            web.layout(0, 0, VIEWPORT_W, VIEWPORT_H)
            web.visibility = View.VISIBLE
            web.onResume()
            web.webChromeClient = WebChromeClient()

            var mainFrameError: String? = null
            var pageFinished = false
            web.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    pageFinished = true
                }

                override fun shouldOverrideUrlLoading(
                    view: WebView?, request: WebResourceRequest?
                ): Boolean = false

                override fun onReceivedError(
                    view: WebView?, request: WebResourceRequest?, error: WebResourceError?,
                ) {
                    if (request?.isForMainFrame == true) {
                        mainFrameError = error?.description?.toString() ?: "load error"
                    }
                }
            }

            web.loadUrl(url)

            // Poll the DOM until the listing count stops growing (or we time out).
            val start = SystemClock.elapsedRealtime()
            var lastCount = -1
            var stableRounds = 0
            var scrolls = 0
            var snapshot: Snapshot? = null
            while (SystemClock.elapsedRealtime() - start < POLL_BUDGET_MS) {
                delayMain(POLL_INTERVAL_MS)
                mainFrameError?.let { return ScanResult.Error("Couldn't load Marketplace: $it") }

                val snap = evaluate(web, EXTRACT_JS)?.let(::parseSnapshot) ?: continue
                snapshot = snap
                if (snap.loginWall) return ScanResult.AuthRequired

                if (snap.items.size == lastCount) stableRounds++ else stableRounds = 0
                lastCount = snap.items.size

                if (pageFinished && stableRounds >= STABLE_ROUNDS_NEEDED) {
                    if (snap.items.isNotEmpty() && scrolls < MAX_SCROLLS) {
                        // Pull in the next page of results, then keep polling.
                        evaluate(web, SCROLL_JS)
                        scrolls++
                        stableRounds = 0
                        continue
                    }
                    break
                }
            }

            val items = snapshot?.items.orEmpty()
            if (items.isNotEmpty()) return ScanResult.Success(items)

            // Nothing in the DOM — try the preloaded JSON in the page source.
            val html = evaluate(web, HTML_JS)?.let(::unquoteJs).orEmpty()
            if (html.contains("login_form") || html.contains("/checkpoint/")) {
                return ScanResult.AuthRequired
            }
            return ScanResult.Success(MarketplaceParser.parse(html))
        } finally {
            try {
                web.stopLoading()
                web.webViewClient = WebViewClient()
                web.destroy()
            } catch (_: Throwable) {
            }
        }
    }

    private suspend fun delayMain(ms: Long) = suspendCancellableCoroutine<Unit> { cont ->
        val r = Runnable { if (cont.isActive) cont.resume(Unit) }
        main.postDelayed(r, ms)
        cont.invokeOnCancellation { main.removeCallbacks(r) }
    }

    /** Runs JS and returns its raw (JSON-encoded) result, or null on failure. */
    private suspend fun evaluate(web: WebView, js: String): String? =
        suspendCancellableCoroutine { cont ->
            try {
                web.evaluateJavascript(js) { value ->
                    if (cont.isActive) cont.resume(if (value == null || value == "null") null else value)
                }
            } catch (e: Throwable) {
                if (cont.isActive) cont.resume(null)
            }
        }

    private class Snapshot(val loginWall: Boolean, val items: List<ScannedListing>)

    /** evaluateJavascript returns a JS string literal; this turns it back into text. */
    private fun unquoteJs(value: String): String = try {
        org.json.JSONArray("[$value]").getString(0)
    } catch (_: Throwable) {
        value.trim('"')
    }

    private fun parseSnapshot(raw: String): Snapshot? = try {
        val obj = JSONObject(unquoteJs(raw))
        val loginWall = obj.optBoolean("login", false)
        val arr = obj.optJSONArray("items")
        val items = LinkedHashMap<String, ScannedListing>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val it = arr.getJSONObject(i)
                val id = it.optString("id")
                if (id.isBlank() || items.containsKey(id)) continue
                val texts = mutableListOf<String>()
                it.optJSONArray("texts")?.let { t ->
                    for (j in 0 until t.length()) {
                        val s = t.optString(j).trim()
                        if (s.isNotEmpty() && s !in texts) texts += s
                    }
                }
                val listing = buildListing(id, texts, it.optString("img"), it.optString("alt"))
                if (listing != null) items[id] = listing
            }
        }
        Snapshot(loginWall, items.values.toList())
    } catch (_: Throwable) {
        null
    }

    private fun buildListing(
        id: String, texts: List<String>, img: String, alt: String,
    ): ScannedListing? {
        val priceText = texts.firstOrNull { PRICE.matches(it) }.orEmpty()
        val location = texts.firstOrNull { LOCATION.matches(it) && !PRICE.matches(it) }.orEmpty()
        val title = texts.firstOrNull {
            it != priceText && it != location && !PRICE.matches(it) &&
                !MILEAGE.matches(it) && it.length > 1
        } ?: alt.takeIf { it.isNotBlank() && !it.startsWith("May be") }
        if (title.isNullOrBlank()) return null

        val price = when {
            priceText.equals("free", ignoreCase = true) -> 0
            else -> priceText.filter { it.isDigit() }.takeIf { it.isNotEmpty() }?.toIntOrNull()
        }
        val imageUrl = if (img.startsWith("http")) img else ""
        return ScannedListing(
            id = id,
            title = title,
            price = price,
            priceText = priceText,
            location = location,
            imageUrl = imageUrl,
            url = "https://www.facebook.com/marketplace/item/$id/",
        )
    }

    companion object {
        private const val VIEWPORT_W = 1080
        private const val VIEWPORT_H = 2400
        private const val TOTAL_TIMEOUT_MS = 45_000L
        private const val POLL_BUDGET_MS = 30_000L
        private const val POLL_INTERVAL_MS = 750L
        private const val STABLE_ROUNDS_NEEDED = 2
        private const val MAX_SCROLLS = 2

        // "$1,200", "$1,200.00", "Free", "CA$900"
        private val PRICE = Regex("^(?:[A-Z]{0,3}\\$\\s?[\\d,]+(?:\\.\\d+)?|Free|FREE)$")
        // "Sebring, FL" / "Lake Placid, FL"
        private val LOCATION = Regex("^[A-Za-z][A-Za-z .'\\-]{1,40}, [A-Z]{2}$")
        private val MILEAGE = Regex("^[\\d,.]+K? (?:miles|km)$", RegexOption.IGNORE_CASE)

        private val EXTRACT_JS = """
            (function () {
              var login = !!document.querySelector('form[action*="login"]') ||
                          location.pathname.indexOf('/login') === 0 ||
                          location.pathname.indexOf('/checkpoint') === 0;
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
                  id: m[1],
                  texts: texts,
                  img: img ? (img.currentSrc || img.src || '') : '',
                  alt: img ? (img.alt || '') : ''
                });
              }
              return JSON.stringify({ login: login, items: items });
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

        private val HTML_JS = "document.documentElement.outerHTML"
    }
}
