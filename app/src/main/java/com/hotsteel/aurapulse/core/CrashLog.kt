package com.hotsteel.aurapulse.core

import android.content.Context
import android.os.Build
import com.hotsteel.aurapulse.BuildConfig
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Keeps the last uncaught exception on disk so a crash that happens before the
 * first frame can still be read on the next launch, without adb.
 *
 * The report is written to internal storage (always writable) and mirrored to
 * the app-specific external folder so it can be pulled with a file manager.
 */
object CrashLog {
  private const val DIR = "crashes"
  private const val LAST = "last.txt"

  fun install(context: Context) {
    val app = context.applicationContext
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      runCatching { write(app, error) }
      if (previous != null) previous.uncaughtException(thread, error) else kotlin.system.exitProcess(10)
    }
  }

  private fun write(context: Context, error: Throwable) {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
    val report = buildString {
      appendLine("time: $stamp")
      appendLine("device: ${Build.MANUFACTURER} ${Build.MODEL}")
      appendLine("android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
      appendLine("app: ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
      appendLine()
      appendLine(error.stackTraceToString())
    }
    val dir = File(context.filesDir, DIR).apply { mkdirs() }
    File(dir, LAST).writeText(report)
    runCatching {
      val external = context.getExternalFilesDir(null) ?: return@runCatching
      external.mkdirs()
      File(external, "last-crash.txt").writeText(report)
    }
  }

  fun last(context: Context): String? = runCatching {
    File(context.filesDir, "$DIR/$LAST").takeIf { it.isFile }?.readText()
  }.getOrNull()

  fun clear(context: Context) {
    runCatching { File(context.filesDir, DIR).deleteRecursively() }
    runCatching { context.getExternalFilesDir(null)?.let { File(it, "last-crash.txt").delete() } }
  }
}
