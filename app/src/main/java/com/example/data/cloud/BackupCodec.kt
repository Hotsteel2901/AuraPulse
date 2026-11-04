package com.example.data.cloud

import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.data.library.encodeTrackIds
import com.example.data.library.stableTrackId
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import org.json.JSONArray
import org.json.JSONObject

data class BackupContent(val tracks: List<LibraryTrack>, val playlists: List<LibraryPlaylist>)

/** Portable metadata only. File contents, Android document grants and downloaded copies never leave the device. */
object BackupCodec {
  const val MAX_BACKUP_BYTES = 700_000
  private const val MAX_TRACKS = 2000
  private const val MAX_PLAYLISTS = 200
  private const val MAX_IDS = 1000

  fun encode(tracks: List<LibraryTrack>, playlists: List<LibraryPlaylist>): String {
    val remote = tracks.filter { it.source in TrackSource.remote && safeRemoteUrl(it.uri) }
    require(remote.size <= MAX_TRACKS && playlists.size <= MAX_PLAYLISTS) { "Library is too large for one backup" }
    val trackArray = JSONArray()
    remote.forEach { t ->
      trackArray.put(JSONObject().put("id", t.id).put("uri", t.uri).put("title", t.title)
        .put("artist", t.artist).put("album", t.album).put("durationMs", t.durationMs)
        .put("source", t.source).put("artworkUri", t.artworkUri?.takeIf(::safeRemoteUrl) ?: "")
        .put("addedAt", t.addedAt))
    }
    val playlistArray = JSONArray()
    playlists.forEach { p ->
      val ids = p.trackIds()
      require(ids.size <= MAX_IDS) { "Playlist is too large" }
      playlistArray.put(JSONObject().put("id", p.id).put("name", p.name)
        .put("trackIds", JSONArray(ids)).put("updatedAt", p.updatedAt))
    }
    val json = JSONObject().put("version", 1).put("tracks", trackArray).put("playlists", playlistArray).toString()
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is too large" }
    return json
  }

  fun decode(json: String): BackupContent {
    require(json.toByteArray(Charsets.UTF_8).size <= MAX_BACKUP_BYTES) { "Backup is too large" }
    val root = JSONObject(json)
    require(root.getInt("version") == 1) { "Unsupported backup version" }
    val tracks = root.getJSONArray("tracks")
    val playlists = root.getJSONArray("playlists")
    require(tracks.length() <= MAX_TRACKS && playlists.length() <= MAX_PLAYLISTS)
    val restoredTracks = (0 until tracks.length()).map { index ->
      val t = tracks.getJSONObject(index)
      val uri = t.getString("uri")
      val id = t.getString("id")
      val source = t.getString("source")
      require(safeRemoteUrl(uri) && source in TrackSource.remote && id == stableTrackId(uri)) { "Invalid track in backup" }
      val art = t.optString("artworkUri").takeIf(::safeRemoteUrl)
      LibraryTrack(
        id = id, uri = uri, title = t.getString("title").take(200), artist = t.optString("artist").take(200),
        album = t.optString("album").take(200), durationMs = t.optLong("durationMs").coerceAtLeast(0),
        source = source, artworkUri = art, downloadedPath = null, addedAt = t.optLong("addedAt").coerceAtLeast(0),
      )
    }
    require(restoredTracks.map { it.id }.distinct().size == restoredTracks.size)
    val restoredPlaylists = (0 until playlists.length()).map { index ->
      val p = playlists.getJSONObject(index)
      val id = p.getString("id")
      val name = p.getString("name").trim()
      require(id.matches(Regex("playlist_[a-zA-Z0-9-]{1,80}")) && name.isNotEmpty() && name.length <= 80)
      val items = p.getJSONArray("trackIds")
      require(items.length() <= MAX_IDS)
      val ids = (0 until items.length()).map { items.getString(it) }
      require(ids.all { it.matches(Regex("track_[0-9a-f]{32}")) })
      LibraryPlaylist(id, name, encodeTrackIds(ids), p.getLong("updatedAt").coerceAtLeast(0))
    }
    require(restoredPlaylists.map { it.id }.distinct().size == restoredPlaylists.size)
    return BackupContent(restoredTracks, restoredPlaylists)
  }

  private fun safeRemoteUrl(value: String): Boolean {
    val url = value.toHttpUrlOrNull() ?: return false
    return url.username.isEmpty() && url.password.isEmpty()
  }
}
