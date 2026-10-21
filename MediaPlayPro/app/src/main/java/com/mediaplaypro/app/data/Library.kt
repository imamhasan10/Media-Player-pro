package com.mediaplaypro.app.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class Media(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val durationMs: Long,
    val size: Long,
    val dateAdded: Long,
    val isVideo: Boolean,
    val mime: String? = null,
) {
    /** Thumbnail: album art for audio, frame for video. */
    val thumb: Uri
        get() = if (isVideo) uri else ContentUris.withAppendedId(
            Uri.parse("content://media/external/audio/albumart"), albumId
        )
}

/** Single unified media library (audio + video) shared by every screen. */
object Library {
    var audio by mutableStateOf<List<Media>>(emptyList())
        private set
    var video by mutableStateOf<List<Media>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set

    suspend fun refresh(ctx: Context) {
        loading = true
        try {
            val cr = ctx.applicationContext.contentResolver
            val a = withContext(Dispatchers.IO) { queryAudio(cr) }
            val v = withContext(Dispatchers.IO) { queryVideo(cr) }
            audio = a
            video = v
        } finally {
            loading = false
        }
    }

    private fun queryAudio(cr: ContentResolver): List<Media> {
        val out = ArrayList<Media>()
        val base = MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.SIZE,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.MIME_TYPE,
        )
        val sel = "${MediaStore.Audio.Media.IS_MUSIC} != 0"
        cr.query(base, proj, sel, null, "${MediaStore.Audio.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val artist = c.getString(2)?.takeIf { it != "<unknown>" } ?: "Unknown artist"
                out.add(
                    Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        title = c.getString(1) ?: "Unknown",
                        artist = artist,
                        album = c.getString(3) ?: "",
                        albumId = c.getLong(4),
                        durationMs = c.getLong(5),
                        size = c.getLong(6),
                        dateAdded = c.getLong(7),
                        isVideo = false,
                        mime = c.getString(8),
                    )
                )
            }
        }
        return out
    }

    private fun queryVideo(cr: ContentResolver): List<Media> {
        val out = ArrayList<Media>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        val proj = arrayOf(
            MediaStore.Video.Media._ID,
            MediaStore.Video.Media.DISPLAY_NAME,
            MediaStore.Video.Media.DURATION,
            MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DATE_ADDED,
            MediaStore.Video.Media.MIME_TYPE,
        )
        cr.query(base, proj, null, null, "${MediaStore.Video.Media.DATE_ADDED} DESC")?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                out.add(
                    Media(
                        id = id,
                        uri = ContentUris.withAppendedId(base, id),
                        title = (c.getString(1) ?: "Video").substringBeforeLast('.'),
                        artist = "",
                        album = "",
                        albumId = 0,
                        durationMs = c.getLong(2),
                        size = c.getLong(3),
                        dateAdded = c.getLong(4),
                        isVideo = true,
                        mime = c.getString(5),
                    )
                )
            }
        }
        return out
    }
}

fun fmtTime(ms: Long): String {
    val s = (ms / 1000).coerceAtLeast(0)
    val h = s / 3600
    val m = (s % 3600) / 60
    val sec = s % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, sec) else "%d:%02d".format(m, sec)
}

fun fmtSize(bytes: Long): String {
    if (bytes <= 0) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB")
    var v = bytes.toDouble()
    var i = 0
    while (v >= 1024 && i < units.size - 1) {
        v /= 1024; i++
    }
    return "%.1f %s".format(v, units[i])
}
