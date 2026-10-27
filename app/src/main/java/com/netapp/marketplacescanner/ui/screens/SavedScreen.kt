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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Image
import coil.compose.AsyncImage
import com.netapp.marketplacescanner.data.net.DealScorer
import com.netapp.marketplacescanner.ui.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SavedScreen(
    vm: ScannerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val saved by vm.savedItems().collectAsState(initial = emptyList())

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Saved") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        if (saved.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) {
                Text(
                    "No saved items yet. Tap the ☆ on any listing to save it here.",
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                items(saved, key = { it.rowId }) { listing ->
                    Card(
                        onClick = {
                            runCatching {
                                context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(listing.url)))
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            SavedThumb(listing.imageUrl, listing.title)
                            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                                Text(
                                    listing.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                val line = buildString {
                                    append(listing.priceText.ifBlank { "—" })
                                    if (DealScorer.isDeal(listing.score)) append("   ${DealScorer.badge(listing.score)}")
                                }
                                Text(line, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                                val src = if (listing.savedSource == "FB") "from Facebook" else "saved here"
                                Text(
                                    listOf(listing.location, src).filter { it.isNotBlank() }.joinToString(" • "),
                                    style = MaterialTheme.typography.labelSmall,
                                )
                            }
                            IconButton(onClick = { vm.setSaved(listing, false) }) {
                                Icon(Icons.Filled.Star, contentDescription = "Unsave", tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SavedThumb(url: String, desc: String) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier = Modifier.size(64.dp).clip(shape).background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center,
    ) {
        if (url.isBlank()) {
            Icon(Icons.Filled.Image, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        } else {
            AsyncImage(model = url, contentDescription = desc, contentScale = ContentScale.Crop, modifier = Modifier.size(64.dp))
        }
    }
}
