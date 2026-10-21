package com.mediaplaypro.app.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.background
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mediaplaypro.app.VideoPlayerActivity
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.data.Media
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.data.fmtSize
import com.mediaplaypro.app.data.fmtTime
import com.mediaplaypro.app.playback.PlayerHub

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = when (Store.themeMode) {
        1 -> false
        2 -> true
        else -> androidx.compose.foundation.isSystemInDarkTheme()
    }
    val scheme = if (dark) darkColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF5B9BFF),
        background = androidx.compose.ui.graphics.Color(0xFF0B1220),
        surface = androidx.compose.ui.graphics.Color(0xFF111A2C),
    ) else lightColorScheme(
        primary = androidx.compose.ui.graphics.Color(0xFF1E6BFF),
        background = androidx.compose.ui.graphics.Color(0xFFF6F8FC),
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

@Composable
fun Artwork(m: Media, size: Dp) {
    Box(
        Modifier.size(size).clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (m.isVideo) Icons.Filled.Movie else Icons.Filled.MusicNote, null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
        AsyncImage(
            model = m.thumb, contentDescription = null,
            modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
        )
    }
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(vertical = 4.dp))
}

@Composable
fun EmptyState(text: String) {
    Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

fun shareMedia(ctx: Context, m: Media) {
    val i = Intent(Intent.ACTION_SEND).apply {
        type = m.mime ?: if (m.isVideo) "video/*" else "audio/*"
        putExtra(Intent.EXTRA_STREAM, m.uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

fun openVideo(ctx: Context, m: Media) {
    val list = Library.video
    VideoPlayerActivity.start(ctx, list.map { it.id }.toLongArray(), list.indexOfFirst { it.id == m.id }.coerceAtLeast(0))
}

/** Song row with the shared track-action menu (queue, playlist, favorite, trim, share, details). */
@Composable
fun SongRow(m: Media, nav: NavHostController, onPlay: () -> Unit, extraMenu: (@Composable (dismiss: () -> Unit) -> Unit)? = null) {
    val ctx = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    var details by remember { mutableStateOf(false) }
    var addToPl by remember { mutableStateOf(false) }
    val fav = m.id in Store.favorites
    val current = PlayerHub.currentId == m.id

    Row(
        Modifier.fillMaxWidth().clickable { onPlay() }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(m, 48.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                m.title, maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodyLarge,
                color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
            )
            Text(
                "${m.artist} · ${fmtTime(m.durationMs)}", maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        IconButton(onClick = { Store.toggleFavorite(m.id) }) {
            Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More") }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play next") }, onClick = { menu = false; PlayerHub.playNext(m) })
                DropdownMenuItem(text = { Text("Add to queue") }, onClick = { menu = false; PlayerHub.addToQueue(m) })
                DropdownMenuItem(text = { Text("Add to playlist") }, onClick = { menu = false; addToPl = true })
                DropdownMenuItem(text = { Text("Trim / cut audio") }, onClick = { menu = false; nav.navigate("trim/${m.id}") })
                DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; shareMedia(ctx, m) })
                DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; details = true })
                extraMenu?.invoke { menu = false }
            }
        }
    }
    if (details) {
        AlertDialog(
            onDismissRequest = { details = false },
            confirmButton = { TextButton(onClick = { details = false }) { Text("Close") } },
            title = { Text(m.title) },
            text = {
                Text("Artist: ${m.artist}\nAlbum: ${m.album}\nDuration: ${fmtTime(m.durationMs)}\n" +
                    "Size: ${fmtSize(m.size)}\nType: ${m.mime ?: "-"}\nPlays: ${Store.playCounts[m.id] ?: 0}")
            }
        )
    }
    if (addToPl) AddToPlaylistDialog(m.id) { addToPl = false }
}

@Composable
fun AddToPlaylistDialog(trackId: Long, onDismiss: () -> Unit) {
    var newName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Add to playlist") },
        text = {
            Column {
                Store.playlists.keys.forEach { name ->
                    TextButton(onClick = { Store.addToPlaylist(name, trackId); onDismiss() }) { Text(name) }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newName, onValueChange = { newName = it },
                        label = { Text("New playlist") }, singleLine = true, modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = {
                        if (newName.isNotBlank()) {
                            Store.createPlaylist(newName)
                            Store.addToPlaylist(newName.trim(), trackId)
                            onDismiss()
                        }
                    }) { Icon(Icons.Filled.Add, "Create") }
                }
            }
        }
    )
}

@Composable
fun VideoRow(m: Media) {
    val ctx = LocalContext.current
    Row(
        Modifier.fillMaxWidth().clickable { openVideo(ctx, m) }.padding(horizontal = 16.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            Artwork(m, 72.dp)
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, maxLines = 2, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text("${fmtTime(m.durationMs)} · ${fmtSize(m.size)}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = {
            ctx.startActivity(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).apply {
                        type = m.mime ?: "video/*"
                        putExtra(Intent.EXTRA_STREAM, m.uri)
                        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }, "Share"
                ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }) { Icon(Icons.Filled.Share, "Share") }
    }
}
