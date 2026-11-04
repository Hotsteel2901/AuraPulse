package com.hotsteel.aurapulse.data.discovery

import com.hotsteel.aurapulse.data.library.Source
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class RemoteTrack(
  val title: String,
  val artist: String,
  val album: String = "",
  val artworkUrl: String? = null,
  val streamUrl: String,
  val durationMs: Long = 0,
  val source: String = Source.PREVIEW,
  val subtitle: String = "",
)

enum class DiscoverySource { PREVIEWS, RADIO, CUSTOM }

class DiscoveryException(message: String, cause: Throwable? = null) : IOException(message, cause)

/**
 * Talks to three real endpoints and nothing else. Previews come from the public
 * iTunes Search API, stations from Radio Browser, and anything else from a
 * user-supplied HTTPS template.
 */
class DiscoveryClient(
  private val client: OkHttpClient = OkHttpClient.Builder()
    .connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(20, TimeUnit.SECONDS)
    .callTimeout(30, TimeUnit.SECONDS)
    .build(),
) {
  private fun get(url: String, accept: String = "application/json"): String {
    val request = Request.Builder().url(url).header("Accept", accept).header("User-Agent", "AuraPulse/2.0").build()
    return client.newCall(request).execute().use { response ->
      val body = response.body?.string().orEmpty()
      if (!response.isSuccessful) throw DiscoveryException("HTTP ${response.code}")
      body
    }
  }

  suspend fun previews(query: String, country: String): List<RemoteTrack> = withContext(Dispatchers.IO) {
    val url = "https://itunes.apple.com/search".toHttpUrlOrNull()!!.newBuilder()
      .addQueryParameter("term", query)
      .addQueryParameter("media", "music")
      .addQueryParameter("entity", "song")
      .addQueryParameter("limit", "50")
      .addQueryParameter("country", country.ifBlank { "US" })
      .build()
    val json = JSONObject(runCatching { get(url.toString()) }.getOrElse { throw DiscoveryException(it.localizedMessage ?: "network", it) })
    val results = json.optJSONArray("results") ?: JSONArray()
    (0 until results.length()).mapNotNull { index ->
      val item = results.optJSONObject(index) ?: return@mapNotNull null
      val preview = item.optString("previewUrl")
      if (preview.isBlank()) return@mapNotNull null
      RemoteTrack(
        title = item.optString("trackName").ifBlank { "Untitled" },
        artist = item.optString("artistName"),
        album = item.optString("collectionName"),
        artworkUrl = item.optString("artworkUrl100").takeIf { it.isNotBlank() }?.replace("100x100", "600x600"),
        streamUrl = preview,
        durationMs = item.optLong("trackTimeMillis").coerceAtMost(30_000),
        source = Source.PREVIEW,
      )
    }
  }

  suspend fun radio(query: String, country: String): List<RemoteTrack> = withContext(Dispatchers.IO) {
    val builder = "https://de1.api.radio-browser.info/json/stations/search".toHttpUrlOrNull()!!.newBuilder()
      .addQueryParameter("limit", "60")
      .addQueryParameter("hidebroken", "true")
      .addQueryParameter("order", "clickcount")
      .addQueryParameter("reverse", "true")
    if (query.isNotBlank()) builder.addQueryParameter("name", query)
    if (country.matches(Regex("[A-Za-z]{2}"))) builder.addQueryParameter("countrycode", country.uppercase(Locale.US))
    val body = runCatching { get(builder.build().toString()) }
      .getOrElse { throw DiscoveryException(it.localizedMessage ?: "network", it) }
    parseStations(JSONArray(body))
  }

  suspend fun radioByTag(tag: String): List<RemoteTrack> = withContext(Dispatchers.IO) {
    val url = "https://de1.api.radio-browser.info/json/stations/search".toHttpUrlOrNull()!!.newBuilder()
      .addQueryParameter("tag", tag).addQueryParameter("limit", "40")
      .addQueryParameter("hidebroken", "true").addQueryParameter("order", "clickcount").addQueryParameter("reverse", "true")
      .build()
    parseStations(JSONArray(runCatching { get(url.toString()) }.getOrElse { throw DiscoveryException(it.localizedMessage ?: "network", it) }))
  }

  private fun parseStations(array: JSONArray): List<RemoteTrack> = (0 until array.length()).mapNotNull { index ->
    val item = array.optJSONObject(index) ?: return@mapNotNull null
    val stream = item.optString("url_resolved").ifBlank { item.optString("url") }
    if (stream.isBlank()) return@mapNotNull null
    val tags = item.optString("tags").split(',').map { it.trim() }.filter { it.isNotBlank() }.take(3)
    RemoteTrack(
      title = item.optString("name").ifBlank { "Station" },
      artist = item.optString("country").ifBlank { "Radio" },
      streamUrl = stream,
      artworkUrl = item.optString("favicon").takeIf { it.startsWith("http") },
      source = Source.RADIO,
      subtitle = listOfNotNull(item.optString("codec").takeIf { it.isNotBlank() }, item.optString("bitrate").takeIf { it.isNotBlank() && it != "0" }?.plus(" kbps"), tags.firstOrNull()).joinToString(" · "),
    )
  }

  suspend fun custom(template: String, query: String): List<RemoteTrack> = withContext(Dispatchers.IO) {
    val url = template.replace("{query}", java.net.URLEncoder.encode(query, "UTF-8"))
    val body = runCatching { get(url) }.getOrElse { throw DiscoveryException(it.localizedMessage ?: "network", it) }
    val root = runCatching { JSONObject(body) }.getOrNull()
    val array = when {
      root == null -> JSONArray(body)
      root.optJSONArray("results") != null -> root.getJSONArray("results")
      root.optJSONArray("tracks") != null -> root.getJSONArray("tracks")
      root.optJSONArray("items") != null -> root.getJSONArray("items")
      root.optJSONArray("data") != null -> root.getJSONArray("data")
      else -> JSONArray()
    }
    (0 until array.length()).mapNotNull { index ->
      val item = array.optJSONObject(index) ?: return@mapNotNull null
      val stream = listOf("streamUrl", "url", "audio", "stream").firstNotNullOfOrNull { key ->
        item.optString(key).takeIf { it.startsWith("http") }
      } ?: return@mapNotNull null
      RemoteTrack(
        title = item.optString("title").ifBlank { item.optString("name").ifBlank { "Untitled" } },
        artist = item.optString("artist").ifBlank { item.optString("author") },
        album = item.optString("album"),
        artworkUrl = item.optString("artwork").takeIf { it.startsWith("http") },
        streamUrl = stream,
        source = Source.CUSTOM,
      )
    }
  }

  companion object {
    fun isValidTemplate(template: String): Boolean {
      val probe = template.replace("{query}", "x").toHttpUrlOrNull() ?: return false
      return template.contains("{query}") && probe.isHttps && probe.username.isEmpty() && probe.password.isEmpty()
    }

    fun isValidStream(value: String): Boolean {
      val url = value.toHttpUrlOrNull() ?: return false
      return url.username.isEmpty() && url.password.isEmpty()
    }
  }
}
