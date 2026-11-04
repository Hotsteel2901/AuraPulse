package com.hotsteel.aurapulse.data.library

/** One timed line of an LRC file. */
data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(
  val lines: List<LyricLine>,
  val synced: Boolean,
  val title: String? = null,
  val artist: String? = null,
) {
  /** Index of the line that should be highlighted at [positionMs], or -1 before the first line. */
  fun indexAt(positionMs: Long): Int {
    if (!synced || lines.isEmpty()) return -1
    var low = 0
    var high = lines.lastIndex
    var result = -1
    while (low <= high) {
      val mid = (low + high) / 2
      if (lines[mid].timeMs <= positionMs) {
        result = mid
        low = mid + 1
      } else {
        high = mid - 1
      }
    }
    return result
  }

  companion object {
    val EMPTY = Lyrics(emptyList(), synced = false)
  }
}

/**
 * Parses the subset of LRC that players actually write: `[mm:ss.xx]` tags,
 * several timestamps per line, `[offset:…]` and the common metadata headers.
 * Anything without a timestamp is kept as unsynchronised text.
 */
object LrcParser {
  private val stamp = Regex("\\[(\\d{1,3}):(\\d{1,2})(?:[.:](\\d{1,3}))?]")
  private val offset = Regex("\\[offset:\\s*([+-]?\\d+)]", RegexOption.IGNORE_CASE)
  private val meta = Regex("\\[(ar|ti|al|by|re|ve):([^]]*)]", RegexOption.IGNORE_CASE)

  fun parse(raw: String): Lyrics {
    val text = raw.removePrefix("\uFEFF")
    val globalOffset = offset.find(text)?.groupValues?.get(1)?.toIntOrNull() ?: 0
    var title: String? = null
    var artist: String? = null
    val lines = mutableListOf<LyricLine>()
    val plain = mutableListOf<String>()
    for (rawLine in text.lineSequence()) {
      val line = rawLine.trim()
      if (line.isEmpty()) continue
      val header = meta.find(line)
      if (header != null) {
        when (header.groupValues[1].lowercase()) {
          "ti" -> title = header.groupValues[2].trim().ifBlank { null }
          "ar" -> artist = header.groupValues[2].trim().ifBlank { null }
        }
        continue
      }
      if (line.startsWith("[offset:", true)) continue
      val matches = stamp.findAll(line).toList()
      if (matches.isEmpty()) {
        if (!line.startsWith("[")) plain += line
        continue
      }
      val content = line.substring(matches.last().range.last + 1).trim()
      for (match in matches) {
        val minutes = match.groupValues[1].toLongOrNull() ?: continue
        val seconds = match.groupValues[2].toLongOrNull() ?: continue
        val fractionRaw = match.groupValues[3]
        val fraction = when (fractionRaw.length) {
          0 -> 0L
          1 -> fractionRaw.toLong() * 100
          2 -> fractionRaw.toLong() * 10
          else -> fractionRaw.take(3).toLong()
        }
        lines += LyricLine(minutes * 60_000 + seconds * 1_000 + fraction - globalOffset, content)
      }
    }
    if (lines.isEmpty()) {
      return Lyrics(plain.mapIndexed { index, value -> LyricLine(index * 4_000L, value) }, synced = false, title, artist)
    }
    return Lyrics(lines.sortedBy { it.timeMs }, synced = true, title, artist)
  }

  /** Probes a neighbouring `.lrc` document for the given file name. */
  fun companionName(fileName: String): String = fileName.substringBeforeLast('.', fileName) + ".lrc"
}
