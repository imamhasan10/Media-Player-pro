package com.mediaplaypro.app.playback

import android.content.ContentValues
import android.content.Context
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import android.media.MediaMuxer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.nio.ByteBuffer

/**
 * Lossless audio trimmer (no re-encoding): copies compressed samples between
 * start and end into an .m4a container. Works for AAC/M4A style sources.
 * MP3/FLAC/OGG cannot be muxed into MP4 without re-encoding on Android.
 */
object AudioTrimmer {
    fun trim(ctx: Context, src: Uri, startMs: Long, endMs: Long, baseName: String): Result<String> {
        val extractor = MediaExtractor()
        val tmp = File(ctx.cacheDir, "trim_${System.currentTimeMillis()}.m4a")
        var muxer: MediaMuxer? = null
        var started = false
        return try {
            extractor.setDataSource(ctx, src, null)
            var track = -1
            for (i in 0 until extractor.trackCount) {
                val mime = extractor.getTrackFormat(i).getString(MediaFormat.KEY_MIME) ?: ""
                if (mime.startsWith("audio/")) { track = i; break }
            }
            if (track < 0) return Result.failure(Exception("No audio track found"))
            extractor.selectTrack(track)
            val format = extractor.getTrackFormat(track)
            val mime = format.getString(MediaFormat.KEY_MIME) ?: ""
            if (mime != "audio/mp4a-latm") {
                return Result.failure(
                    Exception("Lossless trim supports AAC/M4A only (this file is $mime).")
                )
            }
            muxer = MediaMuxer(tmp.absolutePath, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4)
            val out = muxer.addTrack(format)
            muxer.start()
            started = true

            val startUs = startMs * 1000
            val endUs = endMs * 1000
            extractor.seekTo(startUs, MediaExtractor.SEEK_TO_CLOSEST_SYNC)
            val buf = ByteBuffer.allocate(1 shl 20)
            val info = MediaCodec.BufferInfo()
            while (true) {
                buf.clear()
                val size = extractor.readSampleData(buf, 0)
                if (size < 0) break
                val t = extractor.sampleTime
                if (t > endUs) break
                info.offset = 0
                info.size = size
                info.presentationTimeUs = (t - startUs).coerceAtLeast(0)
                info.flags = if ((extractor.sampleFlags and MediaExtractor.SAMPLE_FLAG_SYNC) != 0)
                    MediaCodec.BUFFER_FLAG_KEY_FRAME else 0
                muxer.writeSampleData(out, buf, info)
                extractor.advance()
            }
            muxer.stop()
            started = false
            muxer.release()
            muxer = null
            Result.success(saveToMusic(ctx, tmp, baseName))
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            try { if (started) muxer?.stop() } catch (_: Exception) {}
            try { muxer?.release() } catch (_: Exception) {}
            extractor.release()
            tmp.delete()
        }
    }

    private fun saveToMusic(ctx: Context, file: File, baseName: String): String {
        val safe = baseName.replace(Regex("[\\\\/:*?\"<>|]"), "_").ifBlank { "trimmed" }
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$safe.m4a")
                put(MediaStore.Audio.Media.MIME_TYPE, "audio/mp4")
                put(MediaStore.Audio.Media.RELATIVE_PATH, "Music/MediaPlayPro")
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val cr = ctx.contentResolver
            val uri = cr.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values)
                ?: throw Exception("Could not create output file")
            cr.openOutputStream(uri)!!.use { o -> file.inputStream().use { it.copyTo(o) } }
            values.clear()
            values.put(MediaStore.Audio.Media.IS_PENDING, 0)
            cr.update(uri, values, null, null)
            "Music/MediaPlayPro/$safe.m4a"
        } else {
            val dir = ctx.getExternalFilesDir(Environment.DIRECTORY_MUSIC) ?: ctx.filesDir
            val dest = File(dir, "$safe.m4a")
            file.copyTo(dest, overwrite = false)
            dest.absolutePath
        }
    }
}
