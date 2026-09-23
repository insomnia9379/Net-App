package com.netapp.marketplacescanner.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.netapp.marketplacescanner.BuildConfig
import com.netapp.marketplacescanner.data.net.SessionStore
import com.netapp.marketplacescanner.ui.ScannerViewModel

/**
 * Embeds Facebook's own login page in a WebView. We never see the password —
 * once Facebook sets the authenticated session cookie (contains `c_user`), we
 * read it from the system CookieManager, hand it to the SessionStore, and the
 * background scanner reuses it. The cookie never leaves the device except in
 * requests back to facebook.com.
 *
 * Facebook's login is heavily JavaScript-driven, so the WebView needs a
 * WebChromeClient and a full set of viewport/storage settings or it renders a
 * blank page. Any load error is surfaced on screen (with a retry) rather than
 * left as a silent white screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun LoginScreen(
    vm: ScannerViewModel,
    onDone: () -> Unit,
) {
    var progress by remember { mutableStateOf(0) }
    var captured by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    val loginUrl = "https://www.facebook.com/login.php"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Facebook") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
                actions = {
                    IconButton(onClick = { error = null; webView?.reload() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val cookies = CookieManager.getInstance()
                    cookies.setAcceptCookie(true)
                    if (BuildConfig.DEBUG) WebView.setWebContentsDebuggingEnabled(true)

                    WebView(ctx).apply {
                        webView = this
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT,
                        )
                        cookies.setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            databaseEnabled = true
                            loadWithOverviewMode = true
                            useWideViewPort = true
                            javaScriptCanOpenWindowsAutomatically = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                            userAgentString = SessionStore.DEFAULT_USER_AGENT
                        }

                        // A WebChromeClient is required for many JS-driven pages to
                        // render at all; it also gives us real load progress.
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                progress = newProgress
                            }
                        }

                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(v: WebView?, url: String?, f: Bitmap?) {
                                progress = 0
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                progress = 100
                                if (captured) return
                                val cookie = cookies.getCookie(url) ?: cookies.getCookie(
                                    "https://www.facebook.com"
                                )
                                if (cookie != null && cookie.contains("c_user")) {
                                    captured = true
                                    cookies.flush()
                                    vm.onSessionCaptured(cookie, settings.userAgentString)
                                    onDone()
                                }
                            }

                            // Keep every navigation inside this WebView.
                            override fun shouldOverrideUrlLoading(
                                view: WebView?, request: WebResourceRequest?
                            ): Boolean = false

                            override fun onReceivedError(
                                view: WebView?,
                                request: WebResourceRequest?,
                                err: WebResourceError?,
                            ) {
                                // Only surface failures of the main page, not of
                                // sub-resources like tracking pixels.
                                if (request?.isForMainFrame == true) {
                                    error = "Couldn't load the login page" +
                                        (err?.description?.let { ": $it" } ?: ".")
                                }
                            }
                        }
                        loadUrl(loginUrl)
                    }
                },
            )

            if (progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth(),
                )
            }

            error?.let { message ->
                Column(
                    modifier = Modifier.align(Alignment.Center).fillMaxWidth().padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Text(
                        message,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        "Check your connection and try again.",
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                    )
                    Button(onClick = { error = null; webView?.reload() }) { Text("Retry") }
                }
            }
        }
    }
}
