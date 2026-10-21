package com.mediaplaypro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.mediaplaypro.app.data.Store

@Composable
fun SettingsScreen() {
    var cleared by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Text("Settings", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(16.dp))

        SectionTitle("Appearance")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("System", "Light", "Dark").forEachIndexed { i, n ->
                FilterChip(selected = Store.themeMode == i, onClick = { Store.setTheme(i) }, label = { Text(n) })
            }
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Downloads")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("Wi-Fi only")
                Text("Don't download over mobile data", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = Store.wifiOnly, onCheckedChange = { Store.updateWifiOnly(it) })
        }

        Spacer(Modifier.height(20.dp))
        SectionTitle("Privacy")
        Text("Your media stays on your device. No account, no analytics, no uploads.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = { Store.clearHistory(); cleared = true }) { Text("Clear play history") }
        if (cleared) Text("History cleared.", color = MaterialTheme.colorScheme.primary)

        Spacer(Modifier.height(20.dp))
        SectionTitle("About")
        Text("MediaPlay Pro 1.0.0\nMusic & video player · equalizer · sleep timer · audio trimmer · download manager",
            style = MaterialTheme.typography.bodySmall)
    }
}
