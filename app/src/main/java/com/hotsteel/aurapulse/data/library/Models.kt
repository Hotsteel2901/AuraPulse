package com.hotsteel.aurapulse.data.library

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.security.MessageDigest
import org.json.JSONArray

fun stableId(prefix: String, value: String): String =
  prefix + "_" + MessageDigest.getInstance("SHA-256").digest(value.toByteArray(Charsets.UTF_8))
    .take(12).joinToString("") { "%02x".format(it.toInt() and 0xff) }

object Source {
  const val LOCAL = "LOCAL"
  const val RADIO = "RADIO"
  const val PREVIEW = "PREVIEW"
  const val DIRECT = "DIRECT"
  const val CUSTOM = "CUSTOM"
}

@Entity(tableName = "tracks")
data class TrackEntity(
  @PrimaryKey val id: String,
  val uri: String,
  val title: String,
  val artist: String = "",
  val album: String = "",
  val albumArtist: String = "",
  val durationMs: Long = 0,
  val source: String = Source.LOCAL,
  val artworkUri: String? = null,
  /** A private copy of a user-supplied URL; the user's original document is never touched. */
  val downloadedPath: String? = null,
  val addedAt: Long = System.currentTimeMillis(),
  val trackNumber: Int = 0,
  val year: Int = 0,
  val mimeType: String = "",
  val sizeBytes: Long = 0,
  val folderUri: String? = null,
  val missing: Boolean = false,
) {
  val albumKey: String get() = (albumArtist.ifBlank { artist }).ifBlank { "\u0000" } + "\u0001" + album.ifBlank { "\u0000" }
  val isRemote: Boolean get() = source != Source.LOCAL
}

@Entity(tableName = "playlists")
data class PlaylistEntity(
  @PrimaryKey val id: String,
  val name: String,
  val trackIdsJson: String = "[]",
  val createdAt: Long = System.currentTimeMillis(),
  val updatedAt: Long = System.currentTimeMillis(),
  /** 0..5 selects one of the accent gradients used for the cover. */
  val accent: Int = 0,
) {
  fun trackIds(): List<String> = decodeIds(trackIdsJson)
}

@Entity(tableName = "play_events", indices = [Index("trackId"), Index("startedAt")])
data class PlayEventEntity(
  @PrimaryKey(autoGenerate = true) val id: Long = 0,
  val trackId: String,
  val startedAt: Long,
  val listenedMs: Long,
  val completed: Boolean,
)

@Entity(tableName = "lyrics")
data class LyricEntity(
  @PrimaryKey val trackId: String,
  val content: String,
  val sourceUri: String? = null,
  val offsetMs: Int = 0,
  val updatedAt: Long = System.currentTimeMillis(),
)

@Entity(tableName = "queue_state")
data class QueueStateEntity(
  @PrimaryKey val id: Int = 1,
  val trackIdsJson: String = "[]",
  val index: Int = 0,
  val positionMs: Long = 0,
  val updatedAt: Long = System.currentTimeMillis(),
)

data class PlayStat(
  val trackId: String,
  val plays: Long,
  val listenedMs: Long,
  val lastPlayedAt: Long,
)

data class DailyPlay(val dayKey: Int, val timestamp: Long, val listenedMs: Long)

fun encodeIds(ids: List<String>): String = JSONArray(ids).toString()

fun decodeIds(json: String): List<String> = runCatching {
  val array = JSONArray(json)
  (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
}.getOrDefault(emptyList())
