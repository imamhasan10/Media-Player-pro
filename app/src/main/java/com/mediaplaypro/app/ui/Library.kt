package com.mediaplaypro.app.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController

/** "Play" page: everything stored on the device — music, videos and downloads. */
@Composable
fun LibraryScreen(nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Library", style = MaterialTheme.typography.headlineSmall, modifier = Modifier.weight(1f))
            IconButton(onClick = { nav.navigate("search") }) { Icon(Icons.Filled.Search, "Search") }
            IconButton(onClick = { nav.navigate("equalizer") }) { Icon(Icons.Filled.Equalizer, "Equalizer") }
        }
        TabRow(selectedTabIndex = tab) {
            listOf("Music", "Video", "Downloads").forEachIndexed { i, n ->
                Tab(selected = tab == i, onClick = { tab = i }, text = { Text(n) })
            }
        }
        when (tab) {
            0 -> MusicScreen(nav)
            1 -> VideoScreen()
            else -> DownloadsScreen()
        }
    }
}
