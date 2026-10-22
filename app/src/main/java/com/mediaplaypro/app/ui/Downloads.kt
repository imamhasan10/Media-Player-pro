package com.mediaplaypro.app.ui

import android.app.DownloadManager
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.URLUtil
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.mediaplaypro.app.VideoPlayerActivity
import com.mediaplaypro.app.data.Media
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.data.fmtSize
import com.mediaplaypro.app.playback.PlayerHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

private data class Dl(
    val id: Long, val title: String, val status: Int,
    val total: Long, val sofar: Long, val reason: Int, val url: String, val mime: String?
)

private fun queryDownloads(dm: DownloadManager): List<Dl> {
    val out = ArrayList<Dl>()
    dm.query(DownloadManager.Query())?.use { c ->
        val iId = c.getColumnIndexOrThrow(DownloadManager.COLUMN_ID)
        val iTitle = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TITLE)
        val iStatus = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
        val iTotal = c.getColumnIndexOrThrow(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
        val iSo = c.getColumnIndexOrThrow(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
        val iReason = c.getColumnIndexOrThrow(DownloadManager.COLUMN_REASON)
        val iUri = c.getColumnIndexOrThrow(DownloadManager.COLUMN_URI)
        val iMime = c.getColumnIndexOrThrow(DownloadManager.COLUMN_MEDIA_TYPE)
        while (c.moveToNext()) {
            out.add(
                Dl(
                    c.getLong(iId), c.getString(iTitle) ?: "", c.getInt(iStatus),
                    c.getLong(iTotal), c.getLong(iSo), c.getInt(iReason),
                    c.getString(iUri) ?: "", c.getString(iMime)
                )
            )
        }
    }
    return out.sortedByDescending { it.id }
}

private fun enqueue(ctx: Context, dm: DownloadManager, url: String, name: String): String? {
    if (!(url.startsWith("http://") || url.startsWith("https://"))) return "Enter a valid http(s) link."
    val lower = name.lowercase()
    val dir = when {
        listOf(".mp3", ".m4a", ".aac", ".flac", ".wav", ".ogg", ".opus").any { lower.endsWith(it) } -> Environment.DIRECTORY_MUSIC
        listOf(".mp4", ".mkv", ".webm", ".mov", ".avi", ".3gp").any { lower.endsWith(it) } -> Environment.DIRECTORY_MOVIES
        else -> Environment.DIRECTORY_DOWNLOADS
    }
    return try {
        val req = DownloadManager.Request(Uri.parse(url))
            .setTitle(name)
            .setDescription("MediaPlay Pro")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setAllowedOverMetered(!Store.wifiOnly)
            .setAllowedOverRoaming(false)
            .setDestinationInExternalPublicDir(dir, "MediaPlayPro/$name")
        dm.enqueue(req)
        null
    } catch (e: Exception) {
        "Could not start download: ${e.message}"
    }
}

@Composable
fun DownloadsScreen() {
    val ctx = LocalContext.current
    val dm = remember { ctx.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager }
    var url by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var message by remember { mutableStateOf<String?>(null) }
    var filter by remember { mutableIntStateOf(0) }
    var items by remember { mutableStateOf<List<Dl>>(emptyList()) }

    LaunchedEffect(Unit) {
        while (true) {
            items = withContext(Dispatchers.IO) { runCatching { queryDownloads(dm) }.getOrDefault(emptyList()) }
            delay(1000)
        }
    }

    val shown = items.filter {
        when (filter) {
            1 -> it.status == DownloadManager.STATUS_RUNNING || it.status == DownloadManager.STATUS_PENDING || it.status == DownloadManager.STATUS_PAUSED
            2 -> it.status == DownloadManager.STATUS_SUCCESSFUL
            3 -> it.status == DownloadManager.STATUS_FAILED
            else -> true
        }
    }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { Text("Download manager", style = MaterialTheme.typography.headlineSmall) }
        item {
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                Text(
                    "Paste a direct link to a media file (e.g. https://…/song.mp3 or …/video.mp4) that you own " +
                        "or have permission to save. Streaming sites and DRM-protected content are not supported.",
                    modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall
                )
            }
        }
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = url, onValueChange = { url = it },
                    label = { Text("Media URL") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
                    trailingIcon = {
                        IconButton(onClick = {
                            val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val t = cm.primaryClip?.getItemAt(0)?.text?.toString()
                            if (t != null) url = t.trim()
                        }) { Icon(Icons.Filled.ContentPaste, "Paste") }
                    }
                )
                OutlinedTextField(
                    value = name, onValueChange = { name = it },
                    label = { Text("File name (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth()
                )
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = {
                        val u = url.trim()
                        val fn = name.trim().ifBlank { URLUtil.guessFileName(u, null, null) }
                        message = if (items.any { it.url == u && it.status != DownloadManager.STATUS_FAILED }) {
                            "This link is already in your downloads."
                        } else {
                            enqueue(ctx, dm, u, fn) ?: "Download started."
                        }
                        if (message == "Download started.") { url = ""; name = "" }
                    }, enabled = url.isNotBlank()) {
                        Icon(Icons.Filled.Download, null); Spacer(Modifier.width(6.dp)); Text("Download")
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(if (Store.wifiOnly) "Wi-Fi only" else "Wi-Fi + mobile data",
                        style = MaterialTheme.typography.bodySmall)
                }
                message?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodySmall) }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("All", "Active", "Done", "Failed").forEachIndexed { i, n ->
                    FilterChip(selected = filter == i, onClick = { filter = i }, label = { Text(n) })
                }
            }
        }
        if (shown.isEmpty()) item { EmptyState("No downloads here yet.") }
        items(shown, key = { it.id }) { d -> DownloadCard(ctx, dm, d) { url = d.url } }
    }
}

@Composable
private fun DownloadCard(ctx: Context, dm: DownloadManager, d: Dl, onRetryFill: () -> Unit) {
    val active = d.status == DownloadManager.STATUS_RUNNING || d.status == DownloadManager.STATUS_PENDING ||
        d.status == DownloadManager.STATUS_PAUSED
    val frac = if (d.total > 0) (d.sofar.toFloat() / d.total).coerceIn(0f, 1f) else 0f
    val statusText = when (d.status) {
        DownloadManager.STATUS_RUNNING -> "Downloading ${(frac * 100).toInt()}% · ${fmtSize(d.sofar)} / ${if (d.total > 0) fmtSize(d.total) else "?"}"
        DownloadManager.STATUS_PENDING -> "Queued"
        DownloadManager.STATUS_PAUSED -> "Waiting for network / Wi-Fi (reason ${d.reason})"
        DownloadManager.STATUS_SUCCESSFUL -> "Completed · ${fmtSize(d.total.takeIf { it > 0 } ?: d.sofar)}"
        else -> "Failed (error ${d.reason})"
    }
    ElevatedCard(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(d.title, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.bodyLarge)
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (active) {
                Spacer(Modifier.height(6.dp))
                if (d.total > 0) LinearProgressIndicator(progress = { frac }, modifier = Modifier.fillMaxWidth())
                else LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            Row {
                Spacer(Modifier.weight(1f))
                if (d.status == DownloadManager.STATUS_SUCCESSFUL) {
                    IconButton(onClick = { playDownload(ctx, dm, d) }) { Icon(Icons.Filled.PlayArrow, "Play") }
                    IconButton(onClick = { shareDownload(ctx, dm, d) }) { Icon(Icons.Filled.Share, "Share") }
                }
                if (d.status == DownloadManager.STATUS_FAILED) {
                    IconButton(onClick = { dm.remove(d.id); enqueue(ctx, dm, d.url, d.title) }) { Icon(Icons.Filled.Refresh, "Retry") }
                }
                var confirm by remember { mutableStateOf(false) }
                IconButton(onClick = { if (active) dm.remove(d.id) else confirm = true }) {
                    Icon(if (active) Icons.Filled.Close else Icons.Filled.Delete, if (active) "Cancel" else "Delete")
                }
                if (confirm) {
                    AlertDialog(
                        onDismissRequest = { confirm = false },
                        title = { Text("Delete download?") },
                        text = { Text("The downloaded file will be deleted from your device.") },
                        confirmButton = { TextButton(onClick = { dm.remove(d.id); confirm = false }) { Text("Delete") } },
                        dismissButton = { TextButton(onClick = { confirm = false }) { Text("Cancel") } }
                    )
                }
            }
        }
    }
}

private fun playDownload(ctx: Context, dm: DownloadManager, d: Dl) {
    val u = dm.getUriForDownloadedFile(d.id) ?: return
    val mime = d.mime ?: ""
    when {
        mime.startsWith("video") -> VideoPlayerActivity.startUri(ctx, u.toString(), d.title)
        mime.startsWith("audio") -> PlayerHub.play(
            listOf(Media(-d.id - 1, u, d.title.substringBeforeLast('.'), "Download", "", 0, 0, d.sofar, 0, false, mime)), 0
        )
        else -> ctx.startActivity(
            Intent(Intent.ACTION_VIEW).setDataAndType(u, mime.ifBlank { "*/*" })
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        )
    }
}

private fun shareDownload(ctx: Context, dm: DownloadManager, d: Dl) {
    val u = dm.getUriForDownloadedFile(d.id) ?: return
    val i = Intent(Intent.ACTION_SEND).apply {
        type = d.mime ?: "*/*"
        putExtra(Intent.EXTRA_STREAM, u)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }
    ctx.startActivity(Intent.createChooser(i, "Share").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}
