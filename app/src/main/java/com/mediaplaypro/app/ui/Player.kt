package com.mediaplaypro.app.ui

import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.navigation.NavHostController
import coil.compose.AsyncImage
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.data.fmtTime
import com.mediaplaypro.app.playback.AudioTrimmer
import com.mediaplaypro.app.playback.EqEngine
import com.mediaplaypro.app.playback.PlayerHub
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// ---------------------------------------------------------------- NOW PLAYING
@Composable
fun PlayerScreen(nav: NavHostController) {
    var pos by remember { mutableLongStateOf(0L) }
    var dur by remember { mutableLongStateOf(0L) }
    var drag by remember { mutableStateOf<Float?>(null) }
    var sleepDialog by remember { mutableStateOf(false) }
    var queueDialog by remember { mutableStateOf(false) }
    var speedMenu by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (true) {
            pos = PlayerHub.position()
            dur = PlayerHub.duration()
            delay(400)
        }
    }

    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.Filled.KeyboardArrowDown, "Close") }
            Spacer(Modifier.weight(1f))
            Text("Now playing", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.weight(1f))
            IconButton(onClick = { queueDialog = true }) { Icon(Icons.Filled.QueueMusic, "Queue") }
        }
        Spacer(Modifier.height(12.dp))
        Box(
            Modifier.fillMaxWidth(0.8f).aspectRatio(1f).clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, Modifier.size(96.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            AsyncImage(
                model = PlayerHub.artwork, contentDescription = null,
                modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop
            )
        }
        Spacer(Modifier.height(20.dp))
        Text(PlayerHub.title, style = MaterialTheme.typography.titleLarge, maxLines = 1,
            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(PlayerHub.artist, style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        Spacer(Modifier.height(12.dp))

        Slider(
            value = drag ?: pos.toFloat().coerceAtMost(dur.toFloat().coerceAtLeast(1f)),
            onValueChange = { drag = it },
            onValueChangeFinished = { drag?.let { PlayerHub.seekTo(it.toLong()) }; drag = null },
            valueRange = 0f..dur.toFloat().coerceAtLeast(1f)
        )
        Row(Modifier.fillMaxWidth()) {
            Text(fmtTime(drag?.toLong() ?: pos), style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.weight(1f))
            Text(fmtTime(dur), style = MaterialTheme.typography.labelMedium)
        }
        Spacer(Modifier.height(8.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { PlayerHub.toggleShuffle() }) {
                Icon(Icons.Filled.Shuffle, "Shuffle",
                    tint = if (PlayerHub.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { PlayerHub.previous() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipPrevious, "Previous", Modifier.size(36.dp))
            }
            FilledIconButton(onClick = { PlayerHub.togglePlay() }, modifier = Modifier.size(68.dp)) {
                Icon(if (PlayerHub.isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow, "Play/Pause", Modifier.size(38.dp))
            }
            IconButton(onClick = { PlayerHub.next() }, modifier = Modifier.size(56.dp)) {
                Icon(Icons.Filled.SkipNext, "Next", Modifier.size(36.dp))
            }
            IconButton(onClick = { PlayerHub.cycleRepeat() }) {
                Icon(
                    if (PlayerHub.repeat == Player.REPEAT_MODE_ONE) Icons.Filled.RepeatOne else Icons.Filled.Repeat,
                    "Repeat",
                    tint = if (PlayerHub.repeat == Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
            Box {
                TextButton(onClick = { speedMenu = true }) {
                    Icon(Icons.Filled.Speed, null); Spacer(Modifier.width(4.dp)); Text("${PlayerHub.speed}x")
                }
                DropdownMenu(expanded = speedMenu, onDismissRequest = { speedMenu = false }) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f).forEach { s ->
                        DropdownMenuItem(text = { Text("${s}x") }, onClick = { PlayerHub.setSpeed(s); speedMenu = false })
                    }
                }
            }
            IconButton(onClick = { sleepDialog = true }) {
                Icon(Icons.Filled.Bedtime, "Sleep timer",
                    tint = if (PlayerHub.sleepActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { nav.navigate("equalizer") }) { Icon(Icons.Filled.Equalizer, "Equalizer") }
            val fav = PlayerHub.currentId in Store.favorites
            IconButton(onClick = { if (PlayerHub.currentId >= 0) Store.toggleFavorite(PlayerHub.currentId) }) {
                Icon(if (fav) Icons.Filled.Favorite else Icons.Filled.FavoriteBorder, "Favorite",
                    tint = if (fav) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = { if (PlayerHub.currentId >= 0) nav.navigate("trim/${PlayerHub.currentId}") }) {
                Icon(Icons.Filled.ContentCut, "Trim")
            }
        }
    }

    if (sleepDialog) SleepTimerDialog { sleepDialog = false }
    if (queueDialog) {
        AlertDialog(
            onDismissRequest = { queueDialog = false },
            title = { Text("Queue (${PlayerHub.queueTitles.size})") },
            confirmButton = { TextButton(onClick = { queueDialog = false }) { Text("Close") } },
            dismissButton = { TextButton(onClick = { PlayerHub.clearQueue(); queueDialog = false; nav.popBackStack() }) { Text("Clear queue") } },
            text = {
                LazyColumn(Modifier.heightIn(max = 360.dp)) {
                    itemsIndexed(PlayerHub.queueTitles) { i, t ->
                        Text(
                            "${i + 1}. $t", maxLines = 1, overflow = TextOverflow.Ellipsis,
                            color = if (i == PlayerHub.queueIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.fillMaxWidth().clickable { PlayerHub.skipTo(i) }.padding(vertical = 10.dp)
                        )
                    }
                }
            }
        )
    }
}

// ---------------------------------------------------------------- SLEEP TIMER
@Composable
fun SleepTimerDialog(onDismiss: () -> Unit) {
    var mode by remember { mutableIntStateOf(0) } // 0 = time, 1 = songs
    var minutes by remember { mutableFloatStateOf(15f) }
    var songs by remember { mutableFloatStateOf(3f) }
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(Unit) { while (true) { now = SystemClock.elapsedRealtime(); delay(1000) } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sleep timer") },
        text = {
            Column {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = mode == 0, onClick = { mode = 0 }, label = { Text("Time") })
                    FilterChip(selected = mode == 1, onClick = { mode = 1 }, label = { Text("Songs") })
                }
                Spacer(Modifier.height(12.dp))
                if (mode == 0) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(5, 10, 15, 30).forEach { m ->
                            AssistChip(onClick = { minutes = m.toFloat() }, label = { Text("${m}m") })
                        }
                    }
                    Text("${minutes.toInt()} minutes", style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 8.dp))
                    Slider(value = minutes, onValueChange = { minutes = it }, valueRange = 1f..120f)
                } else {
                    Text("Stop after ${songs.toInt()} songs", style = MaterialTheme.typography.titleMedium)
                    Slider(value = songs, onValueChange = { songs = it }, valueRange = 1f..20f, steps = 18)
                }
                if (PlayerHub.sleepActive) {
                    val label = if (PlayerHub.sleepEndAt > 0)
                        "Active: ${fmtTime((PlayerHub.sleepEndAt - now).coerceAtLeast(0))} left (fades out)"
                    else "Active: ${PlayerHub.sleepTracksLeft} songs left"
                    Text(label, color = MaterialTheme.colorScheme.primary)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = {
                if (mode == 0) PlayerHub.startSleepMinutes(minutes.toInt()) else PlayerHub.startSleepTracks(songs.toInt())
                onDismiss()
            }) { Text("Set timer") }
        },
        dismissButton = {
            TextButton(onClick = { if (PlayerHub.sleepActive) PlayerHub.cancelSleep(); onDismiss() }) {
                Text(if (PlayerHub.sleepActive) "Cancel timer" else "Close")
            }
        }
    )
}

// ---------------------------------------------------------------- EQUALIZER
@Composable
fun EqualizerScreen(nav: NavHostController) {
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Equalizer", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            TextButton(onClick = { EqEngine.reset() }, enabled = EqEngine.available) { Text("Reset all") }
            Switch(checked = EqEngine.enabled, onCheckedChange = { EqEngine.setEnabled(it) }, enabled = EqEngine.available)
        }
        if (!EqEngine.available) {
            EmptyState("Equalizer is not ready yet. Start playing a song first, then come back.")
            return@Column
        }
        Text(if (EqEngine.enabled) "Effects active" else "Effects off",
            color = if (EqEngine.enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 12.dp, bottom = 8.dp))

        if (EqEngine.presets.isNotEmpty()) {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                item { AssistChip(onClick = { EqEngine.reset() }, label = { Text("Flat") }) }
                itemsIndexed(EqEngine.presets) { i, n ->
                    AssistChip(onClick = { EqEngine.usePreset(i) }, label = { Text(n) })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        EqEngine.levels.forEachIndexed { i, level ->
            val hz = EqEngine.freqs.getOrNull(i) ?: 0
            val label = if (hz >= 1000) "${hz / 1000} kHz" else "$hz Hz"
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(label, modifier = Modifier.width(64.dp), style = MaterialTheme.typography.labelMedium)
                Slider(
                    value = level.toFloat(),
                    onValueChange = { EqEngine.setBand(i, it.toInt()) },
                    valueRange = EqEngine.minLevel.toFloat()..EqEngine.maxLevel.toFloat(),
                    modifier = Modifier.weight(1f)
                )
                Text("%+.0f dB".format(level / 100f), modifier = Modifier.width(56.dp),
                    style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.End)
            }
        }
        HorizontalDivider(Modifier.padding(vertical = 8.dp))
        if (EqEngine.bassSupported) {
            Text("Bass boost", style = MaterialTheme.typography.labelLarge)
            Slider(value = EqEngine.bassStrength.toFloat(), onValueChange = { EqEngine.setBass(it.toInt()) }, valueRange = 0f..1000f)
        }
        if (EqEngine.virtSupported) {
            Text("Virtualizer / surround", style = MaterialTheme.typography.labelLarge)
            Slider(value = EqEngine.virtStrength.toFloat(), onValueChange = { EqEngine.setVirt(it.toInt()) }, valueRange = 0f..1000f)
        }
        Text("Loudness boost (max +10 dB to avoid clipping)", style = MaterialTheme.typography.labelLarge)
        Slider(value = EqEngine.loudGain.toFloat(), onValueChange = { EqEngine.setLoud(it.toInt()) }, valueRange = 0f..1000f)
        if (EqEngine.loudGain > 600) {
            Text("High gain can cause distortion.", color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall)
        }
    }
}

// ---------------------------------------------------------------- AUDIO TRIMMER
@Composable
fun TrimScreen(id: Long, nav: NavHostController) {
    val ctx = LocalContext.current
    val scope = rememberCoroutineScope()
    val m = Library.audio.find { it.id == id }
    if (m == null) { EmptyState("Track not found."); return }

    val durSec = (m.durationMs / 1000f).coerceAtLeast(1f)
    var range by remember { mutableStateOf(0f..durSec) }
    var name by remember { mutableStateOf("${m.title} (cut)") }
    var status by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var previewing by remember { mutableStateOf(false) }
    val player = remember { MediaPlayer() }
    val handler = remember { Handler(Looper.getMainLooper()) }
    val stopRunnable = remember { Runnable { try { if (player.isPlaying) player.pause() } catch (_: Exception) {}; previewing = false } }

    DisposableEffect(Unit) {
        onDispose {
            handler.removeCallbacks(stopRunnable)
            try { player.release() } catch (_: Exception) {}
        }
    }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { nav.popBackStack() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
            Text("Audio trimmer", style = MaterialTheme.typography.titleLarge)
        }
        Text(m.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(16.dp))
        RangeSlider(value = range, onValueChange = { range = it }, valueRange = 0f..durSec)
        Row(Modifier.fillMaxWidth()) {
            Text("Start ${fmtTime((range.start * 1000).toLong())}")
            Spacer(Modifier.weight(1f))
            Text("End ${fmtTime((range.endInclusive * 1000).toLong())}")
        }
        Text("Length ${fmtTime(((range.endInclusive - range.start) * 1000).toLong())}",
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onClick = {
            try {
                PlayerHub.pause()
                handler.removeCallbacks(stopRunnable)
                player.reset()
                player.setDataSource(ctx, m.uri)
                player.prepare()
                player.seekTo((range.start * 1000).toInt())
                player.start()
                previewing = true
                handler.postDelayed(stopRunnable, ((range.endInclusive - range.start) * 1000).toLong())
            } catch (e: Exception) {
                status = "Preview failed: ${e.message}"
            }
        }) {
            Icon(Icons.Filled.PlayArrow, null); Spacer(Modifier.width(6.dp)); Text(if (previewing) "Previewing…" else "Preview selection")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Output file name") },
            singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        Button(
            enabled = !busy && range.endInclusive - range.start >= 1f,
            onClick = {
                busy = true; status = "Saving…"
                scope.launch {
                    val r = withContext(Dispatchers.IO) {
                        AudioTrimmer.trim(ctx, m.uri, (range.start * 1000).toLong(), (range.endInclusive * 1000).toLong(), name)
                    }
                    busy = false
                    status = r.fold({ "Saved to $it" }, { "Failed: ${it.message}" })
                    if (r.isSuccess) Library.refresh(ctx)
                }
            }
        ) { Text("Save trimmed audio") }
        status?.let { Text(it, modifier = Modifier.padding(top = 12.dp)) }
        Text(
            "Lossless cut (no quality loss). Supported for AAC/M4A files.",
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 12.dp)
        )
    }
}
