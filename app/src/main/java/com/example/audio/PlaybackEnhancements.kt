package com.example.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

internal fun remainingSleepSeconds(deadlineMs: Long, nowMs: Long): Long =
  if (nowMs >= deadlineMs) 0L else (deadlineMs - nowMs + 999L) / 1000L

internal fun shouldLoop(positionMs: Long, startMs: Long?, endMs: Long?): Boolean =
  startMs != null && endMs != null && endMs >= startMs + 500L && positionMs >= endMs

/** Transient controls live with the service, not the screen, so they keep working in background. */
data class PlaybackEnhancementState(
  val available: Boolean = false,
  val speed: Float = 1f,
  val pitch: Float = 1f,
  val volume: Float = 1f,
  val skipSilence: Boolean = false,
  val sleepRemainingSeconds: Long? = null,
  val sleepAtTrackEnd: Boolean = false,
  val loopStartMs: Long? = null,
  val loopEndMs: Long? = null,
)

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
object PlaybackEnhancements {
  private val handler = Handler(Looper.getMainLooper())
  private val mutableState = MutableStateFlow(PlaybackEnhancementState())
  val state: StateFlow<PlaybackEnhancementState> = mutableState.asStateFlow()
  private var player: ExoPlayer? = null
  private var prefs: android.content.SharedPreferences? = null
  private var deadline: Long? = null
  private var loopMediaId: String? = null
  private val persistSettings = Runnable {
    val current = mutableState.value
    prefs?.edit()?.putFloat("speed", current.speed)?.putFloat("pitch", current.pitch)
      ?.putFloat("volume", current.volume)?.putBoolean("skip_silence", current.skipSilence)?.apply()
  }
  private fun persistSoon() { handler.removeCallbacks(persistSettings); handler.postDelayed(persistSettings, 500L) }

  private val listener = object : Player.Listener {
    override fun onIsPlayingChanged(isPlaying: Boolean) = schedule()

    override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
      mutableState.update { it.copy(speed = playbackParameters.speed, pitch = playbackParameters.pitch) }
      persistSoon()
    }

    override fun onVolumeChanged(volume: Float) {
      mutableState.update { it.copy(volume = volume) }
      persistSoon()
    }

    override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
      if (mutableState.value.sleepAtTrackEnd &&
        (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
        player?.pause()
        mutableState.update { it.copy(sleepAtTrackEnd = false) }
      }
      if (loopMediaId != null && (loopMediaId != mediaItem?.mediaId || reason == Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT)) {
        clearLoop()
      }
      schedule()
    }

    override fun onPlaybackStateChanged(playbackState: Int) {
      if (playbackState == Player.STATE_ENDED && mutableState.value.sleepAtTrackEnd) {
        mutableState.update { it.copy(sleepAtTrackEnd = false) }
      }
      schedule()
    }
  }

  private val ticker = object : Runnable {
    override fun run() {
      val current = player ?: return
      val now = SystemClock.elapsedRealtime()
      deadline?.let { end ->
        if (now >= end) {
          deadline = null
          current.pause()
          mutableState.update { it.copy(sleepRemainingSeconds = null) }
        } else {
          val remaining = remainingSleepSeconds(end, now)
          mutableState.update { if (it.sleepRemainingSeconds != remaining) it.copy(sleepRemainingSeconds = remaining) else it }
        }
      }
      val loop = mutableState.value
      if (current.isPlaying && current.isCurrentMediaItemSeekable &&
        current.currentMediaItem?.mediaId == loopMediaId && shouldLoop(current.currentPosition, loop.loopStartMs, loop.loopEndMs)) {
        current.seekTo(loop.loopStartMs!!)
      }
      schedule()
    }
  }

  fun attach(context: Context, exo: ExoPlayer) {
    detach()
    prefs = context.getSharedPreferences("playback_enhancements", Context.MODE_PRIVATE)
    val speed = (prefs?.getFloat("speed", 1f) ?: 1f).coerceIn(0.5f, 2f)
    val pitch = (prefs?.getFloat("pitch", 1f) ?: 1f).coerceIn(0.5f, 2f)
    val volume = (prefs?.getFloat("volume", 1f) ?: 1f).coerceIn(0f, 1f)
    val silence = prefs?.getBoolean("skip_silence", false) ?: false
    player = exo
    exo.playbackParameters = PlaybackParameters(speed, pitch)
    exo.volume = volume
    exo.skipSilenceEnabled = silence
    mutableState.value = PlaybackEnhancementState(available = true, speed = speed, pitch = pitch, volume = volume, skipSilence = silence)
    exo.addListener(listener)
  }

  fun setSpeed(speed: Float) {
    val exo = player ?: return
    val value = speed.coerceIn(0.5f, 2f)
    exo.playbackParameters = PlaybackParameters(value, mutableState.value.pitch)
    mutableState.update { it.copy(speed = value) }
    persistSoon()
  }

  fun setPitch(pitch: Float) {
    val exo = player ?: return
    val value = pitch.coerceIn(0.5f, 2f)
    exo.playbackParameters = PlaybackParameters(mutableState.value.speed, value)
    mutableState.update { it.copy(pitch = value) }
    persistSoon()
  }

  fun setVolume(volume: Float) {
    val exo = player ?: return
    val value = volume.coerceIn(0f, 1f)
    exo.volume = value
    mutableState.update { it.copy(volume = value) }
    persistSoon()
  }

  fun setSkipSilence(enabled: Boolean) {
    val exo = player ?: return
    exo.skipSilenceEnabled = enabled
    mutableState.update { it.copy(skipSilence = enabled) }
    persistSoon()
  }

  fun sleepAfter(minutes: Int) {
    if (player == null || minutes !in 1..240) return
    deadline = SystemClock.elapsedRealtime() + minutes * 60_000L
    mutableState.update { it.copy(sleepRemainingSeconds = minutes * 60L, sleepAtTrackEnd = false) }
    schedule()
  }

  fun sleepAfterCurrentTrack() {
    if (player?.currentMediaItem == null) return
    deadline = null
    mutableState.update { it.copy(sleepRemainingSeconds = null, sleepAtTrackEnd = true) }
    schedule()
  }

  fun cancelSleep() {
    deadline = null
    mutableState.update { it.copy(sleepRemainingSeconds = null, sleepAtTrackEnd = false) }
    schedule()
  }

  fun setLoopStart(): Boolean {
    val exo = player ?: return false
    if (!exo.isCurrentMediaItemSeekable || exo.currentMediaItem == null) return false
    loopMediaId = exo.currentMediaItem?.mediaId
    mutableState.update { it.copy(loopStartMs = exo.currentPosition.coerceAtLeast(0), loopEndMs = null) }
    schedule()
    return true
  }

  fun setLoopEnd(): Boolean {
    val exo = player ?: return false
    val start = mutableState.value.loopStartMs ?: return false
    val end = exo.currentPosition
    if (!exo.isCurrentMediaItemSeekable || exo.currentMediaItem?.mediaId != loopMediaId || end < start + 500L) return false
    mutableState.update { it.copy(loopEndMs = end) }
    schedule()
    return true
  }

  fun clearLoop() {
    loopMediaId = null
    mutableState.update { it.copy(loopStartMs = null, loopEndMs = null) }
    schedule()
  }

  private fun schedule() {
    handler.removeCallbacks(ticker)
    val hasLoop = mutableState.value.loopEndMs != null && player?.isPlaying == true
    if (deadline != null || hasLoop) handler.postDelayed(ticker, if (hasLoop) 200L else 1000L)
  }

  fun detach() {
    handler.removeCallbacks(ticker)
    handler.removeCallbacks(persistSettings)
    if (player != null) persistSettings.run()
    player?.removeListener(listener)
    player = null
    prefs = null
    deadline = null
    loopMediaId = null
    mutableState.value = PlaybackEnhancementState()
  }
}
