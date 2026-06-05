package com.netapp.marketplacescanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netapp.marketplacescanner.data.db.Listing
import com.netapp.marketplacescanner.ui.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    vm: ScannerViewModel,
    searchId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onLogin: () -> Unit,
) {
    val context = LocalContext.current
    val search by vm.observeSearch(searchId).collectAsState(initial = null)
    val listings by vm.listings(searchId).collectAsState(initial = emptyList())
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    // Clear the unread badge for this search once the user is looking at it.
    DisposableEffect(searchId) {
        onDispose { vm.markRead(searchId) }
    }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
    }
    LaunchedEffect(ui.needsLogin) {
        if (ui.needsLogin) onLogin()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(search?.query?.ifBlank { "All listings" } ?: "Results") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    IconButton(onClick = { search?.let { vm.scanNow(it) } }) {
                        if (ui.scanning) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                            )
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = "Scan now")
                        }
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        if (listings.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "No listings yet. Tap the refresh icon to scan now.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(listings, key = { it.rowId }) { listing ->
                    ListingCard(listing) {
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(listing.url))
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingCard(listing: Listing, onClick: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (listing.isNew) {
                        Badge { Text("NEW") }
                    }
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(start = if (listing.isNew) 8.dp else 0.dp),
                    )
                }
                val priceLine = buildString {
                    append(listing.priceText.ifBlank { "—" })
                    if (listing.lowestPrice != null && listing.price != null &&
                        listing.lowestPrice < listing.price
                    ) {
                        append("  (low $${listing.lowestPrice})")
                    }
                }
                Text(priceLine, style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary)
                if (listing.location.isNotBlank()) {
                    Text(listing.location, style = MaterialTheme.typography.bodySmall)
                }
                Text(
                    "seen ${relativeTime(listing.firstSeenAt)}",
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        }
    }
}
