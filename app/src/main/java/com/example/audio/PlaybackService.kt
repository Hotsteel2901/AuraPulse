package com.example.audio

import android.content.Intent
import android.media.audiofx.Equalizer
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Real media playback, audio focus and a system media session, even in the background. */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class PlaybackService : MediaSessionService() {
  private var session: MediaSession? = null
  private var player: ExoPlayer? = null

  override fun onCreate() {
    super.onCreate()
    DeviceEqualizer.initialize(applicationContext)
    player = ExoPlayer.Builder(this)
      .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
        true,
      )
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .setHandleAudioBecomingNoisy(true)
      .build().also { exo ->
        exo.addListener(object : Player.Listener {
          override fun onAudioSessionIdChanged(audioSessionId: Int) {
            DeviceEqualizer.attach(audioSessionId)
          }
        })
        if (exo.audioSessionId != C.AUDIO_SESSION_ID_UNSET) DeviceEqualizer.attach(exo.audioSessionId)
        session = MediaSession.Builder(this, exo).build()
      }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    // Keep playing after the activity is swiped away; stop an idle service.
    if (player?.playWhenReady != true) stopSelf()
  }

  override fun onDestroy() {
    DeviceEqualizer.release()
    session?.release()
    session = null
    player?.release()
    player = null
    super.onDestroy()
  }
}

data class EqualizerBand(val index: Int, val frequencyHz: Int, val levelMb: Int)
data class EqualizerState(
  val available: Boolean = false,
  val checked: Boolean = false,
  val enabled: Boolean = false,
  val minimumMb: Int = 0,
  val maximumMb: Int = 0,
  val bands: List<EqualizerBand> = emptyList(),
)

/** Only reports actual Android audio effect capabilities; no simulated DSP or bit-perfect claims. */
object DeviceEqualizer {
  private var effect: Equalizer? = null
  private var preferences: android.content.SharedPreferences? = null
  private val mutableState = MutableStateFlow(EqualizerState())
  val state: StateFlow<EqualizerState> = mutableState.asStateFlow()

  fun initialize(context: android.content.Context) {
    preferences = context.getSharedPreferences("device_equalizer", android.content.Context.MODE_PRIVATE)
  }

  fun attach(audioSessionId: Int) {
    if (audioSessionId <= 0) return
    release()
    try {
      val eq = Equalizer(0, audioSessionId)
      effect = eq
      val range = eq.bandLevelRange
      for (i in 0 until eq.numberOfBands.toInt()) {
        val frequency = eq.getCenterFreq(i.toShort()) / 1000
        val stored = preferences?.getInt("band_$frequency", eq.getBandLevel(i.toShort()).toInt())
          ?: eq.getBandLevel(i.toShort()).toInt()
        eq.setBandLevel(i.toShort(), stored.coerceIn(range[0].toInt(), range[1].toInt()).toShort())
      }
      eq.enabled = preferences?.getBoolean("enabled", false) ?: false
      refresh()
    } catch (_: Exception) {
      runCatching { effect?.release() }
      effect = null
      mutableState.value = EqualizerState(checked = true)
    }
  }

  fun setEnabled(enabled: Boolean) {
    val eq = effect ?: return
    try {
      eq.enabled = enabled
      preferences?.edit()?.putBoolean("enabled", enabled)?.apply()
      refresh()
    } catch (_: Exception) { refresh() }
  }

  fun setBand(index: Int, levelMb: Int) {
    val eq = effect ?: return
    try {
      if (index !in 0 until eq.numberOfBands.toInt()) return
      val range = eq.bandLevelRange
      val level = levelMb.coerceIn(range[0].toInt(), range[1].toInt())
      eq.setBandLevel(index.toShort(), level.toShort())
      val frequency = eq.getCenterFreq(index.toShort()) / 1000
      preferences?.edit()?.putInt("band_$frequency", level)?.apply()
      refresh()
    } catch (_: Exception) { refresh() }
  }

  fun reset() {
    state.value.bands.forEach { setBand(it.index, 0) }
  }

  private fun refresh() {
    val eq = effect ?: return
    try {
      val range = eq.bandLevelRange
      mutableState.value = EqualizerState(
        available = true, checked = true, enabled = eq.enabled,
        minimumMb = range[0].toInt(), maximumMb = range[1].toInt(),
        bands = (0 until eq.numberOfBands.toInt()).map { i ->
          EqualizerBand(i, eq.getCenterFreq(i.toShort()) / 1000, eq.getBandLevel(i.toShort()).toInt())
        },
      )
    } catch (_: Exception) {
      mutableState.value = EqualizerState(checked = true)
    }
  }

  fun release() {
    runCatching { effect?.release() }
    effect = null
    mutableState.value = EqualizerState()
  }
}
