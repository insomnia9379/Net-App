package com.netapp.marketplacescanner.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Badge
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.netapp.marketplacescanner.data.db.Listing
import com.netapp.marketplacescanner.data.db.SavedSearch
import com.netapp.marketplacescanner.data.net.DealScorer
import com.netapp.marketplacescanner.ui.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ResultsScreen(
    vm: ScannerViewModel,
    searchId: Long,
    onBack: () -> Unit,
    onEdit: () -> Unit,
    onScan: () -> Unit,
) {
    val context = LocalContext.current
    val search by vm.observeSearch(searchId).collectAsState(initial = null)
    val nearest = search?.sortBy == SavedSearch.SORT_NEAREST
    val listingsFlow = remember(searchId, nearest) { vm.listings(searchId, nearest) }
    val allListings by listingsFlow.collectAsState(initial = emptyList())
    val ui by vm.ui.collectAsState()
    val snackbar = remember { SnackbarHostState() }

    var dealsOnly by remember { mutableStateOf(false) }
    val listings = if (dealsOnly) allListings.filter { DealScorer.isDeal(it.score) } else allListings
    val dealCount = allListings.count { DealScorer.isDeal(it.score) }

    DisposableEffect(searchId) { onDispose { vm.markRead(searchId) } }

    LaunchedEffect(ui.message) {
        ui.message?.let {
            snackbar.showSnackbar(it)
            vm.clearMessage()
        }
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
                    IconButton(onClick = onScan) {
                        Icon(Icons.Default.Refresh, contentDescription = "Scan now")
                    }
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, contentDescription = "Edit")
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = !dealsOnly,
                    onClick = { dealsOnly = false },
                    label = { Text("All (${allListings.size})") },
                )
                FilterChip(
                    selected = dealsOnly,
                    onClick = { dealsOnly = true },
                    label = { Text("🔥 Deals ($dealCount)") },
                )
            }

            if (listings.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        if (allListings.isEmpty()) "No listings yet. Tap the scan icon to scan now."
                        else "No deals in this list yet.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(listings, key = { it.rowId }) { listing ->
                        ListingCard(
                            listing = listing,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url)))
                                }
                            },
                            onToggleSave = { vm.setSaved(listing, !listing.saved) },
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ListingCard(listing: Listing, onClick: () -> Unit, onToggleSave: () -> Unit) {
    Card(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Thumbnail(listing.imageUrl, listing.title)
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (listing.isNew) Badge { Text("NEW") }
                    Text(
                        listing.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
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
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        priceLine,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    if (listing.score != DealScorer.UNKNOWN) {
                        Text(
                            "   ${DealScorer.badge(listing.score)}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
                if (listing.location.isNotBlank()) {
                    Text(listing.location, style = MaterialTheme.typography.bodySmall)
                }
                Text("seen ${relativeTime(listing.firstSeenAt)}", style = MaterialTheme.typography.labelSmall)
            }
            IconButton(onClick = onToggleSave) {
                if (listing.saved) {
                    Icon(Icons.Filled.Star, contentDescription = "Saved", tint = MaterialTheme.colorScheme.primary)
                } else {
                    Icon(Icons.Outlined.StarBorder, contentDescription = "Save")
                }
            }
        }
    }
}

/** 72dp rounded thumbnail; shows a neutral placeholder while loading or if blank. */
@Composable
private fun Thumbnail(url: String, contentDescription: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier.size(72.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Icon(Icons.Default.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(
                model = url,
                contentDescription = contentDescription,
                contentScale = ContentScale.Crop,
                modifier = Modifier.size(72.dp),
            )
        }
    }
}
