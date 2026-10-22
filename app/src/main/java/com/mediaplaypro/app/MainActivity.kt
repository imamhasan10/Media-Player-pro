package com.mediaplaypro.app

import android.Manifest
import android.app.PictureInPictureParams
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
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
import coil.compose.AsyncImage
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.playback.PlayerHub
import com.mediaplaypro.app.playback.YtHub
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

    /** Floating (Picture-in-Picture) window for the YouTube player. */
    fun enterPip() {
        if (YtHub.current == null) return
        try {
            enterPictureInPictureMode(
                PictureInPictureParams.Builder().setAspectRatio(Rational(16, 9)).build()
            )
        } catch (_: Exception) {
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (YtHub.current != null && YtHub.playing) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        YtHub.inPip = isInPictureInPictureMode
    }

    override fun onDestroy() {
        if (isFinishing) YtHub.release(applicationContext)
        super.onDestroy()
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
    var skipped by remember { mutableStateOf(false) }
    val launcher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { granted = mediaGranted(ctx) }

    LaunchedEffect(granted) { if (granted) Library.refresh(ctx) }

    // Ask for storage permission once at start; the app still works (Discover) without it.
    LaunchedEffect(Unit) { if (!granted) launcher.launch(perms) }

    MainScaffold(onGrant = { launcher.launch(perms) }, showGrantHint = !granted && !skipped, onSkip = { skipped = true })
}

private data class TabItem(val route: String, val label: String, val icon: ImageVector)

@Composable
private fun MainScaffold(onGrant: () -> Unit, showGrantHint: Boolean, onSkip: () -> Unit) {
    val nav = rememberNavController()
    val entry by nav.currentBackStackEntryAsState()
    val route = entry?.destination?.route
    val tabs = remember {
        listOf(
            TabItem("discover", "Discover", Icons.Filled.Search),
            TabItem("library", "Library", Icons.Filled.VideoLibrary),
            TabItem("settings", "Settings", Icons.Filled.Settings),
        )
    }
    val showTabs = tabs.any { it.route == route }
    val audioMini = PlayerHub.hasItem && route != "player"

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            bottomBar = {
                Column {
                    if (audioMini) MiniPlayer(nav)
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
            Column(Modifier.padding(pad)) {
                if (showGrantHint && route == "library") {
                    Card(Modifier.padding(12.dp)) {
                        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Allow media access to see your music and videos.", Modifier.weight(1f),
                                style = MaterialTheme.typography.bodySmall)
                            TextButton(onClick = onGrant) { Text("Allow") }
                            TextButton(onClick = onSkip) { Text("Hide") }
                        }
                    }
                }
                NavHost(nav, startDestination = "discover", modifier = Modifier.weight(1f)) {
                    composable("discover") { DiscoverScreen(nav) }
                    composable("library") { LibraryScreen(nav) }
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

        YtOverlay(
            route = route,
            bottomInset = (if (showTabs) 80.dp else 0.dp) + (if (audioMini) 64.dp else 0.dp) + 8.dp,
            onExpand = {
                YtHub.expanded = true
                nav.navigate("discover") {
                    popUpTo(nav.graph.findStartDestination().id) { saveState = true }
                    launchSingleTop = true
                    restoreState = true
                }
            }
        )
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
                Modifier.size(44.dp).clip(RoundedCornerShape(8.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.MusicNote, null)
                AsyncImage(
                    model = PlayerHub.artwork, contentDescription = null,
                    modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
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
