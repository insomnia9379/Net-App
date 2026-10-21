package com.netapp.marketplacescanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.netapp.marketplacescanner.data.db.SavedSearch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSearchScreen(
    vm: com.netapp.marketplacescanner.ui.ScannerViewModel,
    searchId: Long,
    onDone: () -> Unit,
) {
    val isNew = searchId < 0
    var loaded by remember { mutableStateOf(isNew) }

    var query by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("") }
    var locationLabel by remember { mutableStateOf("") }
    var locationId by remember { mutableStateOf("") }
    var radius by remember { mutableStateOf("40") }
    var minPrice by remember { mutableStateOf("") }
    var maxPrice by remember { mutableStateOf("") }
    var keywords by remember { mutableStateOf("") }
    var excludeKeywords by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf(SavedSearch.SORT_NEWEST) }
    var alertNew by remember { mutableStateOf(true) }
    var alertDrop by remember { mutableStateOf(true) }
    var alertUnder by remember { mutableStateOf("") }

    var existing by remember { mutableStateOf<SavedSearch?>(null) }

    LaunchedEffect(searchId) {
        if (!isNew) {
            vm.getSearchOnce(searchId)?.let { s ->
                existing = s
                query = s.query
                category = s.category
                locationLabel = s.locationLabel
                locationId = s.locationId
                radius = s.radiusKm.toString()
                minPrice = s.minPrice?.toString() ?: ""
                maxPrice = s.maxPrice?.toString() ?: ""
                keywords = s.mustIncludeKeywords
                excludeKeywords = s.excludeKeywords
                sortBy = s.sortBy
                alertNew = s.alertOnNew
                alertDrop = s.alertOnPriceDrop
                alertUnder = s.alertUnderPrice?.toString() ?: ""
            }
            loaded = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "New search" else "Edit search") },
                navigationIcon = {
                    IconButton(onClick = onDone) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                actions = {
                    if (!isNew && existing != null) {
                        IconButton(onClick = {
                            existing?.let { vm.deleteSearch(it) }
                            onDone()
                        }) {
                            Icon(Icons.Default.Delete, contentDescription = "Delete")
                        }
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = query, onValueChange = { query = it },
                label = { Text("Search terms") },
                placeholder = { Text("e.g. ikea standing desk") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = category, onValueChange = { category = it },
                label = { Text("Category slug (optional)") },
                placeholder = { Text("e.g. furniture") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = locationLabel, onValueChange = { locationLabel = it },
                label = { Text("Location label (optional)") },
                placeholder = { Text("e.g. Seattle") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = locationId, onValueChange = { locationId = it },
                label = { Text("Marketplace location id (optional)") },
                placeholder = { Text("e.g. seattle") },
                modifier = Modifier.fillMaxWidth(),
            )

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                NumberField("Min $", minPrice, Modifier.weight(1f)) { minPrice = it }
                NumberField("Max $", maxPrice, Modifier.weight(1f)) { maxPrice = it }
            }
            NumberField("Radius (km)", radius, Modifier.fillMaxWidth()) { radius = it }

            OutlinedTextField(
                value = keywords, onValueChange = { keywords = it },
                label = { Text("Must include keywords (comma separated)") },
                placeholder = { Text("e.g. leather, genuine") },
                modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                value = excludeKeywords, onValueChange = { excludeKeywords = it },
                label = { Text("Exclude keywords (comma separated)") },
                placeholder = { Text("e.g. broken, parts, replica") },
                supportingText = { Text("Hide results containing any of these words") },
                modifier = Modifier.fillMaxWidth(),
            )

            Text("Sort results by", style = MaterialTheme.typography.labelLarge)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(
                    selected = sortBy == SavedSearch.SORT_NEWEST,
                    onClick = { sortBy = SavedSearch.SORT_NEWEST },
                    label = { Text("Newest") },
                )
                FilterChip(
                    selected = sortBy == SavedSearch.SORT_NEAREST,
                    onClick = { sortBy = SavedSearch.SORT_NEAREST },
                    label = { Text("Closest") },
                )
            }

            HorizontalDivider()
            Text("Alerts", style = MaterialTheme.typography.titleMedium)

            ToggleRow("Notify on new listings", alertNew) { alertNew = it }
            ToggleRow("Notify on price drops", alertDrop) { alertDrop = it }
            NumberField(
                "Only alert at or below $ (optional)", alertUnder, Modifier.fillMaxWidth()
            ) { alertUnder = it }

            Button(
                onClick = {
                    val s = (existing ?: SavedSearch(query = "")).copy(
                        query = query.trim(),
                        category = category.trim(),
                        locationLabel = locationLabel.trim(),
                        locationId = locationId.trim(),
                        radiusKm = radius.toIntOrNull() ?: 40,
                        minPrice = minPrice.toIntOrNull(),
                        maxPrice = maxPrice.toIntOrNull(),
                        mustIncludeKeywords = keywords.trim(),
                        excludeKeywords = excludeKeywords.trim(),
                        sortBy = sortBy,
                        alertOnNew = alertNew,
                        alertOnPriceDrop = alertDrop,
                        alertUnderPrice = alertUnder.toIntOrNull(),
                    )
                    vm.saveSearch(s) { onDone() }
                },
                enabled = loaded,
                modifier = Modifier.fillMaxWidth(),
            ) { Text(if (isNew) "Create search" else "Save changes") }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { new -> onChange(new.filter { it.isDigit() }) },
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = modifier,
    )
}

@Composable
private fun ToggleRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
