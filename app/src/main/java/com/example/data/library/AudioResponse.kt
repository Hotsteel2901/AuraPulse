package com.example.data.library

/** Streaming manifests refer to external segments and are not an offline audio file. */
internal object AudioResponse {
  private val playlistTypes = setOf(
    "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl",
    "application/dash+xml", "application/x-hls", "audio/scpls", "application/pls+xml",
  )
  private val playlistExtensions = setOf("m3u", "m3u8", "mpd", "pls", "asx", "xspf", "wpl")

  fun isStreamingPlaylist(contentType: String, path: String, sample: ByteArray): Boolean {
    val type = contentType.substringBefore(';').trim().lowercase()
    val extension = path.substringAfterLast('/', "").substringAfterLast('.', "").lowercase()
    if (type in playlistTypes || extension in playlistExtensions || path.lowercase().contains(".ism/manifest")) return true
    val text = sample.toString(Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
    return text.startsWith("#EXTM3U", ignoreCase = true) || text.startsWith("[playlist]", ignoreCase = true) ||
      text.contains(Regex("<(?:MPD|SmoothStreamingMedia|ASX|playlist)(?:\\s|>)", RegexOption.IGNORE_CASE))
  }
}

class UnsupportedStreamingPlaylistException : IllegalArgumentException("Streaming playlists cannot be downloaded as a single offline audio file")
