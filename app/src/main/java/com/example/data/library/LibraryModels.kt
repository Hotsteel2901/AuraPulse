package com.example.data.library

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.security.MessageDigest
import org.json.JSONArray

fun stableTrackId(uri: String): String = "track_" + MessageDigest.getInstance("SHA-256")
  .digest(uri.toByteArray(Charsets.UTF_8))
  .take(16).joinToString("") { "%02x".format(it.toInt() and 0xff) }

/** A URI is either a persisted document permission or an actual HTTP(S) audio URL. */
@Entity(tableName = "library_tracks")
data class LibraryTrack(
  @PrimaryKey val id: String,
  val uri: String,
  val title: String,
  val artist: String = "",
  val album: String = "",
  val durationMs: Long = 0,
  val source: String,
  val artworkUri: String? = null,
  // A private copy of a user-supplied direct URL, never the original document.
  val downloadedPath: String? = null,
  val addedAt: Long = System.currentTimeMillis(),
)

object TrackSource {
  const val LOCAL = "LOCAL"
  const val RADIO = "RADIO"
  const val PREVIEW = "PREVIEW"
  const val DIRECT = "DIRECT"
  const val CUSTOM = "CUSTOM"
  val remote = setOf(RADIO, PREVIEW, DIRECT, CUSTOM)
}

@Entity(tableName = "library_playlists")
data class LibraryPlaylist(
  @PrimaryKey val id: String,
  val name: String,
  val trackIdsJson: String = "[]",
  val updatedAt: Long = System.currentTimeMillis(),
) {
  fun trackIds(): List<String> = runCatching {
    val array = JSONArray(trackIdsJson)
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
  }.getOrDefault(emptyList())
}

fun encodeTrackIds(ids: List<String>): String = JSONArray(ids.distinct()).toString()
