package com.mediaplaypro.app.playback

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.mediaplaypro.app.data.Media
import com.mediaplaypro.app.data.Store

/**
 * UI-facing controller for the playback service.
 * Holds observable state for Compose and implements the sleep timer.
 */
object PlayerHub {
    private var controller: MediaController? = null
    private val handler = Handler(Looper.getMainLooper())

    var hasItem by mutableStateOf(false)
    var isPlaying by mutableStateOf(false)
    var title by mutableStateOf("")
    var artist by mutableStateOf("")
    var artwork by mutableStateOf<Uri?>(null)
    var currentId by mutableLongStateOf(-1L)
    var shuffle by mutableStateOf(false)
    var repeat by mutableIntStateOf(Player.REPEAT_MODE_OFF)
    var speed by mutableFloatStateOf(1f)
    var queueTitles by mutableStateOf<List<String>>(emptyList())
    var queueIndex by mutableIntStateOf(0)

    // ---- sleep timer state ----
    var sleepEndAt by mutableLongStateOf(0L)        // SystemClock.elapsedRealtime() target, 0 = off
    var sleepTracksLeft by mutableIntStateOf(0)     // 0 = off

    val sleepActive: Boolean get() = sleepEndAt > 0L || sleepTracksLeft > 0

    fun connect(ctx: Context) {
        if (controller != null) return
        val app = ctx.applicationContext
        val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
        val future = MediaController.Builder(app, token).buildAsync()
        future.addListener({
            try {
                val c = future.get()
                controller = c
                c.addListener(listener)
                syncAll(c)
            } catch (_: Exception) {
            }
        }, ContextCompat.getMainExecutor(app))
    }

    private val listener = object : Player.Listener {
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            PlayerHub.isPlaying = isPlaying
        }

        override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
            controller?.let { syncAll(it) }
            val id = mediaItem?.mediaId?.toLongOrNull()
            if (id != null) Store.recordPlay(id)
            if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && sleepTracksLeft > 0) {
                sleepTracksLeft -= 1
                if (sleepTracksLeft == 0) controller?.pause()
            }
        }

        override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            controller?.let { syncAll(it) }
        }

        override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
            shuffle = shuffleModeEnabled
        }

        override fun onRepeatModeChanged(repeatMode: Int) {
            repeat = repeatMode
        }

        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            speed = playbackParameters.speed
        }
    }

    private fun syncAll(c: MediaController) {
        hasItem = c.mediaItemCount > 0
        isPlaying = c.isPlaying
        shuffle = c.shuffleModeEnabled
        repeat = c.repeatMode
        speed = c.playbackParameters.speed
        val md = c.currentMediaItem?.mediaMetadata
        title = md?.title?.toString() ?: ""
        artist = md?.artist?.toString() ?: ""
        artwork = md?.artworkUri
        currentId = c.currentMediaItem?.mediaId?.toLongOrNull() ?: -1L
        queueIndex = c.currentMediaItemIndex
        queueTitles = (0 until c.mediaItemCount).map {
            c.getMediaItemAt(it).mediaMetadata.title?.toString() ?: "Track ${it + 1}"
        }
    }

    private fun toItem(m: Media): MediaItem =
        MediaItem.Builder()
            .setMediaId(m.id.toString())
            .setUri(m.uri)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(m.title)
                    .setArtist(m.artist)
                    .setAlbumTitle(m.album)
                    .setArtworkUri(m.thumb)
                    .build()
            )
            .build()

    fun play(list: List<Media>, index: Int) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.setMediaItems(list.map { toItem(it) }, index.coerceIn(0, list.size - 1), 0L)
        c.prepare()
        c.play()
    }

    fun playShuffled(list: List<Media>) {
        val c = controller ?: return
        if (list.isEmpty()) return
        c.shuffleModeEnabled = true
        play(list, (list.indices).random())
    }

    fun playNext(m: Media) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(m), 0)
        else c.addMediaItem(c.currentMediaItemIndex + 1, toItem(m))
    }

    fun addToQueue(m: Media) {
        val c = controller ?: return
        if (c.mediaItemCount == 0) play(listOf(m), 0) else c.addMediaItem(toItem(m))
    }

    fun clearQueue() {
        controller?.clearMediaItems()
        hasItem = false
    }

    fun togglePlay() {
        val c = controller ?: return
        if (c.isPlaying) c.pause() else c.play()
    }

    fun next() { controller?.seekToNextMediaItem() }
    fun previous() { controller?.seekToPreviousMediaItem() }
    fun seekTo(ms: Long) { controller?.seekTo(ms) }
    fun skipTo(index: Int) { controller?.seekToDefaultPosition(index) }
    fun pause() { controller?.pause() }

    fun position(): Long = controller?.currentPosition ?: 0L
    fun duration(): Long = controller?.duration?.takeIf { it > 0 } ?: 0L

    fun toggleShuffle() {
        controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled }
    }

    fun cycleRepeat() {
        controller?.let {
            it.repeatMode = when (it.repeatMode) {
                Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                else -> Player.REPEAT_MODE_OFF
            }
        }
    }

    fun changeSpeed(s: Float) {
        controller?.setPlaybackSpeed(s)
    }

    // ---------------- sleep timer ----------------
    private val fadeRunnable = object : Runnable {
        var step = 0
        override fun run() {
            val c = controller ?: return
            step++
            if (step <= 10) {
                c.volume = 1f - step / 10f
                handler.postDelayed(this, 250)
            } else {
                c.pause()
                c.volume = 1f
                step = 0
            }
        }
    }

    private val sleepRunnable = Runnable {
        sleepEndAt = 0L
        fadeRunnable.step = 0
        handler.post(fadeRunnable)
    }

    fun startSleepMinutes(minutes: Int) {
        cancelSleep()
        sleepEndAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        handler.postDelayed(sleepRunnable, minutes * 60_000L)
    }

    fun startSleepTracks(count: Int) {
        cancelSleep()
        sleepTracksLeft = count
    }

    fun cancelSleep() {
        handler.removeCallbacks(sleepRunnable)
        handler.removeCallbacks(fadeRunnable)
        controller?.volume = 1f
        sleepEndAt = 0L
        sleepTracksLeft = 0
    }
}
