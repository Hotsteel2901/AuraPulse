package com.hotsteel.aurapulse.data.github

import android.content.Context
import android.net.Uri
import android.os.Build
import com.hotsteel.aurapulse.data.library.LibraryRepository
import com.hotsteel.aurapulse.data.library.PlaylistEntity
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.data.library.decodeIds
import com.hotsteel.aurapulse.data.library.encodeIds
import org.json.JSONArray
import org.json.JSONObject

data class SyncState(
  val gistId: String? = null,
  val lastPushedAt: Long = 0,
  val revision: String? = null,
  val remoteUpdatedAt: String? = null,
  val conflict: Boolean = false,
  val trackCount: Int = 0,
  val playlistCount: Int = 0,
)

data class SyncResult(val tracks: Int, val playlists: Int, val url: String)

/**
 * Backs playlists and online links up to a private GitHub gist. Audio files,
 * document permissions and artwork never leave the device.
 */
class GistSync(
  private val context: Context,
  private val account: GitHubAccount,
  private val library: LibraryRepository,
  private val favorites: () -> Set<String>,
  private val restoreFavorites: (Set<String>) -> Unit,
) {
  private val prefs = context.applicationContext.getSharedPreferences("aurapulse_sync", Context.MODE_PRIVATE)
  private val api get() = account.api()

  fun state(): SyncState = SyncState(
    gistId = prefs.getString("gist_id", null),
    lastPushedAt = prefs.getLong("pushed_at", 0),
    revision = prefs.getString("revision", null),
    remoteUpdatedAt = prefs.getString("remote_updated", null),
    conflict = prefs.getBoolean("conflict", false),
    trackCount = prefs.getInt("tracks", 0),
    playlistCount = prefs.getInt("playlists", 0),
  )

  /** Reports whether the remote gist was changed by someone else since our last backup. */
  suspend fun check(): SyncState {
    val session = account.session ?: return state()
    val id = prefs.getString("gist_id", null) ?: return state()
    return try {
      val gist = api.gist(session.token, id)
      val remote = gist.updatedAt
      val conflict = remote != null && remote != prefs.getString("remote_updated", null)
      prefs.edit().putBoolean("conflict", conflict).apply()
      state().copy(remoteUpdatedAt = remote, conflict = conflict)
    } catch (_: Exception) {
      state()
    }
  }

  suspend fun push(): SyncResult {
    val session = account.requireSession()
    val tracks = library.tracksSnapshot()
    val playlists = library.playlistsSnapshot()
    val payload = buildPayload(tracks, playlists, favorites())
    val content = payload.toString(2)
    val gistId = prefs.getString("gist_id", null) ?: findBackupGist(session.token)
    val gist = if (gistId != null) {
      runCatching { api.updateGist(session.token, gistId, DESCRIPTION, mapOf(FILE to content)) }
        .getOrElse { error ->
          if (error is GitHubException && error.isNotFound) api.createGist(session.token, DESCRIPTION, mapOf(FILE to content), public = false)
          else throw error
        }
    } else {
      api.createGist(session.token, DESCRIPTION, mapOf(FILE to content), public = false)
    }
    prefs.edit()
      .putString("gist_id", gist.id)
      .putString("remote_updated", gist.updatedAt)
      .putString("revision", gist.updatedAt)
      .putLong("pushed_at", System.currentTimeMillis())
      .putBoolean("conflict", false)
      .putInt("tracks", tracks.size)
      .putInt("playlists", playlists.size)
      .apply()
    return SyncResult(tracks.size, playlists.size, gist.htmlUrl)
  }

  suspend fun pull(): SyncResult {
    val session = account.requireSession()
    val gistId = prefs.getString("gist_id", null) ?: findBackupGist(session.token)
      ?: throw GitHubException(404, "no backup")
    val gist = api.gist(session.token, gistId)
    val raw = gist.files[FILE] ?: gist.files.values.firstOrNull { it.isNotBlank() }
      ?: throw GitHubException(404, "no backup")
    val result = importPayload(JSONObject(raw), gist.htmlUrl)
    prefs.edit()
      .putString("gist_id", gist.id)
      .putString("remote_updated", gist.updatedAt)
      .putString("revision", gist.updatedAt)
      .putBoolean("conflict", false)
      .apply()
    return result
  }

  /** Serialises the same schema used by gist sync, for a local file export. */
  suspend fun exportJson(): String {
    val payload = buildPayload(library.tracksSnapshot(), library.playlistsSnapshot(), favorites())
    return payload.toString(2)
  }

  /** Merges a previously exported JSON document back into the library. */
  suspend fun importJson(raw: String, origin: String): SyncResult = importPayload(JSONObject(raw), origin)

  private suspend fun importPayload(payload: JSONObject, origin: String): SyncResult {
    if (payload.optInt("schema", 0) != SCHEMA) throw GitHubException(422, "unsupported backup schema")

    val remoteTracks = payload.optJSONArray("tracks")?.let(::readTracks).orEmpty()
    val remotePlaylists = payload.optJSONArray("playlists")?.let(::readPlaylists).orEmpty()
    val remoteFavorites = payload.optJSONArray("favorites")?.let { array ->
      (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
    }.orEmpty()

    val local = library.tracksSnapshot().associateBy { it.id }
    val toInsert = remoteTracks.filter { remote ->
      val existing = local[remote.id]
      when {
        existing == null -> remote.source != Source.LOCAL || documentReadable(remote.uri)
        remote.addedAt > existing.addedAt && existing.source == remote.source -> false // metadata refresh is cosmetic; keep local file state
        else -> false
      }
    }.map { it.copy(missing = false) }
    library.restore(toInsert)

    val localPlaylists = library.playlistsSnapshot().associateBy { it.id }
    var merged = 0
    for (remote in remotePlaylists) {
      val existing = localPlaylists[remote.id]
      if (existing == null) {
        library.restorePlaylist(remote); merged++
      } else if (remote.updatedAt > existing.updatedAt) {
        library.restorePlaylist(remote); merged++
      }
    }
    restoreFavorites(favorites() + remoteFavorites)
    return SyncResult(toInsert.size, merged, origin)
  }

  /** Publishes one playlist as a portable .m3u gist so it can be shared or imported elsewhere. */
  suspend fun publishPlaylist(playlist: PlaylistEntity, tracks: List<TrackEntity>, public: Boolean, device: String): String {
    val session = account.requireSession()
    val byId = tracks.associateBy { it.id }
    val builder = StringBuilder("#EXTM3U\n")
    playlist.trackIds().mapNotNull(byId::get).forEach { track ->
      val seconds = (track.durationMs / 1000).toInt()
      builder.append("#EXTINF:").append(seconds).append(',')
        .append(track.artist.ifBlank { "Unknown artist" }).append(" - ")
        .append(track.title).append('\n')
        .append(track.uri).append('\n')
    }
    val name = playlist.name.replace(Regex("[^A-Za-z0-9._-]+"), "-").trim('-').ifBlank { "playlist" }
    val gist = api.createGist(
      token = session.token,
      description = "AuraPulse playlist · ${playlist.name}",
      files = mapOf("$name.m3u" to builder.toString()),
      public = public,
    )
    return gist.htmlUrl
  }

  private suspend fun findBackupGist(token: String): String? =
    api.listGists(token, perPage = 100).firstOrNull { it.description == DESCRIPTION }?.id

  private fun buildPayload(tracks: List<TrackEntity>, playlists: List<PlaylistEntity>, favorites: Set<String>): JSONObject {
    val trackArray = JSONArray()
    tracks.forEach { track ->
      // A local track's uri is a content:// document grant or an app-private
      // path. The documented privacy boundary says neither ever leaves the
      // device, so only playable remote URLs are serialised.
      trackArray.put(JSONObject()
        .put("id", track.id)
        .put("uri", if (track.source == Source.LOCAL) "" else track.uri)
        .put("title", track.title)
        .put("artist", track.artist).put("album", track.album).put("albumArtist", track.albumArtist)
        .put("durationMs", track.durationMs).put("source", track.source).put("addedAt", track.addedAt)
        .put("trackNumber", track.trackNumber).put("year", track.year).put("mimeType", track.mimeType)
        .put("sizeBytes", track.sizeBytes))
    }
    val playlistArray = JSONArray()
    playlists.forEach { playlist ->
      playlistArray.put(JSONObject()
        .put("id", playlist.id).put("name", playlist.name)
        .put("trackIds", JSONArray(playlist.trackIds().take(2000)))
        .put("createdAt", playlist.createdAt).put("updatedAt", playlist.updatedAt)
        .put("accent", playlist.accent))
    }
    return JSONObject()
      .put("schema", SCHEMA)
      .put("app", "AuraPulse")
      .put("device", "${Build.MANUFACTURER} ${Build.MODEL}".trim())
      .put("createdAt", System.currentTimeMillis())
      .put("tracks", trackArray)
      .put("playlists", playlistArray)
      .put("favorites", JSONArray(favorites.toList()))
      .put("counts", JSONObject().put("tracks", tracks.size).put("playlists", playlists.size))
  }

  private fun readTracks(array: JSONArray): List<TrackEntity> = (0 until array.length()).mapNotNull { index ->
    val json = array.optJSONObject(index) ?: return@mapNotNull null
    val uri = json.optString("uri")
    if (uri.isBlank() || !json.has("id")) return@mapNotNull null
    TrackEntity(
      id = json.getString("id"), uri = uri, title = json.optString("title", "Untitled"),
      artist = json.optString("artist"), album = json.optString("album"),
      albumArtist = json.optString("albumArtist"), durationMs = json.optLong("durationMs"),
      source = json.optString("source", Source.LOCAL), addedAt = json.optLong("addedAt"),
      trackNumber = json.optInt("trackNumber"), year = json.optInt("year"),
      mimeType = json.optString("mimeType"), sizeBytes = json.optLong("sizeBytes"),
    )
  }

  private fun readPlaylists(array: JSONArray): List<PlaylistEntity> = (0 until array.length()).mapNotNull { index ->
    val json = array.optJSONObject(index) ?: return@mapNotNull null
    val id = json.optString("id")
    val name = json.optString("name")
    if (id.isBlank() || name.isBlank()) return@mapNotNull null
    val ids = json.optJSONArray("trackIds")?.let { list ->
      (0 until list.length()).mapNotNull { list.optString(it).takeIf(String::isNotBlank) }
    } ?: decodeIds(json.optString("trackIdsJson", "[]"))
    PlaylistEntity(
      id = id, name = name, trackIdsJson = encodeIds(ids),
      createdAt = json.optLong("createdAt", 0), updatedAt = json.optLong("updatedAt", 0),
      accent = json.optInt("accent"),
    )
  }

  private fun documentReadable(uri: String): Boolean = runCatching {
    when (Uri.parse(uri).scheme) {
      "file" -> java.io.File(uri.removePrefix("file://")).exists()
      else -> context.contentResolver.openFileDescriptor(Uri.parse(uri), "r")?.use { true } ?: false
    }
  }.getOrDefault(false)

  private companion object {
    const val SCHEMA = 1
    const val FILE = "aurapulse-library.json"
    const val DESCRIPTION = "AuraPulse library backup"
  }
}
