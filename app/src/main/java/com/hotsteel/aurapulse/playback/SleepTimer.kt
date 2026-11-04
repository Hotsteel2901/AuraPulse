package com.hotsteel.aurapulse.playback

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SleepState(
  val endsAt: Long? = null,
  val atTrackEnd: Boolean = false,
  val fading: Boolean = false,
) {
  val active: Boolean get() = endsAt != null || atTrackEnd
  fun remainingMs(now: Long = System.currentTimeMillis()): Long? = endsAt?.let { (it - now).coerceAtLeast(0) }
}

/**
 * The timer lives in this process-wide object so it keeps counting while the UI
 * is gone; the playback service applies the fade and the final pause.
 */
object SleepTimer {
  private val mutable = MutableStateFlow(SleepState())
  val state: StateFlow<SleepState> = mutable.asStateFlow()

  val fadeWindowMs = 15_000L

  fun start(minutes: Int) {
    if (minutes <= 0) return
    mutable.value = SleepState(endsAt = System.currentTimeMillis() + minutes * 60_000L)
  }

  fun endOfTrack() {
    mutable.value = SleepState(atTrackEnd = true)
  }

  fun markFading(fading: Boolean) {
    if (mutable.value.fading != fading) mutable.value = mutable.value.copy(fading = fading)
  }

  fun cancel() {
    mutable.value = SleepState()
  }

  /** Fraction of the fade window that is left, or 1 when no fade is running. */
  fun fadeFactor(now: Long = System.currentTimeMillis()): Float {
    val endsAt = mutable.value.endsAt ?: return 1f
    val remaining = endsAt - now
    if (remaining > fadeWindowMs) return 1f
    return (remaining.toFloat() / fadeWindowMs).coerceIn(0f, 1f)
  }
}
