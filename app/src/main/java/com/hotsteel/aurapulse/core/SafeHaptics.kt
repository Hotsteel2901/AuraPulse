package com.hotsteel.aurapulse.core

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import java.util.concurrent.Executors

/**
 * Haptics that cannot freeze the UI.
 *
 * `View.performHapticFeedback` and `Vibrator.vibrate` are synchronous binder
 * calls to the vibrator service. On this device that service can wedge, and a
 * blocked binder call never times out: tapping the play button (which ticked)
 * froze the whole app. The request now runs on a private worker thread, so the
 * worst case is a dropped tick — never a dead main thread.
 *
 * One worker, not a pool: if the service is wedged, the first call parks that
 * thread and later ticks are dropped instead of piling up.
 */
object SafeHaptics {
  private val worker = Executors.newSingleThreadExecutor { runnable ->
    Thread(runnable, "aura-haptics").apply {
      isDaemon = true
      priority = Thread.MIN_PRIORITY
    }
  }

  @Volatile private var vibrator: Vibrator? = null

  /** Short tick for toggles and chips. */
  fun tick(context: Context) = play(context, 8L)

  /** Slightly longer confirmation for a primary action. */
  fun press(context: Context) = play(context, 16L)

  private fun play(context: Context, milliseconds: Long) {
    val app = context.applicationContext
    worker.execute {
      runCatching {
        val device = vibrator ?: resolve(app).also { vibrator = it } ?: return@runCatching
        if (!device.hasVibrator()) return@runCatching
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
          device.vibrate(VibrationEffect.createOneShot(milliseconds, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
          @Suppress("DEPRECATION")
          device.vibrate(milliseconds)
        }
      }
    }
  }

  private fun resolve(context: Context): Vibrator? =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      context.getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Vibrator::class.java)
    }
}
