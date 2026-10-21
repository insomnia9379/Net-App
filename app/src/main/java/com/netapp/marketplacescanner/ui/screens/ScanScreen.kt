package com.netapp.marketplacescanner.ui.screens

import android.view.ViewGroup
import android.webkit.WebView
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.netapp.marketplacescanner.data.net.ForegroundScanner
import com.netapp.marketplacescanner.data.net.ScanResult
import com.netapp.marketplacescanner.ui.ScannerViewModel

/**
 * Runs a scan in a WebView the user can see. Facebook only renders its results
 * grid into an on-screen, desktop-UA WebView, so scanning is a foreground action
 * here: open the screen, watch it load and scroll, results get saved.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    vm: ScannerViewModel,
    searchId: Long,
    onBack: () -> Unit,
    onViewResults: () -> Unit,
) {
    val search by vm.observeSearch(searchId).collectAsState(initial = null)

    var webView by remember { mutableStateOf<WebView?>(null) }
    var scanner by remember { mutableStateOf<ForegroundScanner?>(null) }
    val log = remember { mutableStateListOf<String>() }
    var running by remember { mutableStateOf(false) }
    var summary by remember { mutableStateOf<String?>(null) }
    var needLogin by remember { mutableStateOf(false) }
    var runCount by remember { mutableIntStateOf(0) }

    // Runs on first ready, and again whenever the user taps Scan (runCount++).
    LaunchedEffect(webView, search, runCount) {
        val s = search ?: return@LaunchedEffect
        val w = webView ?: return@LaunchedEffect
        val sc = scanner ?: ForegroundScanner(w).also { it.configure(); scanner = it }
        running = true
        needLogin = false
        summary = null
        log.clear()
        when (val r = sc.scan(vm.scanUrl(s), onLog = { line ->
            log.add(line); if (log.size > 8) log.removeAt(0)
        })) {
            is ScanResult.Success -> {
                summary = vm.persistScan(s, r.listings)
                running = false
            }
            is ScanResult.AuthRequired -> {
                needLogin = true
                running = false
            }
            is ScanResult.Error -> {
                summary = "Error: ${r.message}"
                running = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(search?.query?.ifBlank { "Scan" } ?: "Scan") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            // The live WebView — visible so Facebook renders results (and so the
            // user can log in here if needed).
            Box(Modifier.weight(1f).fillMaxWidth()) {
                AndroidView(
                    modifier = Modifier.fillMaxSize(),
                    factory = { ctx ->
                        WebView(ctx).apply {
                            layoutParams = ViewGroup.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.MATCH_PARENT,
                            )
                            webView = this
                            scanner = ForegroundScanner(this).also { it.configure() }
                        }
                    },
                    onRelease = { it.destroy() },
                )
            }

            Surface(tonalElevation = 2.dp) {
                Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (running) {
                            CircularProgressIndicator(Modifier.heightIn(max = 20.dp).padding(end = 8.dp), strokeWidth = 2.dp)
                        }
                        Text(
                            summary ?: log.lastOrNull() ?: "Starting…",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }

                    if (log.size > 1 && summary == null) {
                        Column(Modifier.fillMaxWidth().heightIn(max = 90.dp).verticalScroll(rememberScrollState())) {
                            log.forEach { Text("· $it", style = MaterialTheme.typography.labelSmall) }
                        }
                    }

                    if (needLogin) {
                        Text(
                            "Sign in to Facebook above, then tap Scan.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Button(onClick = { if (!running) runCount++ }, enabled = !running) {
                            Text(if (summary != null || needLogin) "Scan again" else "Scan")
                        }
                        if (summary != null) {
                            OutlinedButton(onClick = onViewResults) { Text("View results") }
                        }
                    }
                }
            }
        }
    }
}
