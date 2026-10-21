package com.mediaplaypro.app.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.data.Media
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.playback.PlayerHub

// ---------------------------------------------------------------- HOME
@Composable
fun HomeScreen(nav: NavHostController) {
    val audio = Library.audio
    val recentAudio = Store.recent.mapNotNull { id -> audio.find { it.id == id } }.take(5)

    LazyColumn(contentPadding = PaddingValues(bottom = 16.dp)) {
        item {
            Text("All Media", style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(16.dp))
        }
        item {
            OutlinedCard(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp).clickable { nav.navigate("search") }
            ) {
                Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Search, null)
                    Spacer(Modifier.width(10.dp))
                    Text("Search music, video, artist, album…",
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        item {
            Row(
                Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                Tile("Music", Icons.Filled.MusicNote) { nav.navigate("music") }
                Tile("Video", Icons.Filled.VideoLibrary) { nav.navigate("video") }
                Tile("Downloads", Icons.Filled.Download) { nav.navigate("downloads") }
                Tile("Equalizer", Icons.Filled.Equalizer) { nav.navigate("equalizer") }
            }
        }
        item {
            Text(
                "${audio.size} songs · ${Library.video.size} videos",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )
        }
        if (recentAudio.isNotEmpty()) {
            item { Header("Recently played") }
            items(recentAudio, key = { "r${it.id}" }) { m ->
                SongRow(m, nav, onPlay = { PlayerHub.play(recentAudio, recentAudio.indexOf(m)) })
            }
        }
        item { Header("Recently added music") }
        val newest = audio.take(5)
        if (newest.isEmpty()) item { EmptyState(if (Library.loading) "Scanning…" else "No music found on this device.") }
        items(newest, key = { "a${it.id}" }) { m ->
            SongRow(m, nav, onPlay = { PlayerHub.play(audio, audio.indexOf(m)) })
        }
        item { Header("Recently added videos") }
        val newVideo = Library.video.take(5)
        if (newVideo.isEmpty()) item { EmptyState("No videos found on this device.") }
        items(newVideo, key = { "v${it.id}" }) { VideoRow(it) }
    }
}

@Composable
private fun Header(text: String) {
    Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) { SectionTitle(text) }
}

@Composable
private fun Tile(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { onClick() }) {
        FilledTonalIconButton(onClick = onClick, modifier = Modifier.size(56.dp)) { Icon(icon, label) }
        Spacer(Modifier.height(4.dp))
        Text(label, style = MaterialTheme.typography.labelMedium)
    }
}

// ---------------------------------------------------------------- MUSIC
@Composable
fun MusicScreen(nav: NavHostController) {
    var tab by remember { mutableIntStateOf(0) }
    val tabs = listOf("Songs", "Favorites", "Recent", "Most played", "Playlists")
    Column(Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tab, edgePadding = 8.dp) {
            tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
        }
        when (tab) {
            0 -> SongsTab(nav)
            1 -> TrackList(nav, Library.audio.filter { it.id in Store.favorites }, "No favorites yet — tap the heart on a song.")
            2 -> TrackList(nav, Store.recent.mapNotNull { id -> Library.audio.find { it.id == id } }, "Nothing played yet.")
            3 -> TrackList(
                nav,
                Library.audio.filter { (Store.playCounts[it.id] ?: 0) > 0 }
                    .sortedByDescending { Store.playCounts[it.id] ?: 0 },
                "Nothing played yet."
            )
            else -> PlaylistsTab(nav)
        }
    }
}

@Composable
private fun SongsTab(nav: NavHostController) {
    var sort by remember { mutableIntStateOf(0) }
    var sortMenu by remember { mutableStateOf(false) }
    val names = listOf("Date added", "Title", "Artist", "Duration", "Size")
    val sorted = remember(Library.audio, sort) {
        when (sort) {
            1 -> Library.audio.sortedBy { it.title.lowercase() }
            2 -> Library.audio.sortedBy { it.artist.lowercase() }
            3 -> Library.audio.sortedByDescending { it.durationMs }
            4 -> Library.audio.sortedByDescending { it.size }
            else -> Library.audio
        }
    }
    Column {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            FilledTonalButton(onClick = { PlayerHub.playShuffled(sorted) }, enabled = sorted.isNotEmpty()) {
                Icon(Icons.Filled.Shuffle, null); Spacer(Modifier.width(6.dp)); Text("Shuffle all")
            }
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { sortMenu = true }) { Text("Sort: ${names[sort]}") }
                DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                    names.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { sort = i; sortMenu = false }) }
                }
            }
        }
        TrackList(nav, sorted, if (Library.loading) "Scanning…" else "No music found.")
    }
}

@Composable
private fun TrackList(nav: NavHostController, list: List<Media>, empty: String) {
    if (list.isEmpty()) { EmptyState(empty); return }
    LazyColumn {
        items(list, key = { it.id }) { m ->
            SongRow(m, nav, onPlay = { PlayerHub.play(list, list.indexOf(m)) })
        }
    }
}

@Composable
private fun PlaylistsTab(nav: NavHostController) {
    var create by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    Column {
        Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            SectionTitle("Your playlists")
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { create = true }) { Icon(Icons.Filled.Add, null); Text("New") }
        }
        if (Store.playlists.isEmpty()) EmptyState("No playlists. Create one with “New”.")
        LazyColumn {
            items(Store.playlists.keys.toList(), key = { it }) { n ->
                ListItem(
                    headlineContent = { Text(n) },
                    supportingContent = { Text("${Store.playlists[n]?.size ?: 0} songs") },
                    leadingContent = { Icon(Icons.Filled.QueueMusic, null) },
                    modifier = Modifier.clickable { nav.navigate("playlist/${android.net.Uri.encode(n)}") }
                )
            }
        }
    }
    if (create) {
        AlertDialog(
            onDismissRequest = { create = false },
            title = { Text("New playlist") },
            text = { OutlinedTextField(value = name, onValueChange = { name = it }, singleLine = true, label = { Text("Name") }) },
            confirmButton = { TextButton(onClick = { Store.createPlaylist(name); name = ""; create = false }) { Text("Create") } },
            dismissButton = { TextButton(onClick = { create = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun PlaylistScreen(name: String, nav: NavHostController) {
    val ids = Store.playlists[name] ?: emptyList()
    val tracks = ids.mapNotNull { id -> Library.audio.find { it.id == id } }
    var confirm by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text(name, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { PlayerHub.play(tracks, 0) }, enabled = tracks.isNotEmpty()) { Icon(Icons.Filled.PlayArrow, "Play all") }
            IconButton(onClick = { confirm = true }) { Icon(Icons.Filled.Delete, "Delete playlist") }
        }
        if (tracks.isEmpty()) EmptyState("Empty playlist. Use “Add to playlist” from any song menu.")
        LazyColumn {
            items(tracks, key = { it.id }) { m ->
                SongRow(m, nav, onPlay = { PlayerHub.play(tracks, tracks.indexOf(m)) }) { dismiss ->
                    DropdownMenuItem(text = { Text("Remove from playlist") }, onClick = { dismiss(); Store.removeFromPlaylist(name, m.id) })
                }
            }
        }
    }
    if (confirm) {
        AlertDialog(
            onDismissRequest = { confirm = false },
            title = { Text("Delete playlist?") },
            text = { Text("“$name” will be removed. Your songs are not deleted.") },
            confirmButton = { TextButton(onClick = { Store.deletePlaylist(name); confirm = false; nav.popBackStack() }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
        )
    }
}

// ---------------------------------------------------------------- VIDEO
@Composable
fun VideoScreen() {
    var sort by remember { mutableIntStateOf(0) }
    var menu by remember { mutableStateOf(false) }
    val names = listOf("Date added", "Title", "Duration", "Size")
    val sorted = remember(Library.video, sort) {
        when (sort) {
            1 -> Library.video.sortedBy { it.title.lowercase() }
            2 -> Library.video.sortedByDescending { it.durationMs }
            3 -> Library.video.sortedByDescending { it.size }
            else -> Library.video
        }
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth().padding(16.dp, 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Videos (${sorted.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.weight(1f))
            Box {
                TextButton(onClick = { menu = true }) { Text("Sort: ${names[sort]}") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    names.forEachIndexed { i, n -> DropdownMenuItem(text = { Text(n) }, onClick = { sort = i; menu = false }) }
                }
            }
        }
        if (sorted.isEmpty()) EmptyState(if (Library.loading) "Scanning…" else "No videos found.")
        LazyColumn { items(sorted, key = { it.id }) { VideoRow(it) } }
    }
}

// ---------------------------------------------------------------- SEARCH
@Composable
fun SearchScreen(nav: NavHostController) {
    var q by remember { mutableStateOf("") }
    var filter by remember { mutableIntStateOf(0) } // 0 all, 1 music, 2 video
    val query = q.trim().lowercase()
    val songs = if (query.isEmpty() || filter == 2) emptyList() else Library.audio.filter {
        it.title.lowercase().contains(query) || it.artist.lowercase().contains(query) || it.album.lowercase().contains(query)
    }
    val videos = if (query.isEmpty() || filter == 1) emptyList() else Library.video.filter {
        it.title.lowercase().contains(query)
    }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            OutlinedTextField(
                value = q, onValueChange = { q = it }, singleLine = true,
                placeholder = { Text("Search…") }, modifier = Modifier.weight(1f),
                trailingIcon = { if (q.isNotEmpty()) IconButton(onClick = { q = "" }) { Icon(Icons.Filled.Close, "Clear") } },
                keyboardOptions = KeyboardOptions.Default
            )
        }
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf("All", "Music", "Video").forEachIndexed { i, n ->
                FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(n) })
            }
        }
        if (query.isNotEmpty() && songs.isEmpty() && videos.isEmpty()) EmptyState("No results for “$q”")
        LazyColumn {
            items(songs, key = { "s${it.id}" }) { m -> SongRow(m, nav, onPlay = { PlayerHub.play(songs, songs.indexOf(m)) }) }
            items(videos, key = { "v${it.id}" }) { VideoRow(it) }
        }
    }
}
