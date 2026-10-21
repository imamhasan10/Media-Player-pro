package com.mediaplaypro.app.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import org.json.JSONArray
import org.json.JSONObject

/** Small persistent store: settings, favorites, history, play counts, playlists. */
object Store {
    private var sp: SharedPreferences? = null

    var themeMode by mutableIntStateOf(0) // 0 system, 1 light, 2 dark
    var wifiOnly by mutableStateOf(false)
    var favorites by mutableStateOf<Set<Long>>(emptySet())
    var recent by mutableStateOf<List<Long>>(emptyList())
    var playCounts by mutableStateOf<Map<Long, Int>>(emptyMap())
    var playlists by mutableStateOf<Map<String, List<Long>>>(emptyMap())

    fun init(ctx: Context) {
        val p = ctx.getSharedPreferences("mpp", Context.MODE_PRIVATE)
        sp = p
        themeMode = p.getInt("theme", 0)
        wifiOnly = p.getBoolean("wifi", false)
        favorites = parseLongs(p.getString("fav", "")).toSet()
        recent = parseLongs(p.getString("recent", ""))
        val counts = HashMap<Long, Int>()
        for (part in (p.getString("counts", "") ?: "").split(",")) {
            val kv = part.split(":")
            if (kv.size == 2) {
                val k = kv[0].toLongOrNull()
                val v = kv[1].toIntOrNull()
                if (k != null && v != null) counts[k] = v
            }
        }
        playCounts = counts
        val pl = LinkedHashMap<String, List<Long>>()
        try {
            val obj = JSONObject(p.getString("playlists", "{}") ?: "{}")
            for (name in obj.keys()) {
                val arr = obj.getJSONArray(name)
                pl[name] = List(arr.length()) { i -> arr.getLong(i) }
            }
        } catch (_: Exception) {
        }
        playlists = pl
    }

    private fun parseLongs(s: String?): List<Long> =
        (s ?: "").split(",").mapNotNull { it.toLongOrNull() }

    private fun edit(block: SharedPreferences.Editor.() -> Unit) {
        val e = sp?.edit() ?: return
        e.block()
        e.apply()
    }

    fun setTheme(mode: Int) {
        themeMode = mode
        edit { putInt("theme", mode) }
    }

    fun setWifiOnly(v: Boolean) {
        wifiOnly = v
        edit { putBoolean("wifi", v) }
    }

    fun toggleFavorite(id: Long) {
        favorites = if (id in favorites) favorites - id else favorites + id
        edit { putString("fav", favorites.joinToString(",")) }
    }

    fun recordPlay(id: Long) {
        if (id < 0) return
        recent = (listOf(id) + recent.filter { it != id }).take(50)
        playCounts = playCounts + (id to ((playCounts[id] ?: 0) + 1))
        edit {
            putString("recent", recent.joinToString(","))
            putString("counts", playCounts.entries.joinToString(",") { "${it.key}:${it.value}" })
        }
    }

    fun clearHistory() {
        recent = emptyList()
        playCounts = emptyMap()
        edit { remove("recent"); remove("counts") }
    }

    // ---- playlists ----
    private fun savePlaylists() {
        val obj = JSONObject()
        for ((n, ids) in playlists) obj.put(n, JSONArray(ids))
        edit { putString("playlists", obj.toString()) }
    }

    fun createPlaylist(name: String) {
        val n = name.trim()
        if (n.isEmpty() || playlists.containsKey(n)) return
        playlists = playlists + (n to emptyList())
        savePlaylists()
    }

    fun deletePlaylist(name: String) {
        playlists = playlists - name
        savePlaylists()
    }

    fun addToPlaylist(name: String, id: Long) {
        val cur = playlists[name] ?: return
        if (id in cur) return // duplicate detection
        playlists = playlists + (name to (cur + id))
        savePlaylists()
    }

    fun removeFromPlaylist(name: String, id: Long) {
        val cur = playlists[name] ?: return
        playlists = playlists + (name to cur.filter { it != id })
        savePlaylists()
    }

    // ---- video resume positions ----
    fun videoPos(key: String): Long = sp?.getLong("vp_$key", 0L) ?: 0L

    fun saveVideoPos(key: String, pos: Long) {
        edit { putLong("vp_$key", pos) }
    }
}
