package com.netapp.marketplacescanner.ui.screens

import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import com.netapp.marketplacescanner.ui.ScannerViewModel

/**
 * Embeds Facebook's own login page in a WebView. We never see the password —
 * once Facebook sets the authenticated session cookie (contains `c_user`), we
 * read it from the system CookieManager, hand it to the SessionStore, and the
 * background scanner reuses it. The cookie never leaves the device except in
 * requests back to facebook.com.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    vm: ScannerViewModel,
    onDone: () -> Unit,
) {
    var loading by remember { mutableStateOf(true) }
    var captured by remember { mutableStateOf(false) }
    val loginUrl = "https://m.facebook.com/login.php"

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sign in to Facebook") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Close")
                    }
                },
            )
        },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    CookieManager.getInstance().apply {
                        setAcceptCookie(true)
                    }
                    WebView(ctx).apply {
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                        settings.apply {
                            javaScriptEnabled = true
                            domStorageEnabled = true
                            cacheMode = WebSettings.LOAD_DEFAULT
                            userAgentString = com.netapp.marketplacescanner.data.net
                                .SessionStore.DEFAULT_USER_AGENT
                        }
                        webViewClient = object : WebViewClient() {
                            override fun onPageStarted(
                                view: WebView?, url: String?, favicon: android.graphics.Bitmap?
                            ) {
                                loading = true
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                loading = false
                                if (captured) return
                                val cookie = CookieManager.getInstance().getCookie(url)
                                if (cookie != null && cookie.contains("c_user")) {
                                    captured = true
                                    vm.onSessionCaptured(cookie, settings.userAgentString)
                                    onDone()
                                }
                            }
                        }
                        loadUrl(loginUrl)
                    }
                },
            )
            if (loading) {
                LinearProgressIndicator(
                    Modifier.align(Alignment.TopCenter).fillMaxWidth()
                )
            }
        }
    }
}
