package com.netapp.marketplacescanner.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.netapp.marketplacescanner.ui.ScannerViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    vm: ScannerViewModel,
    onBack: () -> Unit,
    onLogin: () -> Unit,
) {
    val loggedIn by vm.isLoggedIn.collectAsState()
    val interval by vm.intervalMinutes.collectAsState()
    val wifiOnly by vm.wifiOnly.collectAsState()
    val capturedAt by vm.session.capturedAt.collectAsState(initial = 0L)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("Account", style = MaterialTheme.typography.titleMedium)
            if (loggedIn) {
                Text(
                    "Signed in to Facebook • session captured ${relativeTime(capturedAt)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                OutlinedButton(onClick = { vm.logout() }) { Text("Sign out") }
            } else {
                Text(
                    "Not signed in. Scanning is paused until you sign in.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Button(onClick = onLogin) { Text("Sign in to Facebook") }
            }

            Divider()
            Text("Scan frequency", style = MaterialTheme.typography.titleMedium)
            val options = listOf(15L, 30L, 60L, 120L, 360L)
            options.forEach { minutes ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(
                            selected = interval == minutes,
                            onClick = { vm.setInterval(minutes) },
                        )
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RadioButton(
                        selected = interval == minutes,
                        onClick = { vm.setInterval(minutes) },
                    )
                    Text(intervalLabel(minutes), modifier = Modifier.padding(start = 8.dp))
                }
            }

            Divider()
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Scan on Wi-Fi only", fontWeight = FontWeight.Medium)
                    Text(
                        "Skip background scans on mobile data.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(checked = wifiOnly, onCheckedChange = { vm.setWifiOnly(it) })
            }

            Divider()
            Text(
                "Listings are fetched from your own Facebook session and stored only on " +
                    "this device. This is a personal tool — respect Facebook's Terms of " +
                    "Service and applicable laws when using it.",
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun intervalLabel(minutes: Long): String = when {
    minutes < 60 -> "Every $minutes minutes"
    minutes == 60L -> "Every hour"
    else -> "Every ${minutes / 60} hours"
}
