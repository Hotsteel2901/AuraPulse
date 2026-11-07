package com.hotsteel.aurapulse.data.library

import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class ImportedEntry(val title: String, val artist: String, val url: String, val durationMs: Long)
data class ImportedPlaylist(val name: String, val entries: List<ImportedEntry>)

/**
 * Reads an M3U/M3U8 playlist or an AuraPulse backup JSON from any HTTPS URL.
 * Used both by "import playlist from URL" and by publishing/consuming gists.
 */
class PlaylistImporter(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .build(),
) {
  suspend fun fetch(rawUrl: String): ImportedPlaylist = withContext(Dispatchers.IO) {
    val url = rawUrl.trim().toHttpUrlOrNull() ?: throw IOException("invalid url")
    // The suffix test must end on a label boundary: "evilgithubusercontent.com"
    // is not GitHub, and a bare endsWith() would let it through over cleartext.
    val githubHost = url.host == "githubusercontent.com" || url.host.endsWith(".githubusercontent.com")
    if (!url.isHttps && !githubHost) throw IOException("https required")
    val request = Request.Builder().url(url).header("User-Agent", "AuraPulse/2.0").build()
    val body = client.newCall(request).execute().use { response ->
      if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
      response.body?.string().orEmpty()
    }
    val name = url.pathSegments.lastOrNull()?.substringBeforeLast('.')?.ifBlank { null } ?: "Imported playlist"
    when {
      body.trimStart().startsWith("{") -> parseJson(body, name)
      else -> parseM3u(body, name)
    }
  }

  private fun parseM3u(body: String, fallbackName: String): ImportedPlaylist {
    val entries = mutableListOf<ImportedEntry>()
    var pendingTitle = ""
    var pendingDuration = 0L
    var playlistName = fallbackName
    for (raw in body.lineSequence()) {
      val line = raw.trim()
      if (line.isEmpty()) continue
      when {
        line.startsWith("#EXTM3U") -> Unit
        line.startsWith("#PLAYLIST:") -> playlistName = line.removePrefix("#PLAYLIST:").trim().ifBlank { fallbackName }
        line.startsWith("#EXTINF:") -> {
          val payload = line.removePrefix("#EXTINF:")
          val duration = payload.substringBefore(',').trim().toDoubleOrNull()?.times(1000)?.toLong() ?: 0L
          val label = payload.substringAfter(',', "").trim()
          pendingDuration = duration
          pendingTitle = label
        }
        line.startsWith("#") -> Unit
        else -> {
          val parts = pendingTitle.split(" - ", limit = 2)
          val artist = if (parts.size == 2) parts[0].trim() else ""
          val title = if (parts.size == 2) parts[1].trim() else pendingTitle.ifBlank { line.substringAfterLast('/') }
          entries += ImportedEntry(title, artist, line, pendingDuration)
          pendingTitle = ""
          pendingDuration = 0
        }
      }
    }
    if (entries.isEmpty()) throw IOException("no entries")
    return ImportedPlaylist(playlistName, entries)
  }

  private fun parseJson(body: String, fallbackName: String): ImportedPlaylist {
    val root = JSONObject(body)
    val array: JSONArray = root.optJSONArray("tracks") ?: root.optJSONArray("entries") ?: root.optJSONArray("items")
      ?: throw IOException("no tracks array")
    val entries = (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      val url = listOf("uri", "url", "streamUrl", "stream").firstNotNullOfOrNull { key ->
        item.optString(key).takeIf { it.startsWith("http") }
      } ?: return@mapNotNull null
      ImportedEntry(
        title = item.optString("title").ifBlank { "Untitled" },
        artist = item.optString("artist"),
        url = url,
        durationMs = item.optLong("durationMs"),
      )
    }
    if (entries.isEmpty()) throw IOException("no playable entries")
    return ImportedPlaylist(root.optString("name").ifBlank { fallbackName }, entries)
  }
}
