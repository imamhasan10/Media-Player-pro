package com.mediaplaypro.app.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mediaplaypro.app.MainActivity
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.data.YouTubeApi
import com.mediaplaypro.app.data.YtVideo
import com.mediaplaypro.app.playback.YtHub

private fun openOnYoutube(ctx: Context, id: String) {
    ctx.startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com/watch?v=$id"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

// ---------------------------------------------------------------- DISCOVER PAGE
@Composable
fun DiscoverScreen(nav: NavHostController) {
    val ctx = LocalContext.current
    var tab by remember { mutableIntStateOf(0) } // 0 Mix, 1 Recommend
    var input by remember { mutableStateOf("") }
    var submitted by remember { mutableStateOf("") }
    var videos by remember { mutableStateOf<List<YtVideo>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var reload by remember { mutableIntStateOf(0) }
    val key = Store.effectiveYtKey()

    LaunchedEffect(tab, submitted, key, Store.region, reload) {
        if (key.isBlank()) { videos = emptyList(); error = null; return@LaunchedEffect }
        loading = true
        error = null
        val res = when {
            submitted.isNotBlank() -> YouTubeApi.search(key, submitted, Store.region)
            tab == 0 -> YouTubeApi.trending(key, Store.region)
            else -> YouTubeApi.search(key, YtHub.lastTitle.ifBlank { "top songs" }, Store.region)
        }
        videos = res.videos
        error = res.error
        loading = false
    }

    Column(Modifier.fillMaxSize()) {
        // The real player is drawn by YtOverlay on top of this spacer.
        if (YtHub.expanded && YtHub.current != null) {
            Spacer(Modifier.fillMaxWidth().aspectRatio(16f / 9f))
            NowPlayingBar(ctx)
        }

        OutlinedTextField(
            value = input, onValueChange = { input = it },
            placeholder = { Text("Search videos…") }, singleLine = true,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = {
                if (input.isNotEmpty()) IconButton(onClick = { input = ""; submitted = "" }) { Icon(Icons.Filled.Close, "Clear") }
            },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { submitted = input.trim() }),
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        )
        Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(selected = tab == 0 && submitted.isBlank(), onClick = { tab = 0; submitted = ""; input = "" }, label = { Text("Mix") })
            FilterChip(selected = tab == 1 && submitted.isBlank(), onClick = { tab = 1; submitted = ""; input = "" }, label = { Text("Recommend") })
        }

        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            if (key.isBlank()) item {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                    Column(Modifier.padding(16.dp)) {
                        Text("YouTube API key needed", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Add a free YouTube Data API v3 key in Settings to list videos here.",
                            style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(vertical = 8.dp)
                        )
                        Button(onClick = { nav.navigate("settings") }) { Text("Open Settings") }
                    }
                }
            }
            if (loading) item { LinearProgressIndicator(Modifier.fillMaxWidth()) }
            error?.let { msg ->
                item {
                    Column {
                        Text(msg, color = MaterialTheme.colorScheme.error)
                        TextButton(onClick = { reload++ }) { Text("Retry") }
                    }
                }
            }
            itemsIndexed(videos, key = { _, v -> v.id }) { i, v ->
                YtCard(v, YtHub.current?.id == v.id) { YtHub.open(videos, i) }
            }
        }
    }
}

@Composable
private fun YtCard(v: YtVideo, isCurrent: Boolean, onClick: () -> Unit) {
    val ctx = LocalContext.current
    Column(Modifier.fillMaxWidth().clickable { onClick() }) {
        Box {
            AsyncImage(
                model = v.thumb, contentDescription = v.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
                    .clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surfaceVariant)
            )
            if (v.duration.isNotEmpty()) {
                Text(
                    v.duration, color = Color.White, style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.align(Alignment.BottomEnd).padding(8.dp)
                        .background(Color(0xAA000000), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
        }
        Row(Modifier.padding(top = 8.dp), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(
                    v.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                )
                Text(v.channel, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { openOnYoutube(ctx, v.id) }) { Icon(Icons.Filled.OpenInNew, "Open on YouTube") }
        }
    }
}

@Composable
private fun NowPlayingBar(ctx: Context) {
    val v = YtHub.current ?: return
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)) {
        Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleSmall)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(v.channel, maxLines = 1, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
            IconButton(onClick = { YtHub.previous() }) { Icon(Icons.Filled.SkipPrevious, "Previous") }
            FilledIconButton(onClick = { YtHub.togglePlay() }) {
                Icon(if (YtHub.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
            }
            IconButton(onClick = { YtHub.next() }) { Icon(Icons.Filled.SkipNext, "Next") }
            IconButton(onClick = { YtHub.expanded = false }) { Icon(Icons.Filled.KeyboardArrowDown, "Minimize") }
            IconButton(onClick = { (ctx as? MainActivity)?.enterPip() }) { Icon(Icons.Filled.PictureInPictureAlt, "Floating window") }
            IconButton(onClick = { YtHub.close() }) { Icon(Icons.Filled.Close, "Close") }
        }
        YtHub.error?.let {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    "This video can't be played inside the app.",
                    color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { openOnYoutube(ctx, v.id) }) { Text("Open in YouTube") }
            }
        }
    }
}

// ---------------------------------------------------------------- PLAYER HOST (one WebView, three layouts)
/**
 * Single call-site for the player so the same WebView survives layout changes:
 *  - expanded on the Discover page (top, 16:9)
 *  - mini window (bottom-right) on every other page, or after "minimize"
 *  - full-screen when the app is in Picture-in-Picture
 */
@Composable
fun BoxScope.YtOverlay(route: String?, bottomInset: Dp, onExpand: () -> Unit) {
    val v = YtHub.current ?: return
    val full = YtHub.expanded && route == "discover"
    val pip = YtHub.inPip

    val containerMod = when {
        pip -> Modifier.align(Alignment.Center).fillMaxSize().background(Color.Black)
        full -> Modifier.align(Alignment.TopCenter).fillMaxWidth().background(Color.Black)
        else -> Modifier.align(Alignment.BottomEnd)
            .padding(end = 12.dp, bottom = bottomInset)
            .width(230.dp)
            .shadow(8.dp, RoundedCornerShape(12.dp))
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surface)
    }
    Column(containerMod) {
        AndroidView(
            factory = { c -> YtHub.webView(c) },
            modifier = if (pip) Modifier.fillMaxSize() else Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        if (!full && !pip) {
            Row(Modifier.fillMaxWidth().padding(start = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(v.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    style = MaterialTheme.typography.labelMedium, modifier = Modifier.weight(1f))
                IconButton(onClick = { YtHub.togglePlay() }, modifier = Modifier.size(36.dp)) {
                    Icon(if (YtHub.playing) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause")
                }
                IconButton(onClick = onExpand, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.OpenInFull, "Expand") }
                IconButton(onClick = { YtHub.close() }, modifier = Modifier.size(36.dp)) { Icon(Icons.Filled.Close, "Close") }
            }
        }
    }
}
