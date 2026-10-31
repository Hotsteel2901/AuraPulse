package com.example.data.discovery

import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.data.library.stableTrackId
import java.net.URLEncoder
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

enum class DiscoverySource { PREVIEWS, RADIO, CUSTOM }

/** Only returns entries that actually provide an HTTP(S) audio URL. */
class MusicDiscovery {
  private val http = OkHttpClient.Builder().connectTimeout(12, TimeUnit.SECONDS)
    .readTimeout(15, TimeUnit.SECONDS).callTimeout(30, TimeUnit.SECONDS).build()

  suspend fun search(source: DiscoverySource, query: String, country: String, customTemplate: String = ""): List<LibraryTrack> =
    withContext(Dispatchers.IO) {
      require(query.isNotBlank())
      val region = country.trim().uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US"
      val encoded = URLEncoder.encode(query.trim(), "UTF-8")
      val url = when (source) {
        DiscoverySource.PREVIEWS -> "https://itunes.apple.com/search?media=music&entity=song&limit=35&country=$region&term=$encoded"
        DiscoverySource.RADIO -> "https://de1.api.radio-browser.info/json/stations/search?name=$encoded&hidebroken=true&order=clickcount&reverse=true&limit=35"
        DiscoverySource.CUSTOM -> {
          require(customTemplate.contains("{query}")) { "A {query} placeholder is required" }
          customTemplate.replace("{query}", encoded).replace("{country}", region)
        }
      }
      val parsedUrl = url.toHttpUrlOrNull() ?: error("Invalid search URL")
      require(parsedUrl.isHttps && parsedUrl.username.isEmpty() && parsedUrl.password.isEmpty()) {
        "The search endpoint must be HTTPS and must not contain credentials"
      }
      val request = Request.Builder().url(parsedUrl).header("User-Agent", "AuraPulse/1.0")
        .header("Accept", "application/json").build()
      http.newCall(request).execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val body = response.body ?: error("Empty response")
        if (body.contentLength() > MAX_RESPONSE_BYTES) error("Response is too large")
        val raw = body.byteStream().use { stream ->
          val bytes = stream.readNBytesCompat(MAX_RESPONSE_BYTES + 1)
          require(bytes.size <= MAX_RESPONSE_BYTES) { "Response is too large" }
          bytes.toString(Charsets.UTF_8)
        }
        parseResults(source, raw, parsedUrl.toString())
      }
    }

  internal fun parseResults(source: DiscoverySource, json: String, baseUrl: String): List<LibraryTrack> {
    val root = json.trim()
    val array = if (root.startsWith("[")) JSONArray(root) else {
      val obj = JSONObject(root)
      obj.optJSONArray("results") ?: obj.optJSONArray("tracks") ?: obj.optJSONArray("items")
        ?: obj.optJSONArray("data") ?: throw IllegalArgumentException("No results array in response")
    }
    val out = mutableListOf<LibraryTrack>()
    for (index in 0 until minOf(array.length(), 100)) {
      val obj = array.optJSONObject(index) ?: continue
      val audio = when (source) {
        DiscoverySource.PREVIEWS -> obj.string("previewUrl")
        DiscoverySource.RADIO -> obj.string("url_resolved", "url")
        DiscoverySource.CUSTOM -> obj.string("streamUrl", "audioUrl", "url", "previewUrl")
      }
      val url = resolvePlayableUrl(baseUrl, audio) ?: continue
      val title = when (source) {
        DiscoverySource.PREVIEWS -> obj.string("trackName")
        DiscoverySource.RADIO -> obj.string("name")
        DiscoverySource.CUSTOM -> obj.string("title", "name", "trackName")
      }
      if (title.isBlank()) continue
      val art = when (source) {
        DiscoverySource.PREVIEWS -> obj.string("artworkUrl100", "artworkUrl60")
        DiscoverySource.RADIO -> obj.string("favicon")
        DiscoverySource.CUSTOM -> obj.string("artworkUrl", "image", "cover")
      }.let { resolvePlayableUrl(baseUrl, it) }
      val artist = when (source) {
        DiscoverySource.PREVIEWS -> obj.string("artistName")
        DiscoverySource.RADIO -> obj.string("country", "tags")
        DiscoverySource.CUSTOM -> obj.string("artist", "artistName")
      }
      val sourceType = when (source) {
        DiscoverySource.PREVIEWS -> TrackSource.PREVIEW
        DiscoverySource.RADIO -> TrackSource.RADIO
        DiscoverySource.CUSTOM -> TrackSource.CUSTOM
      }
      out += LibraryTrack(
        id = stableTrackId(url), uri = url, title = title.take(200), artist = artist.take(200),
        album = obj.string("collectionName", "album").take(200),
        // The catalog's trackTimeMillis describes the full song, not the preview.
        durationMs = if (source == DiscoverySource.CUSTOM) obj.optLong("durationMs", 0L).coerceAtLeast(0) else 0,
        source = sourceType, artworkUri = art,
      )
    }
    return out.distinctBy { it.id }
  }

  companion object {
    private const val MAX_RESPONSE_BYTES = 2 * 1024 * 1024

    internal fun resolvePlayableUrl(baseUrl: String, value: String): String? {
      if (value.isBlank()) return null
      val url = value.toHttpUrlOrNull() ?: baseUrl.toHttpUrlOrNull()?.resolve(value) ?: return null
      if (url.username.isNotEmpty() || url.password.isNotEmpty()) return null
      return url.toString()
    }

    private fun JSONObject.string(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
      if (!has(key) || isNull(key)) null else optString(key).trim().takeIf { it.isNotEmpty() && it != "null" }
    }.orEmpty()

    // InputStream.readNBytes is not available on every Android version supported by the app.
    private fun java.io.InputStream.readNBytesCompat(limit: Int): ByteArray {
      val out = java.io.ByteArrayOutputStream()
      val buffer = ByteArray(8192)
      while (out.size() < limit) {
        val n = read(buffer, 0, minOf(buffer.size, limit - out.size()))
        if (n < 0) break
        out.write(buffer, 0, n)
      }
      return out.toByteArray()
    }
  }
}
