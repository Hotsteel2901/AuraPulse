package com.hotsteel.aurapulse.data.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.hotsteel.aurapulse.core.fmt
import java.io.File
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val skipped: Int, val failed: Int, val limited: Boolean = false)
data class RescanResult(val added: Int, val missing: Int, val restored: Int)

class LibraryRepository(context: Context, private val db: AuraDatabase) {
  private val app = context.applicationContext
  private val dao = db.dao()
  private val folders = app.getSharedPreferences("aurapulse_folders", Context.MODE_PRIVATE)

  val tracks: Flow<List<TrackEntity>> = dao.observeTracks()
  val playlists: Flow<List<PlaylistEntity>> = dao.observePlaylists()
  val stats: Flow<Map<String, PlayStat>> = dao.observeStats().map { rows -> rows.associateBy { it.trackId } }
  val totalListeningMs: Flow<Long> = dao.observeTotalMs()
  val lyricIds: Flow<Set<String>> = dao.observeLyricIds().map { it.toSet() }

  // ---------------------------------------------------------------- imports

  private fun persist(uri: Uri) {
    runCatching { app.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION) }
  }

  suspend fun importUris(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
    var added = 0; var skipped = 0; var failed = 0
    for (uri in uris.distinct()) {
      currentCoroutineContext().ensureActive()
      try {
        persist(uri)
        val track = read(uri, folderUri = null)
        if (dao.insertTrack(track) != -1L) added++ else skipped++
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        failed++
      }
    }
    ImportResult(added, skipped, failed)
  }

  suspend fun importTree(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    persist(uri)
    folders.edit().putStringSet("trees", (folders.getStringSet("trees", emptySet()) ?: emptySet()) + uri.toString()).apply()
    val root = DocumentFile.fromTreeUri(app, uri) ?: error("unreadable folder")
    val queue = ArrayDeque<Pair<DocumentFile, String>>()
    queue.add(root to uri.toString())
    var seen = 0; var added = 0; var skipped = 0; var failed = 0; var limited = false
    while (queue.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val (directory, treeUri) = queue.removeFirst()
      val children = try { directory.listFiles() } catch (_: Exception) { failed++; continue }
      for (child in children) {
        currentCoroutineContext().ensureActive()
        if (++seen > MAX_ENTRIES) { limited = true; break }
        if (child.isDirectory) queue.add(child to treeUri)
        else if (child.isFile && isAudio(child.type, child.name.orEmpty())) {
          try {
            if (dao.insertTrack(read(child.uri, treeUri)) != -1L) added++ else skipped++
          } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++ }
        }
      }
      if (limited) break
    }
    ImportResult(added, skipped, failed, limited)
  }

  private fun read(uri: Uri, folderUri: String?): TrackEntity {
    app.contentResolver.openFileDescriptor(uri, "r")?.use { } ?: error("cannot read $uri")
    val name = app.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }.orEmpty()
    val size = app.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else 0L } ?: 0L
    val retriever = MediaMetadataRetriever()
    var title = ""; var artist = ""; var album = ""; var albumArtist = ""
    var duration = 0L; var trackNumber = 0; var year = 0; var artwork: String? = null
    val id = stableId("track", uri.toString())
    try {
      retriever.setDataSource(app, uri)
      title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
      artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
      album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
      albumArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUMARTIST).orEmpty().trim()
      duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
      trackNumber = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_CD_TRACK_NUMBER)
        ?.substringBefore('/')?.trim()?.toIntOrNull() ?: 0
      year = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_YEAR)?.take(4)?.toIntOrNull() ?: 0
      val picture = retriever.embeddedPicture
      if (picture != null && picture.size <= MAX_ARTWORK) {
        val file = File(app.filesDir, "artwork/$id.img")
        file.parentFile?.mkdirs()
        file.writeBytes(picture)
        artwork = Uri.fromFile(file).toString()
      }
    } catch (_: Exception) {
      // Tags are optional: some providers only expose a playable stream.
    } finally {
      runCatching { retriever.release() }
    }
    val fallbackTitle = name.substringBeforeLast('.', name).ifBlank { "Untitled" }
    return TrackEntity(
      id = id, uri = uri.toString(), title = title.ifBlank { fallbackTitle },
      artist = artist, album = album, albumArtist = albumArtist,
      durationMs = duration.coerceAtLeast(0), source = Source.LOCAL, artworkUri = artwork,
      trackNumber = trackNumber, year = year,
      mimeType = app.contentResolver.getType(uri).orEmpty(),
      sizeBytes = size, folderUri = folderUri,
    )
  }

  suspend fun tracksSnapshot(): List<TrackEntity> = withContext(Dispatchers.IO) { dao.tracks() }

  suspend fun playlistsSnapshot(): List<PlaylistEntity> = withContext(Dispatchers.IO) { dao.playlists() }

  suspend fun restorePlaylist(playlist: PlaylistEntity) = withContext(Dispatchers.IO) { dao.putPlaylist(playlist) }

  suspend fun add(track: TrackEntity): Boolean = withContext(Dispatchers.IO) { dao.insertTrack(track) != -1L }

  suspend fun delete(ids: Collection<String>): List<TrackEntity> = withContext(Dispatchers.IO) {
    val removed = mutableListOf<TrackEntity>()
    db.withTransaction {
      ids.forEach { id ->
        dao.track(id)?.let { track ->
          removed += track
          dao.deleteTrack(id)
          dao.playlists().forEach { playlist ->
            val current = playlist.trackIds()
            if (id in current) dao.putPlaylist(
              playlist.copy(trackIdsJson = encodeIds(current - id), updatedAt = System.currentTimeMillis()),
            )
          }
          dao.deleteLyrics(id)
        }
      }
    }
    removed.forEach { track ->
      // Only copies created by this app are removed, never the user's own document.
      track.downloadedPath?.let { path ->
        val dir = File(app.filesDir, "downloads").canonicalFile
        val file = File(path).canonicalFile
        if (file.parentFile == dir) file.delete()
      }
      if (track.source == Source.LOCAL) File(app.filesDir, "artwork/${track.id}.img").delete()
    }
    removed
  }

  suspend fun restore(tracks: List<TrackEntity>) = withContext(Dispatchers.IO) {
    tracks.forEach { dao.insertTrack(it.copy(missing = false)) }
  }

  suspend fun markMissing(id: String, missing: Boolean) = withContext(Dispatchers.IO) { dao.setMissing(id, missing) }

  /** Verifies every local document, then walks remembered folder trees for new files. */
  suspend fun rescan(): RescanResult = withContext(Dispatchers.IO) {
    var missing = 0; var restored = 0
    val all = dao.tracks()
    for (track in all) {
      if (track.source != Source.LOCAL) continue
      val exists = runCatching {
        when (Uri.parse(track.uri).scheme) {
          "file" -> File(track.uri.removePrefix("file://")).exists()
          else -> app.contentResolver.openFileDescriptor(Uri.parse(track.uri), "r")?.use { true } ?: false
        }
      }.getOrDefault(false)
      if (!exists && !track.missing) { dao.setMissing(track.id, true); missing++ }
      if (exists && track.missing) { dao.setMissing(track.id, false); restored++ }
    }
    var added = 0
    folders.getStringSet("trees", emptySet())?.toList()?.forEach { tree ->
      val result = runCatching { importTree(Uri.parse(tree)) }.getOrNull()
      added += result?.added ?: 0
    }
    RescanResult(added, missing, restored)
  }

  fun folderTrees(): List<String> = folders.getStringSet("trees", emptySet())?.toList().orEmpty()

  fun forgetFolder(uri: String) {
    folders.edit().putStringSet("trees", (folders.getStringSet("trees", emptySet()) ?: emptySet()) - uri).apply()
  }

  // ---------------------------------------------------------------- playlists

  suspend fun createPlaylist(name: String): PlaylistEntity? = withContext(Dispatchers.IO) {
    val clean = name.trim().take(80)
    if (clean.isEmpty()) return@withContext null
    val playlist = PlaylistEntity(
      id = "pl_" + UUID.randomUUID().toString().take(8),
      name = clean,
      accent = (0..5).random(),
    )
    dao.putPlaylist(playlist)
    playlist
  }

  suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) {
    val clean = name.trim().take(80)
    if (clean.isEmpty()) return@withContext
    dao.playlist(id)?.let { dao.putPlaylist(it.copy(name = clean, updatedAt = System.currentTimeMillis())) }
  }

  suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) { dao.deletePlaylist(id) }

  suspend fun duplicatePlaylist(id: String): PlaylistEntity? = withContext(Dispatchers.IO) {
    val source = dao.playlist(id) ?: return@withContext null
    val copy = source.copy(
      id = "pl_" + UUID.randomUUID().toString().take(8),
      name = source.name.take(70) + " copy",
      createdAt = System.currentTimeMillis(), updatedAt = System.currentTimeMillis(),
      accent = (source.accent + 1) % 6,
    )
    dao.putPlaylist(copy)
    copy
  }

  suspend fun setPlaylistTracks(id: String, ids: List<String>) = withContext(Dispatchers.IO) {
    if (ids.size > MAX_PLAYLIST) return@withContext
    dao.playlist(id)?.let {
      dao.putPlaylist(it.copy(trackIdsJson = encodeIds(ids.distinct()), updatedAt = System.currentTimeMillis()))
    }
  }

  suspend fun addToPlaylist(id: String, trackId: String) = withContext(Dispatchers.IO) {
    dao.playlist(id)?.let { playlist ->
      val ids = playlist.trackIds()
      if (trackId !in ids && ids.size < MAX_PLAYLIST) {
        dao.putPlaylist(playlist.copy(trackIdsJson = encodeIds(ids + trackId), updatedAt = System.currentTimeMillis()))
      }
    }
  }

  suspend fun removeFromPlaylist(id: String, trackId: String) = withContext(Dispatchers.IO) {
    dao.playlist(id)?.let { playlist ->
      dao.putPlaylist(playlist.copy(
        trackIdsJson = encodeIds(playlist.trackIds() - trackId), updatedAt = System.currentTimeMillis(),
      ))
    }
  }

  // ---------------------------------------------------------------- history

  suspend fun recordPlay(trackId: String, listenedMs: Long, completed: Boolean) = withContext(Dispatchers.IO) {
    if (listenedMs < 3_000 && !completed) return@withContext
    dao.insertEvent(PlayEventEntity(
      trackId = trackId, startedAt = System.currentTimeMillis(),
      listenedMs = listenedMs.coerceAtLeast(0), completed = completed,
    ))
  }

  suspend fun clearListening() = withContext(Dispatchers.IO) { dao.clearEvents() }

  suspend fun topStats(): List<PlayStat> = withContext(Dispatchers.IO) { dao.stats() }

  suspend fun eventsSince(since: Long): List<PlayEventEntity> = withContext(Dispatchers.IO) { dao.eventsSince(since) }

  suspend fun totalMs(): Long = withContext(Dispatchers.IO) { dao.totalMs() }

  // ---------------------------------------------------------------- lyrics

  suspend fun lyrics(trackId: String): LyricEntity? = withContext(Dispatchers.IO) { dao.lyrics(trackId) }

  suspend fun putLyrics(entity: LyricEntity) = withContext(Dispatchers.IO) { dao.putLyrics(entity) }

  suspend fun deleteLyrics(trackId: String) = withContext(Dispatchers.IO) { dao.deleteLyrics(trackId) }

  // ---------------------------------------------------------------- queue

  suspend fun saveQueue(trackIds: List<String>, index: Int, positionMs: Long) = withContext(Dispatchers.IO) {
    dao.putQueueState(QueueStateEntity(1, encodeIds(trackIds), index, positionMs, System.currentTimeMillis()))
  }

  suspend fun loadQueue(): QueueStateEntity? = withContext(Dispatchers.IO) { dao.queueState() }

  suspend fun storageBytes(): Long = withContext(Dispatchers.IO) {
    listOf("downloads", "artwork").sumOf { dir -> File(app.filesDir, dir).walkTopDown().filter { it.isFile }.sumOf { it.length() } }
  }

  fun clearArtworkCache() {
    File(app.filesDir, "artwork").listFiles()?.forEach { it.delete() }
  }

  companion object {
    const val MAX_PLAYLIST = 2000
    private const val MAX_ENTRIES = 8000
    private const val MAX_ARTWORK = 4 * 1024 * 1024
    private val extensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga", "aif", "aiff", "mp4", "amr", "mka", "wma")

    fun isAudio(mime: String?, name: String): Boolean =
      mime?.startsWith("audio/", true) == true || mime.equals("application/ogg", true) ||
        name.substringAfterLast('.', "").lowercase(Locale.US) in extensions

    fun humanDuration(ms: Long): String = fmt.duration(ms)
  }
}
