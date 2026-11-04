package com.hotsteel.aurapulse.playback

import androidx.media3.common.PlaybackParameters
import androidx.media3.exoplayer.ExoPlayer
import com.hotsteel.aurapulse.core.fmt
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class EnhancementState(
  val speed: Float = 1f,
  val pitch: Float = 1f,
  val volume: Float = 1f,
  val skipSilence: Boolean = false,
  val available: Boolean = false,
  val loopStartMs: Long? = null,
  val loopEndMs: Long? = null,
) {
  val looping: Boolean get() = loopStartMs != null && loopEndMs != null
}

/**
 * Real player-side enhancements: PlaybackParameters, player volume, silence
 * skipping and an A–B loop enforced by the service. Nothing is faked when the
 * decoder refuses a feature.
 */
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackEnhancements {
  private var player: ExoPlayer? = null
  private val mutable = MutableStateFlow(EnhancementState())
  val state: StateFlow<EnhancementState> = mutable.asStateFlow()

  fun attach(player: ExoPlayer) {
    this.player = player
    runCatching { player.skipSilenceEnabled = mutable.value.skipSilence }
    applyParameters()
  }

  fun detach() {
    player = null
    mutable.value = mutable.value.copy(available = false, loopStartMs = null, loopEndMs = null)
  }

  fun setSpeed(value: Float) {
    mutable.value = mutable.value.copy(speed = value.coerceIn(0.25f, 3f))
    applyParameters()
  }

  fun setPitch(value: Float) {
    mutable.value = mutable.value.copy(pitch = value.coerceIn(0.5f, 2f))
    applyParameters()
  }

  fun setVolume(value: Float) {
    val volume = value.coerceIn(0f, 1f)
    mutable.value = mutable.value.copy(volume = volume)
    player?.volume = volume
  }

  fun setSkipSilence(enabled: Boolean) {
    val accepted = runCatching {
      player?.skipSilenceEnabled = enabled
      player?.skipSilenceEnabled == enabled
    }.getOrDefault(false)
    mutable.value = mutable.value.copy(skipSilence = accepted && enabled)
  }

  fun setLoopStart(positionMs: Long): Boolean {
    val state = mutable.value
    val clean = positionMs.coerceAtLeast(0)
    mutable.value = if (state.loopEndMs != null && clean >= state.loopEndMs) {
      state.copy(loopStartMs = clean, loopEndMs = null)
    } else {
      state.copy(loopStartMs = clean)
    }
    return true
  }

  fun setLoopEnd(positionMs: Long): Boolean {
    val state = mutable.value
    val start = state.loopStartMs ?: return false
    val end = positionMs.coerceAtLeast(0)
    if (end <= start + 500) return false
    mutable.value = state.copy(loopEndMs = end)
    return true
  }

  fun clearLoop() {
    mutable.value = mutable.value.copy(loopStartMs = null, loopEndMs = null)
  }

  /** Returns a seek target when the loop wrapped, so the service can seek. */
  fun loopTarget(positionMs: Long): Long? {
    val state = mutable.value
    val end = state.loopEndMs ?: return null
    val start = state.loopStartMs ?: return null
    return if (positionMs >= end || positionMs < start - 1500) start else null
  }

  fun reset() {
    setSpeed(1f); setPitch(1f); setVolume(1f); setSkipSilence(false); clearLoop()
  }

  fun describeLoop(): String? {
    val state = mutable.value
    val start = state.loopStartMs ?: return null
    val end = state.loopEndMs ?: return null
    return "${fmt.duration(start)} – ${fmt.duration(end)}"
  }

  private fun applyParameters() {
    val state = mutable.value
    runCatching { player?.playbackParameters = PlaybackParameters(state.speed, state.pitch) }
    mutable.value = state.copy(available = player != null)
  }
}
