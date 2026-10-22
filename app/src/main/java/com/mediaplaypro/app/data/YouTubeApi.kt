package com.mediaplaypro.app.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class YtVideo(
    val id: String,
    val title: String,
    val channel: String,
    val duration: String,
    val thumb: String,
)

data class YtResult(val videos: List<YtVideo>, val error: String? = null)

/** Official YouTube Data API v3 (needs a free API key). Only lists videos; playback uses the official embed player. */
object YouTubeApi {
    private const val BASE = "https://www.googleapis.com/youtube/v3"

    suspend fun trending(key: String, region: String): YtResult = withContext(Dispatchers.IO) {
        try {
            val url = "$BASE/videos?part=snippet,contentDetails&chart=mostPopular&videoCategoryId=10" +
                "&regionCode=${enc(region)}&maxResults=30&key=${enc(key)}"
            YtResult(parseVideos(get(url)))
        } catch (e: Exception) {
            YtResult(emptyList(), e.message ?: "Network error")
        }
    }

    suspend fun search(key: String, query: String, region: String): YtResult = withContext(Dispatchers.IO) {
        try {
            val sUrl = "$BASE/search?part=snippet&type=video&videoEmbeddable=true&maxResults=30" +
                "&regionCode=${enc(region)}&q=${enc(query)}&key=${enc(key)}"
            val items = JSONObject(get(sUrl)).optJSONArray("items")
            val ids = ArrayList<String>()
            if (items != null) for (i in 0 until items.length()) {
                items.getJSONObject(i).optJSONObject("id")?.optString("videoId")?.takeIf { it.isNotEmpty() }?.let { ids.add(it) }
            }
            if (ids.isEmpty()) return@withContext YtResult(emptyList())
            val vUrl = "$BASE/videos?part=snippet,contentDetails&id=${ids.joinToString(",")}&key=${enc(key)}"
            YtResult(parseVideos(get(vUrl)))
        } catch (e: Exception) {
            YtResult(emptyList(), e.message ?: "Network error")
        }
    }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8")

    private fun get(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 15000
        c.readTimeout = 15000
        try {
            val code = c.responseCode
            val stream = if (code in 200..299) c.inputStream else c.errorStream
            val body = stream?.bufferedReader()?.use { it.readText() } ?: ""
            if (code !in 200..299) {
                val msg = try {
                    JSONObject(body).getJSONObject("error").getString("message")
                } catch (_: Exception) { "HTTP $code" }
                throw Exception(msg)
            }
            return body
        } finally {
            c.disconnect()
        }
    }

    private fun parseVideos(json: String): List<YtVideo> {
        val out = ArrayList<YtVideo>()
        val items = JSONObject(json).optJSONArray("items") ?: return out
        for (i in 0 until items.length()) {
            val o = items.getJSONObject(i)
            val id = o.optString("id")
            val sn = o.optJSONObject("snippet") ?: continue
            val th = sn.optJSONObject("thumbnails")
            val thumb = th?.optJSONObject("high")?.optString("url")
                ?: th?.optJSONObject("medium")?.optString("url")
                ?: th?.optJSONObject("default")?.optString("url") ?: ""
            out.add(
                YtVideo(
                    id = id,
                    title = sn.optString("title"),
                    channel = sn.optString("channelTitle"),
                    duration = parseDuration(o.optJSONObject("contentDetails")?.optString("duration") ?: ""),
                    thumb = thumb,
                )
            )
        }
        return out
    }

    private fun parseDuration(iso: String): String {
        val m = Regex("PT(?:(\\d+)H)?(?:(\\d+)M)?(?:(\\d+)S)?").matchEntire(iso) ?: return ""
        val h = m.groupValues[1].toIntOrNull() ?: 0
        val min = m.groupValues[2].toIntOrNull() ?: 0
        val s = m.groupValues[3].toIntOrNull() ?: 0
        return if (h > 0) "%d:%02d:%02d".format(h, min, s) else "%d:%02d".format(min, s)
    }
}
