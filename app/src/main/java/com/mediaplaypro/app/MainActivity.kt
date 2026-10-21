package com.mediaplaypro.app

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.compose.ui.graphics.vector.ImageVector
import coil.compose.AsyncImage
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.playback.PlayerHub
import com.mediaplaypro.app.ui.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            AppTheme {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Root()
                }
            }
        }
    }
}

private fun mediaGranted(ctx: Context): Boolean {
    fun ok(p: String) = ContextCompat.checkSelfPermission(ctx, p) == PackageManager.PERMISSION_GRANTED
    return if (Build.VERSION.SDK_INT >= 33)
        ok(Manifest.permission.READ_MEDIA_AUDIO) || ok(Manifest.permission.READ_MEDIA_VIDEO)
    else ok(Manifest.permission.READ_EXTERNAL_STORAGE)
}

@Composable
private fun Root() {
    val ctx = LocalContext.current
    val perms = remember {
        if (Build.VERSION.SDK_INT >= 33)
            arrayOf(
                Manifest.permission.READ_MEDIA_AUDIO,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.POST_NOTIFICATIONS
            )
        else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
    }
    var granted by remember { mutableStateOf(mediaGranted(ctx)) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = mediaGranted(ctx) }

    LaunchedEffect(granted) { if (granted) Library.refresh(ctx) }

    if (!granted) {
        Column(
            Modifier.fillMaxSize().padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Filled.PlayCircle, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(16.dp))
            Text("MediaPlay Pro", style = MaterialTheme.typography.headlineMedium)
            Spacer(Modifier.height(8.dp))
            Text(
                "To show your music and videos the app needs permission to read media files. " +
                    "Files never leave your device.",
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = { launcher.launch(perms) }) { Text("Allow access") }
        }
    } else {
        MainScaffold()
    }
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold() {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = remember {
        listOf(
            TabItem("home", "Home", Icons.Filled.Home),
            TabItem("music", "Music", Icons.Filled.MusicNote),
            TabItem("video", "Video", Icons.Filled.VideoLibrary),
            TabItem("downloads", "Downloads", Icons.Filled.Download),
            TabItem("settings", "Settings", Icons.Filled.Settings),
        )
    }
    val showTabs = tabs.any { it.route == route }

    Scaffold(
        bottomBar = {
            Column {
                if (PlayerHub.hasItem && route != "player") MiniPlayer(nav)
                if (showTabs) {
                    NavigationBar {
                        tabs.forEach { t ->
                            NavigationBarItem(
                                selected = route == t.route,
                                onClick = {
                                    nav.navigate(t.route) {
                                        popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = { Icon(t.icon, t.label) },
                                label = { Text(t.label, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
            }
        }
    ) { pad ->
        NavHost(nav, startDestination = "home", modifier = Modifier.padding(pad)) {
            composable("home") { HomeScreen(nav) }
            composable("music") { MusicScreen(nav) }
            composable("video") { VideoScreen() }
            composable("downloads") { DownloadsScreen() }
            composable("settings") { SettingsScreen() }
            composable("search") { SearchScreen(nav) }
            composable("player") { PlayerScreen(nav) }
            composable("equalizer") { EqualizerScreen(nav) }
            composable(
                "trim/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { TrimScreen(it.arguments?.getLong("id") ?: -1L, nav) }
            composable(
                "playlist/{name}",
                arguments = listOf(navArgument("name") { type = NavType.StringType })
            ) { PlaylistScreen(it.arguments?.getString("name") ?: "", nav) }
        }
    }
}

@Composable
private fun MiniPlayer(nav: NavHostController) {
    Surface(tonalElevation = 6.dp, modifier = Modifier.fillMaxWidth().clickable { nav.navigate("player") }) {
        Row(
            Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(44.dp).clip(MaterialTheme.shapes.small)
                    .then(Modifier),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null)
                AsyncImage(
                    model = PlayerHub.artwork, contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(PlayerHub.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodyLarge)
                Text(PlayerHub.artist, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.bodySmall)
            }
            IconButton(onClick = { PlayerHub.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
            IconButton(onClick = { PlayerHub.togglePlay() }) {
                Icon(if (PlayerHub.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = { PlayerHub.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
        }
    }
}
