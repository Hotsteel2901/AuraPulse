package com.hotsteel.aurapulse.core

import java.text.DateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Time and size formatting shared by every surface so numbers never disagree. */
object fmt {
  fun duration(milliseconds: Long): String {
    val total = (milliseconds.coerceAtLeast(0) / 1000)
    val hours = total / 3600
    val minutes = (total % 3600) / 60
    val seconds = total % 60
    return if (hours > 0) "%d:%02d:%02d".format(hours, minutes, seconds)
    else "%d:%02d".format(minutes, seconds)
  }

  /** "1 h 24 min" style, for totals rather than track positions. */
  fun durationLong(milliseconds: Long, hoursShort: String, minutesShort: String): String {
    val totalMinutes = (milliseconds.coerceAtLeast(0) / 60_000)
    if (totalMinutes < 1) return "0"
    val hours = totalMinutes / 60
    val minutes = totalMinutes % 60
    return when {
      hours > 0 && minutes > 0 -> hoursShort.format(hours) + " " + minutesShort.format(minutes)
      hours > 0 -> hoursShort.format(hours)
      else -> minutesShort.format(minutes)
    }
  }

  fun hours(milliseconds: Long): String {
    val hours = milliseconds / 3_600_000.0
    return when {
      hours >= 100 -> String.format(Locale.US, "%.0f", hours)
      hours >= 10 -> String.format(Locale.US, "%.1f", hours)
      hours > 0 -> String.format(Locale.US, "%.1f", hours)
      else -> "0"
    }
  }

  fun bytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = arrayOf("KB", "MB", "GB", "TB")
    var value = bytes.toDouble() / 1024
    var index = 0
    while (value >= 1024 && index < units.lastIndex) {
      value /= 1024
      index++
    }
    return String.format(Locale.US, if (value >= 100) "%.0f %s" else "%.1f %s", value, units[index])
  }

  fun relative(timestamp: Long, now: Long = System.currentTimeMillis()): String {
    if (timestamp <= 0) return "—"
    val delta = (now - timestamp).coerceAtLeast(0)
    val minutes = TimeUnit.MILLISECONDS.toMinutes(delta)
    val hours = TimeUnit.MILLISECONDS.toHours(delta)
    val days = TimeUnit.MILLISECONDS.toDays(delta)
    return when {
      minutes < 1 -> "now"
      minutes < 60 -> "${minutes}m"
      hours < 24 -> "${hours}h"
      days < 7 -> "${days}d"
      days < 30 -> "${days / 7}w"
      days < 365 -> "${days / 30}mo"
      else -> "${days / 365}y"
    }
  }

  fun date(timestamp: Long): String =
    if (timestamp <= 0) "—" else DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(timestamp))

  fun clock(milliseconds: Long): String {
    val total = (milliseconds.coerceAtLeast(0) / 1000)
    return "%02d:%02d".format(total / 60, total % 60)
  }

  fun speed(value: Float): String = String.format(Locale.US, "%.2f", value).trimEnd('0').trimEnd('.')

  fun dayKey(timestamp: Long): Int {
    val calendar = Calendar.getInstance().apply { timeInMillis = timestamp }
    return calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
  }

  fun weekdayInitial(index: Int): String =
    listOf("S", "M", "T", "W", "T", "F", "S")[index.coerceIn(0, 6)]

  fun extensionOf(pathOrMime: String): String {
    val lower = pathOrMime.lowercase(Locale.US)
    val fromMime = when {
      lower.contains("mpeg") -> "mp3"
      lower.contains("mp4") || lower.contains("m4a") -> "m4a"
      lower.contains("flac") -> "flac"
      lower.contains("ogg") -> "ogg"
      lower.contains("opus") -> "opus"
      lower.contains("wav") -> "wav"
      else -> null
    }
    if (fromMime != null) return fromMime.uppercase(Locale.US)
    val ext = lower.substringAfterLast('.', "")
    return if (ext.length in 1..5 && ext.all { it.isLetterOrDigit() }) ext.uppercase(Locale.US) else "AUDIO"
  }
}
