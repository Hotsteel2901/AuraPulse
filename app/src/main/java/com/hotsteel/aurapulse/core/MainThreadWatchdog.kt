package com.hotsteel.aurapulse.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.hotsteel.aurapulse.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

/**
 * Records what the main thread was doing when it stopped responding.
 *
 * A wedged main thread cannot report itself, so a daemon thread watches a
 * heartbeat that the looper posts every 500 ms. When the gap passes four
 * seconds the main thread stack is written to internal storage, and the next
 * launch shows it in the crash screen — which is the only way to diagnose a
 * freeze without adb.
 *
 * Debug builds only: the heartbeat is cheap, but there is nothing to gain from
 * running it for users.
 */
object MainThreadWatchdog {
  private const val DIR = "crashes"
  private const val FILE = "stall.txt"
  private const val TIMEOUT_MS = 4_000L
  private const val BEAT_MS = 500L

  fun install(context: Context) {
    if (!BuildConfig.DEBUG) return
    // Robolectric's paused looper treats the repeating heartbeat as work that
    // never finishes, so every idle() call would spin forever.
    if (android.os.Build.FINGERPRINT == "robolectric") return
    val app = context.applicationContext
    val main = Looper.getMainLooper()
    val handler = Handler(main)
    val lastBeat = AtomicLong(SystemClock.uptimeMillis())
    val reported = AtomicBoolean(false)

    val beat = object : Runnable {
      override fun run() {
        lastBeat.set(SystemClock.uptimeMillis())
        reported.set(false)
        handler.postDelayed(this, BEAT_MS)
      }
    }
    handler.post(beat)

    Thread({
      while (true) {
        try {
          Thread.sleep(1_000)
        } catch (_: InterruptedException) {
          return@Thread
        }
        val stalled = SystemClock.uptimeMillis() - lastBeat.get()
        if (stalled >= TIMEOUT_MS && reported.compareAndSet(false, true)) {
          write(app, stalled, main.thread)
        }
      }
    }, "aura-main-watchdog").apply { isDaemon = true }.start()
  }

  private fun write(context: Context, stalledMs: Long, thread: Thread) {
    runCatching {
      val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
      val report = buildString {
        appendLine("time: $stamp")
        appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("main thread unresponsive for $stalledMs ms")
        appendLine()
        thread.stackTrace.forEach { appendLine("  at $it") }
      }
      File(context.filesDir, DIR).mkdirs()
      File(context.filesDir, "$DIR/$FILE").writeText(report)
    }
  }

  fun last(context: Context): String? = runCatching {
    File(context.filesDir, "$DIR/$FILE").takeIf { it.isFile }?.readText()
  }.getOrNull()

  fun clear(context: Context) {
    runCatching { File(context.filesDir, "$DIR/$FILE").delete() }
  }
}
