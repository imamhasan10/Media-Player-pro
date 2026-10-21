package com.mediaplaypro.app

import android.app.PictureInPictureParams
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.view.Gravity
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.mediaplaypro.app.data.Library
import com.mediaplaypro.app.data.Store
import com.mediaplaypro.app.playback.PlayerHub
import kotlin.math.roundToInt

/**
 * Full-screen video player: gestures (double-tap seek, brightness/volume swipes),
 * resize modes, orientation lock, touch lock, PiP, external subtitles,
 * multiple audio/subtitle tracks + speed (via the built-in settings menu), resume position.
 */
class VideoPlayerActivity : ComponentActivity() {
    private var player: ExoPlayer? = null
    private lateinit var playerView: PlayerView
    private lateinit var topBar: LinearLayout
    private lateinit var unlockBtn: Button
    private lateinit var feedback: TextView
    private lateinit var titleView: TextView
    private lateinit var audioManager: AudioManager

    private var resizeIdx = 0
    private var orientationIdx = 0
    private var locked = false
    private var curVolume = 0f
    private val hideFeedback = Runnable { feedback.visibility = View.GONE }

    private val subtitlePicker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) addSubtitle(uri)
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        PlayerHub.pause() // pause background music while watching video

        val items = buildItems()
        if (items.first.isEmpty()) { finish(); return }

        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }
        playerView = PlayerView(this).apply {
            useController = true
            controllerShowTimeoutMs = 3000
            setShowSubtitleButton(true)
            setShowNextButton(true)
            setShowPreviousButton(true)
            layoutParams = FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        }
        root.addView(playerView)

        topBar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundColor(0x99000000.toInt())
            setPadding(dp(8), dp(8), dp(8), dp(8))
        }
        titleView = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 14f; maxLines = 1
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        topBar.addView(barButton("←") { finish() })
        topBar.addView(titleView)
        val fitBtn = barButton("Fit") {}
        fitBtn.setOnClickListener {
            val modes = intArrayOf(AspectRatioFrameLayout.RESIZE_MODE_FIT, AspectRatioFrameLayout.RESIZE_MODE_FILL, AspectRatioFrameLayout.RESIZE_MODE_ZOOM)
            val names = arrayOf("Fit", "Fill", "Crop")
            resizeIdx = (resizeIdx + 1) % modes.size
            playerView.resizeMode = modes[resizeIdx]
            fitBtn.text = names[resizeIdx]
        }
        topBar.addView(fitBtn)
        topBar.addView(barButton("Subs") { subtitlePicker.launch(arrayOf("*/*")) })
        val rotBtn = barButton("Auto") {}
        rotBtn.setOnClickListener {
            orientationIdx = (orientationIdx + 1) % 3
            when (orientationIdx) {
                0 -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED; rotBtn.text = "Auto" }
                1 -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE; rotBtn.text = "Land" }
                else -> { requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT; rotBtn.text = "Port" }
            }
        }
        topBar.addView(rotBtn)
        topBar.addView(barButton("PiP") { enterPip() })
        topBar.addView(barButton("Lock") { setLocked(true) })
        root.addView(topBar, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP))

        feedback = TextView(this).apply {
            setTextColor(Color.WHITE); textSize = 22f; visibility = View.GONE
            setBackgroundColor(0xAA000000.toInt()); setPadding(dp(16), dp(8), dp(16), dp(8))
        }
        root.addView(feedback, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER))

        unlockBtn = barButton("🔓 Unlock") { setLocked(false) }.apply {
            visibility = View.GONE
            setBackgroundColor(0x99000000.toInt())
        }
        root.addView(unlockBtn, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END or Gravity.CENTER_VERTICAL))

        setContentView(root)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, root).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        playerView.setControllerVisibilityListener(PlayerView.ControllerVisibilityListener { v ->
            topBar.visibility = if (v == View.VISIBLE && !locked) View.VISIBLE else View.GONE
        })
        setupGestures()

        val p = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MOVIE).build(),
                true
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        player = p
        playerView.player = p
        val start = items.second
        val key = items.first[start].localConfiguration?.uri?.toString() ?: ""
        val resume = Store.videoPos(key)
        p.setMediaItems(items.first, start, if (resume > 5000) resume else 0L)
        p.addListener(object : Player.Listener {
            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                titleView.text = mediaItem?.mediaMetadata?.title ?: ""
            }
        })
        titleView.text = items.first[start].mediaMetadata.title ?: ""
        p.prepare()
        p.playWhenReady = true
    }

    /** Returns (items, startIndex). */
    private fun buildItems(): Pair<List<MediaItem>, Int> {
        val single = intent.getStringExtra("uri")
        if (single != null) {
            val item = MediaItem.Builder().setUri(single)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(intent.getStringExtra("title") ?: "").build())
                .build()
            return Pair(listOf(item), 0)
        }
        val ids = intent.getLongArrayExtra("ids") ?: LongArray(0)
        val titles = Library.video.associate { it.id to it.title }
        val list = ids.map { id ->
            val u = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
            MediaItem.Builder().setMediaId(u.toString()).setUri(u)
                .setMediaMetadata(MediaMetadata.Builder().setTitle(titles[id] ?: "Video").build())
                .build()
        }
        return Pair(list, intent.getIntExtra("index", 0).coerceIn(0, (list.size - 1).coerceAtLeast(0)))
    }

    private fun barButton(label: String, onClick: () -> Unit): Button =
        Button(this).apply {
            text = label; isAllCaps = false; setTextColor(Color.WHITE)
            setBackgroundColor(Color.TRANSPARENT); minWidth = 0; minimumWidth = 0
            setPadding(dp(8), 0, dp(8), 0)
            setOnClickListener { onClick() }
        }

    private fun flash(text: String) {
        feedback.text = text
        feedback.visibility = View.VISIBLE
        feedback.removeCallbacks(hideFeedback)
        feedback.postDelayed(hideFeedback, 700)
    }

    private fun setupGestures() {
        curVolume = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat()
        val detector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (locked) {
                    unlockBtn.visibility = if (unlockBtn.visibility == View.VISIBLE) View.GONE else View.VISIBLE
                } else if (playerView.isControllerFullyVisible) playerView.hideController()
                else playerView.showController()
                return true
            }

            override fun onDoubleTap(e: MotionEvent): Boolean {
                if (locked) return true
                val p = player ?: return true
                val back = e.x < playerView.width / 2f
                val target = if (back) p.currentPosition - 10_000 else p.currentPosition + 10_000
                val dur = if (p.duration > 0) p.duration else Long.MAX_VALUE
                p.seekTo(target.coerceIn(0, dur))
                flash(if (back) "⏪ -10s" else "+10s ⏩")
                return true
            }

            override fun onScroll(e1: MotionEvent?, e2: MotionEvent, distanceX: Float, distanceY: Float): Boolean {
                if (locked || e1 == null) return false
                if (Math.abs(distanceY) < Math.abs(distanceX)) return false
                val frac = distanceY / playerView.height.coerceAtLeast(1)
                if (e1.x < playerView.width / 2f) {
                    val lp = window.attributes
                    val cur = if (lp.screenBrightness < 0) 0.5f else lp.screenBrightness
                    lp.screenBrightness = (cur + frac).coerceIn(0.02f, 1f)
                    window.attributes = lp
                    flash("☀ ${(lp.screenBrightness * 100).roundToInt()}%")
                } else {
                    val max = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
                    curVolume = (curVolume + frac * max).coerceIn(0f, max.toFloat())
                    audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, curVolume.roundToInt(), 0)
                    flash("🔊 ${(curVolume / max * 100).roundToInt()}%")
                }
                return true
            }
        })
        playerView.setOnTouchListener { _, ev -> detector.onTouchEvent(ev); true }
    }

    private fun setLocked(on: Boolean) {
        locked = on
        playerView.useController = !on
        topBar.visibility = View.GONE
        unlockBtn.visibility = if (on) View.VISIBLE else View.GONE
        if (!on) playerView.showController()
    }

    private fun addSubtitle(uri: Uri) {
        val p = player ?: return
        var name = uri.lastPathSegment ?: ""
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) name = c.getString(0) ?: name
        }
        val lower = name.lowercase()
        val mime = when {
            lower.endsWith(".vtt") -> MimeTypes.TEXT_VTT
            lower.endsWith(".ass") || lower.endsWith(".ssa") -> MimeTypes.TEXT_SSA
            else -> MimeTypes.APPLICATION_SUBRIP
        }
        val cur = p.currentMediaItem ?: return
        val sub = MediaItem.SubtitleConfiguration.Builder(uri)
            .setMimeType(mime).setLanguage("und").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build()
        val idx = p.currentMediaItemIndex
        val pos = p.currentPosition
        p.replaceMediaItem(idx, cur.buildUpon().setSubtitleConfigurations(listOf(sub)).build())
        p.seekTo(idx, pos)
        flash("Subtitle loaded")
    }

    private fun enterPip() {
        try {
            enterPictureInPictureMode(PictureInPictureParams.Builder().build())
        } catch (_: Exception) {
            flash("PiP not supported")
        }
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        if (player?.isPlaying == true && !locked) enterPip()
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        playerView.useController = !isInPictureInPictureMode && !locked
        if (isInPictureInPictureMode) topBar.visibility = View.GONE
    }

    private fun saveProgress() {
        val p = player ?: return
        val uri = p.currentMediaItem?.localConfiguration?.uri?.toString() ?: return
        val pos = p.currentPosition
        val done = p.duration > 0 && pos > p.duration - 5000
        Store.saveVideoPos(uri, if (done) 0L else pos)
    }

    override fun onPause() {
        super.onPause()
        saveProgress()
        if (!isInPictureInPictureMode) player?.pause()
    }

    override fun onDestroy() {
        saveProgress()
        if (::playerView.isInitialized) playerView.player = null
        player?.release()
        player = null
        super.onDestroy()
    }

    companion object {
        fun start(ctx: Context, ids: LongArray, index: Int) {
            ctx.startActivity(
                Intent(ctx, VideoPlayerActivity::class.java)
                    .putExtra("ids", ids).putExtra("index", index)
            )
        }

        fun startUri(ctx: Context, uri: String, title: String) {
            ctx.startActivity(
                Intent(ctx, VideoPlayerActivity::class.java)
                    .putExtra("uri", uri).putExtra("title", title)
            )
        }
    }
}
