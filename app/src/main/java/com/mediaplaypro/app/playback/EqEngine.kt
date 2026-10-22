package com.mediaplaypro.app.playback

import android.content.Context
import android.content.SharedPreferences
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * One shared audio-effects engine, attached to the playback service's audio session.
 * Band count/range come from the device (usually 5 bands).
 */
object EqEngine {
    private var eq: Equalizer? = null
    private var bass: BassBoost? = null
    private var virt: Virtualizer? = null
    private var loud: LoudnessEnhancer? = null
    private var sp: SharedPreferences? = null

    var available by mutableStateOf(false)
    var enabled by mutableStateOf(false)
    var minLevel by mutableIntStateOf(-1500)
    var maxLevel by mutableIntStateOf(1500)
    var freqs by mutableStateOf<List<Int>>(emptyList())
    var levels by mutableStateOf<List<Int>>(emptyList())
    var presets by mutableStateOf<List<String>>(emptyList())
    var bassStrength by mutableIntStateOf(0)
    var virtStrength by mutableIntStateOf(0)
    var loudGain by mutableIntStateOf(0)
    var bassSupported by mutableStateOf(false)
    var virtSupported by mutableStateOf(false)

    fun attach(ctx: Context, session: Int) {
        if (session == 0) return
        val p = ctx.getSharedPreferences("mpp_eq", Context.MODE_PRIVATE)
        sp = p
        release()
        enabled = p.getBoolean("on", false)
        try {
            val e = Equalizer(0, session)
            eq = e
            val range = e.bandLevelRange
            minLevel = range[0].toInt()
            maxLevel = range[1].toInt()
            val n = e.numberOfBands.toInt()
            freqs = (0 until n).map { e.getCenterFreq(it.toShort()) / 1000 }
            presets = (0 until e.numberOfPresets.toInt()).map { e.getPresetName(it.toShort()) }
            val saved = p.getString("levels", null)?.split(",")?.mapNotNull { it.toIntOrNull() }
            levels = if (saved != null && saved.size == n) saved else List(n) { 0 }
            levels.forEachIndexed { i, v ->
                e.setBandLevel(i.toShort(), v.coerceIn(minLevel, maxLevel).toShort())
            }
            e.enabled = enabled
            available = true
        } catch (_: Throwable) {
            available = false
        }
        try {
            val b = BassBoost(0, session)
            bass = b
            bassSupported = b.strengthSupported
            bassStrength = p.getInt("bass", 0)
            if (bassSupported) b.setStrength(bassStrength.toShort())
            b.enabled = enabled
        } catch (_: Throwable) {
            bassSupported = false
        }
        try {
            val v = Virtualizer(0, session)
            virt = v
            virtSupported = v.strengthSupported
            virtStrength = p.getInt("virt", 0)
            if (virtSupported) v.setStrength(virtStrength.toShort())
            v.enabled = enabled
        } catch (_: Throwable) {
            virtSupported = false
        }
        try {
            val l = LoudnessEnhancer(session)
            loud = l
            loudGain = p.getInt("loud", 0)
            l.setTargetGain(loudGain)
            l.enabled = enabled
        } catch (_: Throwable) {
        }
    }

    fun release() {
        try { eq?.release() } catch (_: Throwable) {}
        try { bass?.release() } catch (_: Throwable) {}
        try { virt?.release() } catch (_: Throwable) {}
        try { loud?.release() } catch (_: Throwable) {}
        eq = null; bass = null; virt = null; loud = null
        available = false
    }

    private fun persist() {
        sp?.edit()
            ?.putBoolean("on", enabled)
            ?.putString("levels", levels.joinToString(","))
            ?.putInt("bass", bassStrength)
            ?.putInt("virt", virtStrength)
            ?.putInt("loud", loudGain)
            ?.apply()
    }

    fun updateEnabled(on: Boolean) {
        enabled = on
        try { eq?.enabled = on } catch (_: Throwable) {}
        try { bass?.enabled = on } catch (_: Throwable) {}
        try { virt?.enabled = on } catch (_: Throwable) {}
        try { loud?.enabled = on } catch (_: Throwable) {}
        persist()
    }

    fun setBand(i: Int, level: Int) {
        val v = level.coerceIn(minLevel, maxLevel)
        try { eq?.setBandLevel(i.toShort(), v.toShort()) } catch (_: Throwable) {}
        levels = levels.toMutableList().also { if (i in it.indices) it[i] = v }
        persist()
    }

    fun usePreset(index: Int) {
        val e = eq ?: return
        try {
            e.usePreset(index.toShort())
            levels = (0 until levels.size).map { e.getBandLevel(it.toShort()).toInt() }
        } catch (_: Throwable) {}
        persist()
    }

    fun reset() {
        levels.indices.forEach { try { eq?.setBandLevel(it.toShort(), 0) } catch (_: Throwable) {} }
        levels = List(levels.size) { 0 }
        setBass(0); setVirt(0); setLoud(0)
        persist()
    }

    fun setBass(v: Int) {
        bassStrength = v
        try { if (bassSupported) bass?.setStrength(v.toShort()) } catch (_: Throwable) {}
        persist()
    }

    fun setVirt(v: Int) {
        virtStrength = v
        try { if (virtSupported) virt?.setStrength(v.toShort()) } catch (_: Throwable) {}
        persist()
    }

    /** Loudness boost in millibels (0..1000). Capped to avoid clipping. */
    fun setLoud(v: Int) {
        loudGain = v.coerceIn(0, 1000)
        try { loud?.setTargetGain(loudGain) } catch (_: Throwable) {}
        persist()
    }
}
