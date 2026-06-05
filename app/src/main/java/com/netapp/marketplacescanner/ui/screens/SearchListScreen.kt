package com.netapp.marketplacescanner.ui.screens

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netapp.marketplacescanner.data.db.SavedSearch
import com.netapp.marketplacescanner.ui.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchListScreen(
    vm: ScannerViewModel,
    onAdd: () -> Unit,
    onOpen: (Long) -> Unit,
    onEdit: (Long) -> Unit,
    onLogin: () -> Unit,
    onSettings: () -> Unit,
) {
    val searches by vm.searches.collectAsState()
    val ui by vm.ui.collectAsState()
    val loggedIn by vm.isLoggedIn.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Ask for notification permission once on Android 13+ so alerts can show.
    val notifPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* result handled by the system; nothing to do here */ }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Marketplace Scanner") },
                actions = {
                    IconButton(onClick = { vm.scanAllNow() }) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan all")
                    }
                    IconButton(onClick = onSettings) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAdd,
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("New search") },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (!loggedIn) {
                item {
                    LoginBanner(onLogin = onLogin)
                    Spacer(Modifier.height(4.dp))
                }
            }
            if (searches.isEmpty()) {
                item { EmptyState() }
            } else {
                items(searches, key = { it.id }) { search ->
                    SearchCard(
                        search = search,
                        onOpen = { onOpen(search.id) },
                        onEdit = { onEdit(search.id) },
                        onToggle = { vm.toggleEnabled(search) },
                    )
                }
            }
        }
    }
}

@Composable
private fun LoginBanner(onLogin: () -> Unit) {
    Card(onClick = onLogin, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text("Sign in to Facebook", fontWeight = FontWeight.Bold)
            Text(
                "Scanning needs your Facebook session. Tap to sign in — your login " +
                    "stays on this device.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("No searches yet", style = MaterialTheme.typography.titleMedium)
        Text(
            "Create a search to start watching Marketplace for matches.",
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SearchCard(
    search: SavedSearch,
    onOpen: () -> Unit,
    onEdit: () -> Unit,
    onToggle: () -> Unit,
) {
    Card(onClick = onOpen, modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    search.query.ifBlank { "All listings" },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    buildSummary(search),
                    style = MaterialTheme.typography.bodySmall,
                )
                Text(
                    if (search.lastScannedAt == 0L) "Not scanned yet"
                    else "${search.lastResultCount} listings • last scan ${relativeTime(search.lastScannedAt)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            Switch(checked = search.enabled, onCheckedChange = { onToggle() })
        }
    }
}

private fun buildSummary(s: SavedSearch): String {
    val parts = mutableListOf<String>()
    val price = when {
        s.minPrice != null && s.maxPrice != null -> "$${s.minPrice}–$${s.maxPrice}"
        s.maxPrice != null -> "≤ $${s.maxPrice}"
        s.minPrice != null -> "≥ $${s.minPrice}"
        else -> null
    }
    price?.let { parts += it }
    parts += "${s.radiusKm} km"
    if (s.locationLabel.isNotBlank()) parts += s.locationLabel
    if (s.alertUnderPrice != null) parts += "alert ≤ $${s.alertUnderPrice}"
    return parts.joinToString(" • ")
}
