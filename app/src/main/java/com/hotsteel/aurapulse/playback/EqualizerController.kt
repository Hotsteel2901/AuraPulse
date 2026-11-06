package com.hotsteel.aurapulse.playback

import android.content.Context
import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import java.util.Locale
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject

data class EqBand(val index: Int, val frequencyHz: Int, val levelMb: Int)

data class EqPreset(val id: String, val name: String?, val nameRes: Int?, val points: List<Pair<Int, Int>>)

data class EffectState(val supported: Boolean, val enabled: Boolean, val strength: Int)

data class EqualizerState(
  val available: Boolean = false,
  val checked: Boolean = false,
  val enabled: Boolean = false,
  val minimumMb: Int = -1500,
  val maximumMb: Int = 1500,
  val bands: List<EqBand> = emptyList(),
  val presetId: String? = null,
  val customPresets: List<EqPreset> = emptyList(),
  val bass: EffectState = EffectState(false, false, 0),
  val virtualizer: EffectState = EffectState(false, false, 0),
  val loudness: EffectState = EffectState(false, false, 0),
)

/**
 * Wraps the equalizer and the three optional Android audio effects. Everything
 * is capability-gated: an unsupported effect is reported as unavailable instead
 * of being simulated, and no sample-rate or bit-perfect claim is made anywhere.
 */
object EqualizerController {
  private var prefs: android.content.SharedPreferences? = null
  @Volatile private var equalizer: Equalizer? = null
  @Volatile private var bass: BassBoost? = null
  @Volatile private var virtualizer: Virtualizer? = null
  @Volatile private var loudness: LoudnessEnhancer? = null
  @Volatile private var sessionId: Int = 0
  @Volatile private var targetSession: Int = 0
  private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)
  private val lock = Any()
  private var selectedPreset: String? = null
  private var custom: MutableList<EqPreset> = mutableListOf()
  private val handler = android.os.Handler(android.os.Looper.getMainLooper())
  private val pending = mutableMapOf<Int, Int>()
  private val mutable = MutableStateFlow(EqualizerState())
  val state: StateFlow<EqualizerState> = mutable.asStateFlow()

  private val persist = Runnable {
    val edit = prefs?.edit()
    pending.forEach { (frequency, level) -> edit?.putInt("band_$frequency", level) }
    edit?.putString("preset", selectedPreset)
    edit?.apply()
    pending.clear()
  }

  fun initialize(context: Context) {
    if (prefs != null) return
    prefs = context.applicationContext.getSharedPreferences("aurapulse_eq", Context.MODE_PRIVATE)
    custom = readCustom(prefs!!).toMutableList()
  }

  /**
   * Building an AudioEffect is a synchronous binder call into audioserver and
   * can block while the session's effect chain is being set up. Doing it on the
   * main thread froze the player the moment playback started, so the whole
   * attach runs on a background thread; the UI reads [state] instead.
   */
  fun attach(audioSessionId: Int) {
    if (audioSessionId <= 0) return
    if (audioSessionId == targetSession && equalizer != null) return
    targetSession = audioSessionId
    io.launch {
      synchronized(lock) {
        if (targetSession != audioSessionId) return@synchronized
        releaseEffects()
        sessionId = audioSessionId
        build(audioSessionId)
        refresh()
      }
    }
  }

  private fun build(audioSessionId: Int) {
    try {
      val eq = Equalizer(0, audioSessionId)
      equalizer = eq
      val range = eq.bandLevelRange
      val storedPreset = prefs?.getString("preset", null)
      selectedPreset = storedPreset
      for (i in 0 until eq.numberOfBands.toInt()) {
        val freq = eq.getCenterFreq(i.toShort()) / 1000
        val stored = prefs?.getInt("band_$freq", eq.getBandLevel(i.toShort()).toInt())
          ?: eq.getBandLevel(i.toShort()).toInt()
        eq.setBandLevel(i.toShort(), stored.toShort().coerceIn(range[0], range[1]))
      }
      eq.enabled = prefs?.getBoolean("enabled", false) ?: false
    } catch (_: Exception) {
      equalizer = null
    }
    attachEffect()
  }

  private fun attachEffect() {
    if (sessionId <= 0) return
    bass = runCatching { BassBoost(0, sessionId).apply {
      setStrength(prefs?.getInt("bass_strength", 0)?.toShort() ?: 0)
      enabled = prefs?.getBoolean("bass_enabled", false) ?: false
    } }.getOrNull()
    virtualizer = runCatching { Virtualizer(0, sessionId).apply {
      setStrength(prefs?.getInt("virtualizer_strength", 0)?.toShort() ?: 0)
      enabled = prefs?.getBoolean("virtualizer_enabled", false) ?: false
    } }.getOrNull()
    loudness = runCatching { LoudnessEnhancer(sessionId).apply {
      setTargetGain(prefs?.getInt("loudness_gain", 0) ?: 0)
      enabled = prefs?.getBoolean("loudness_enabled", false) ?: false
    } }.getOrNull()
  }

  fun setEnabled(enabled: Boolean) {
    val eq = equalizer ?: return
    runCatching { eq.enabled = enabled }
    prefs?.edit()?.putBoolean("enabled", enabled)?.apply()
    refresh()
  }

  fun setBand(index: Int, levelMb: Int) {
    val eq = equalizer ?: return
    runCatching {
      if (index !in 0 until eq.numberOfBands.toInt()) return
      val range = eq.bandLevelRange
      val level = levelMb.coerceIn(range[0].toInt(), range[1].toInt())
      eq.setBandLevel(index.toShort(), level.toShort())
      val freq = eq.getCenterFreq(index.toShort()) / 1000
      selectedPreset = null
      pending[freq] = level
      handler.removeCallbacks(persist)
      handler.postDelayed(persist, 400)
    }
    refresh()
  }

  fun applyPreset(preset: EqPreset) {
    val eq = equalizer ?: return
    runCatching {
      handler.removeCallbacks(persist)
      if (pending.isNotEmpty()) persist.run()
      val range = eq.bandLevelRange
      for (i in 0 until eq.numberOfBands.toInt()) {
        val freq = eq.getCenterFreq(i.toShort()) / 1000
        val level = interpolate(preset.points, freq).coerceIn(range[0].toInt(), range[1].toInt())
        eq.setBandLevel(i.toShort(), level.toShort())
        prefs?.edit()?.putInt("band_$freq", level)?.apply()
      }
      selectedPreset = preset.id
      prefs?.edit()?.putString("preset", preset.id)?.apply()
    }
    refresh()
  }

  fun saveCustomPreset(name: String): EqPreset {
    val bands = mutable.value.bands.map { it.frequencyHz to it.levelMb }
    val preset = EqPreset("custom_" + System.currentTimeMillis(), name.take(40), null, bands)
    custom = (custom + preset).takeLast(20).toMutableList()
    writeCustom()
    selectedPreset = preset.id
    refresh()
    return preset
  }

  fun deleteCustomPreset(id: String) {
    custom = custom.filterNot { it.id == id }.toMutableList()
    writeCustom()
    if (selectedPreset == id) selectedPreset = null
    refresh()
  }

  fun setBass(enabled: Boolean, strength: Int? = null) {
    val effect = bass ?: return
    runCatching {
      if (strength != null) effect.setStrength(strength.coerceIn(0, 1000).toShort())
      effect.enabled = enabled
    }
    prefs?.edit()?.putBoolean("bass_enabled", enabled)
      ?.putInt("bass_strength", effect.roundedStrength.toInt())?.apply()
    refresh()
  }

  fun setVirtualizer(enabled: Boolean, strength: Int? = null) {
    val effect = virtualizer ?: return
    runCatching {
      if (strength != null) effect.setStrength(strength.coerceIn(0, 1000).toShort())
      effect.enabled = enabled
    }
    prefs?.edit()?.putBoolean("virtualizer_enabled", enabled)?.apply()
    refresh()
  }

  fun setLoudness(enabled: Boolean, gain: Int? = null) {
    val effect = loudness ?: return
    runCatching {
      if (gain != null) effect.setTargetGain(gain.coerceIn(0, 1500))
      effect.enabled = enabled
    }
    prefs?.edit()?.putBoolean("loudness_enabled", enabled)?.putInt("loudness_gain", gain ?: (loudness?.targetGain?.toInt() ?: 0))?.apply()
    refresh()
  }

  fun reset() {
    applyPreset(builtIn.first())
    setBass(false, 0); setVirtualizer(false, 0); setLoudness(false, 0)
  }

  fun releaseEffects() {
    handler.removeCallbacks(persist)
    if (pending.isNotEmpty()) persist.run()
    runCatching { equalizer?.release() }
    runCatching { bass?.release() }
    runCatching { virtualizer?.release() }
    runCatching { loudness?.release() }
    equalizer = null; bass = null; virtualizer = null; loudness = null
    sessionId = 0
    refresh()
  }

  fun release() {
    targetSession = 0
    io.launch {
      synchronized(lock) {
        releaseEffects()
        mutable.value = EqualizerState(checked = true)
      }
    }
  }

  private fun refresh() {
    val eq = equalizer
    if (eq == null) {
      mutable.value = EqualizerState(checked = true, customPresets = custom)
      return
    }
    val state = runCatching {
      val range = eq.bandLevelRange
      EqualizerState(
        available = true, checked = true, enabled = eq.enabled,
        minimumMb = range[0].toInt(), maximumMb = range[1].toInt(),
        bands = (0 until eq.numberOfBands.toInt()).map { i ->
          EqBand(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()).toInt())
        },
        presetId = selectedPreset,
        customPresets = custom,
        bass = bass?.let { EffectState(true, it.enabled, it.roundedStrength.toInt()) } ?: EffectState(false, false, 0),
        virtualizer = virtualizer?.let { EffectState(true, it.enabled, it.roundedStrength.toInt()) } ?: EffectState(false, false, 0),
        loudness = loudness?.let { EffectState(true, it.enabled, it.targetGain.toInt()) } ?: EffectState(false, false, 0),
      )
    }.getOrElse { EqualizerState(checked = true, customPresets = custom) }
    mutable.value = state
  }

  private fun interpolate(points: List<Pair<Int, Int>>, frequency: Int): Int {
    if (points.isEmpty()) return 0
    if (frequency <= points.first().first) return points.first().second
    if (frequency >= points.last().first) return points.last().second
    for (i in 0 until points.lastIndex) {
      val (f1, v1) = points[i]
      val (f2, v2) = points[i + 1]
      if (frequency in f1..f2) {
        val ratio = (frequency - f1).toFloat() / (f2 - f1).coerceAtLeast(1)
        return (v1 + (v2 - v1) * ratio).toInt()
      }
    }
    return 0
  }

  private fun readCustom(prefs: android.content.SharedPreferences): List<EqPreset> = runCatching {
    val array = JSONArray(prefs.getString("custom", "[]"))
    (0 until array.length()).mapNotNull { index ->
      val json = array.optJSONObject(index) ?: return@mapNotNull null
      val points = json.optJSONArray("points") ?: return@mapNotNull null
      val list = (0 until points.length()).mapNotNull { p ->
        points.optJSONArray(p)?.let { pair -> pair.optInt(0) to pair.optInt(1) }
      }
      EqPreset(json.optString("id"), json.optString("name"), null, list)
    }
  }.getOrDefault(emptyList())

  private fun writeCustom() {
    val array = JSONArray()
    custom.forEach { preset ->
      val points = JSONArray()
      preset.points.forEach { (freq, level) -> points.put(JSONArray().put(freq).put(level)) }
      array.put(JSONObject().put("id", preset.id).put("name", preset.name).put("points", points))
    }
    prefs?.edit()?.putString("custom", array.toString())?.apply()
  }

  /** Curves are approximations expressed at reference frequencies, then fitted to the device bands. */
  val builtIn: List<EqPreset> = listOf(
    EqPreset("flat", null, com.hotsteel.aurapulse.R.string.preset_flat, listOf(31 to 0, 1000 to 0, 16000 to 0)),
    EqPreset("acoustic", null, com.hotsteel.aurapulse.R.string.preset_acoustic, listOf(31 to 120, 250 to 80, 1000 to -40, 4000 to 60, 16000 to 100)),
    EqPreset("bass", null, com.hotsteel.aurapulse.R.string.preset_bass, listOf(31 to 420, 62 to 360, 125 to 240, 250 to 100, 1000 to 0, 16000 to -60)),
    EqPreset("vocal", null, com.hotsteel.aurapulse.R.string.preset_vocal, listOf(31 to -120, 250 to -60, 1000 to 140, 3000 to 220, 8000 to 80, 16000 to -40)),
    EqPreset("treble", null, com.hotsteel.aurapulse.R.string.preset_treble, listOf(31 to -100, 1000 to 0, 4000 to 180, 8000 to 280, 16000 to 320)),
    EqPreset("loudness", null, com.hotsteel.aurapulse.R.string.preset_loudness, listOf(31 to 300, 125 to 180, 1000 to -60, 4000 to 60, 16000 to 200)),
    EqPreset("night", null, com.hotsteel.aurapulse.R.string.preset_night, listOf(31 to -60, 250 to 60, 1000 to 120, 4000 to -20, 16000 to -140)),
    EqPreset("podcast", null, com.hotsteel.aurapulse.R.string.preset_podcast, listOf(31 to -160, 125 to -40, 1000 to 160, 3000 to 120, 8000 to -40, 16000 to -120)),
    EqPreset("electronic", null, com.hotsteel.aurapulse.R.string.preset_electronic, listOf(31 to 260, 125 to 120, 1000 to -40, 4000 to 60, 16000 to 180)),
    EqPreset("rock", null, com.hotsteel.aurapulse.R.string.preset_rock, listOf(31 to 200, 250 to -60, 1000 to -100, 4000 to 140, 16000 to 220)),
    EqPreset("jazz", null, com.hotsteel.aurapulse.R.string.preset_jazz, listOf(31 to 160, 250 to 60, 1000 to -80, 4000 to 100, 16000 to 160)),
    EqPreset("classical", null, com.hotsteel.aurapulse.R.string.preset_classical, listOf(31 to -40, 250 to 40, 1000 to -40, 4000 to 40, 16000 to 120)),
  )

  fun bandLabel(frequencyHz: Int): String =
    if (frequencyHz >= 1000) String.format(Locale.US, "%.0fk", frequencyHz / 1000f) else "$frequencyHz"
}
