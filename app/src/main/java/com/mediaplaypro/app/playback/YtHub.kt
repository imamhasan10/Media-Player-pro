package com.mediaplaypro.app.playback

import android.annotation.SuppressLint
import android.content.Context
import android.content.MutableContextWrapper
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.mediaplaypro.app.data.YtVideo

/**
 * Holds the single YouTube player (official IFrame embed inside a WebView) and its state.
 * One WebView instance is reused so the video keeps playing while it moves
 * between the expanded view, the mini player and Picture-in-Picture.
 */
object YtHub {
    var list by mutableStateOf<List<YtVideo>>(emptyList())
    var index by mutableIntStateOf(0)
    var current by mutableStateOf<YtVideo?>(null)
    var lastTitle: String = ""
    var playing by mutableStateOf(false)
    var expanded by mutableStateOf(true)
    var inPip by mutableStateOf(false)
    var error by mutableStateOf<Int?>(null)

    private var ready = false
    private var web: WebView? = null
    private val main = Handler(Looper.getMainLooper())

    private const val HTML = """
<!DOCTYPE html><html><head>
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>html,body{margin:0;padding:0;height:100%;background:#000;overflow:hidden}#p{width:100%;height:100%}</style>
</head><body><div id="p"></div>
<script src="https://www.youtube.com/iframe_api"></script>
<script>
var player;
function onYouTubeIframeAPIReady(){
  player = new YT.Player('p', {
    width:'100%', height:'100%',
    playerVars:{autoplay:1, playsinline:1, rel:0, fs:0, modestbranding:1, origin:'https://www.youtube.com'},
    events:{
      onReady:function(){ Android.onReady(); },
      onStateChange:function(e){ Android.onState(e.data); },
      onError:function(e){ Android.onError(e.data); }
    }
  });
}
function load(id){ if(player && player.loadVideoById){ player.loadVideoById(id); } }
</script></body></html>
"""

    private class Bridge {
        @JavascriptInterface fun onReady() { main.post { ready = true; loadCurrent() } }
        @JavascriptInterface fun onState(s: Int) { main.post { handleState(s) } }
        @JavascriptInterface fun onError(c: Int) { main.post { error = c } }
    }

    @SuppressLint("SetJavaScriptEnabled")
    fun webView(ctx: Context): WebView {
        val existing = web
        if (existing != null) {
            (existing.context as? MutableContextWrapper)?.baseContext = ctx
            (existing.parent as? ViewGroup)?.removeView(existing)
            return existing
        }
        val wv = WebView(MutableContextWrapper(ctx))
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        wv.settings.mediaPlaybackRequiresUserGesture = false
        wv.setBackgroundColor(Color.BLACK)
        wv.webChromeClient = WebChromeClient()
        wv.addJavascriptInterface(Bridge(), "Android")
        wv.loadDataWithBaseURL("https://www.youtube.com", HTML, "text/html", "utf-8", null)
        web = wv
        return wv
    }

    /** Called when the activity goes away so we don't leak it. */
    fun release(appCtx: Context) {
        val wv = web ?: return
        (wv.parent as? ViewGroup)?.removeView(wv)
        (wv.context as? MutableContextWrapper)?.baseContext = appCtx
    }

    private fun js(code: String) {
        web?.evaluateJavascript(code, null)
    }

    private fun handleState(s: Int) {
        playing = s == 1
        if (s == 0) next() // ended -> next video in list
    }

    fun open(videos: List<YtVideo>, i: Int) {
        if (videos.isEmpty()) return
        PlayerHub.pause() // stop local music
        list = videos
        index = i.coerceIn(0, videos.size - 1)
        current = videos[index]
        lastTitle = videos[index].title
        expanded = true
        loadCurrent()
    }

    private fun loadCurrent() {
        val v = current ?: return
        error = null
        if (ready) js("load('${v.id}')")
    }

    fun togglePlay() {
        if (playing) js("player.pauseVideo()") else js("player.playVideo()")
    }

    fun next() {
        if (index + 1 < list.size) {
            index += 1; current = list[index]; lastTitle = list[index].title; loadCurrent()
        }
    }

    fun previous() {
        if (index > 0) {
            index -= 1; current = list[index]; lastTitle = list[index].title; loadCurrent()
        }
    }

    fun close() {
        js("player.pauseVideo()")
        current = null
        playing = false
        error = null
    }
}
