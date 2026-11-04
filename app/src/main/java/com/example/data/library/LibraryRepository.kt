package com.example.data.library

import android.content.Context
import android.content.Intent
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import java.io.File
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext

data class ImportResult(val added: Int, val skipped: Int, val failed: Int, val limited: Boolean = false)

class LibraryRepository(private val context: Context, private val db: LibraryDatabase) {
  private val dao = db.libraryDao()
  val tracks = dao.observeTracks()
  val playlists = dao.observePlaylists()

  /** Persist the document grant before doing any work; otherwise it disappears on restart. */
  private fun persistReadPermission(uri: Uri) {
    context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
  }

  suspend fun importDocuments(uris: List<Uri>): ImportResult = withContext(Dispatchers.IO) {
    var added = 0
    var skipped = 0
    var failed = 0
    for (uri in uris.distinct()) {
      currentCoroutineContext().ensureActive()
      try {
        require(uri.scheme == "content")
        persistReadPermission(uri)
        val track = readDocument(uri)
        if (dao.insertTrack(track) != -1L) added++ else skipped++
      } catch (e: CancellationException) {
        throw e
      } catch (_: Exception) {
        failed++
      }
    }
    ImportResult(added, skipped, failed)
  }

  /** The persisted tree grant covers its descendants; do not request individual grants. */
  suspend fun importFolder(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
    persistReadPermission(uri)
    val root = DocumentFile.fromTreeUri(context, uri) ?: error("Cannot open folder")
    val queue = ArrayDeque<DocumentFile>()
    queue.add(root)
    var seen = 0
    var added = 0
    var skipped = 0
    var failed = 0
    var limited = false
    while (queue.isNotEmpty()) {
      currentCoroutineContext().ensureActive()
      val directory = queue.removeFirst()
      val children = try { directory.listFiles() } catch (_: Exception) { failed++; continue }
      for (child in children) {
        currentCoroutineContext().ensureActive()
        if (++seen > MAX_FOLDER_ENTRIES) { limited = true; break }
        if (child.isDirectory) {
          queue.add(child)
        } else if (child.isFile && isAudioDocument(child.type, child.name.orEmpty())) {
          try {
            if (dao.insertTrack(readDocument(child.uri)) != -1L) added++ else skipped++
          } catch (e: CancellationException) { throw e } catch (_: Exception) { failed++ }
        }
      }
      if (limited) break
    }
    ImportResult(added, skipped, failed, limited)
  }

  private fun readDocument(uri: Uri): LibraryTrack {
    // Opening the file first catches revoked permissions and inaccessible providers.
    context.contentResolver.openFileDescriptor(uri, "r")?.use { }
      ?: error("Cannot read document")
    val name = context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
      ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }
      .orEmpty()
    val retriever = MediaMetadataRetriever()
    var title = ""
    var artist = ""
    var album = ""
    var duration = 0L
    var artwork: String? = null
    val id = stableTrackId(uri.toString())
    try {
      retriever.setDataSource(context, uri)
      title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE).orEmpty().trim()
      artist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST).orEmpty().trim()
      album = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ALBUM).orEmpty().trim()
      duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
        ?.coerceAtLeast(0L) ?: 0L
      val picture = retriever.embeddedPicture
      if (picture != null && picture.size <= MAX_ARTWORK_BYTES) {
        val file = File(context.filesDir, "artwork/$id.img")
        file.parentFile?.mkdirs()
        file.writeBytes(picture)
        artwork = Uri.fromFile(file).toString()
      }
    } catch (_: Exception) {
      // Some providers/codecs cannot expose tags; ExoPlayer may still play the document.
    } finally { runCatching { retriever.release() } }
    return LibraryTrack(
      id = id, uri = uri.toString(), title = title.ifBlank { name.substringBeforeLast('.', name) },
      artist = artist, album = album, durationMs = duration, source = TrackSource.LOCAL,
      artworkUri = artwork,
    )
  }

  suspend fun addTrack(track: LibraryTrack): Boolean = withContext(Dispatchers.IO) {
    dao.insertTrack(track) != -1L
  }

  suspend fun deleteTrack(track: LibraryTrack) = withContext(Dispatchers.IO) {
    val removed = db.withTransaction {
      val latest = dao.getTrack(track.id)
      dao.deleteTrack(track.id)
      dao.getPlaylists().forEach { playlist ->
        if (track.id in playlist.trackIds()) {
          dao.putPlaylist(playlist.copy(
            trackIdsJson = encodeTrackIds(playlist.trackIds() - track.id),
            updatedAt = System.currentTimeMillis(),
          ))
        }
      }
      latest
    }
    // Only delete copies made by this app, never the user's original document.
    (removed?.downloadedPath ?: track.downloadedPath)?.let { path ->
      val dir = File(context.filesDir, "downloads").canonicalFile
      val file = File(path).canonicalFile
      if (file.parentFile == dir) file.delete()
    }
    if (track.source == TrackSource.LOCAL) File(context.filesDir, "artwork/${track.id}.img").delete()
  }

  suspend fun createPlaylist(name: String): LibraryPlaylist = withContext(Dispatchers.IO) {
    val playlist = LibraryPlaylist(id = "playlist_${UUID.randomUUID()}", name = name.trim().take(80))
    require(playlist.name.isNotEmpty())
    dao.putPlaylist(playlist)
    playlist
  }

  suspend fun renamePlaylist(id: String, name: String) = withContext(Dispatchers.IO) {
    require(name.isNotBlank())
    db.withTransaction {
      dao.getPlaylist(id)?.let { dao.putPlaylist(it.copy(name = name.trim().take(80), updatedAt = System.currentTimeMillis())) }
    }
  }

  suspend fun setPlaylistTracks(id: String, ids: List<String>) = withContext(Dispatchers.IO) {
    require(ids.size <= MAX_PLAYLIST_TRACKS)
    db.withTransaction {
      dao.getPlaylist(id)?.let {
        dao.putPlaylist(it.copy(trackIdsJson = encodeTrackIds(ids), updatedAt = System.currentTimeMillis()))
      }
    }
  }

  suspend fun deletePlaylist(id: String) = withContext(Dispatchers.IO) { dao.deletePlaylist(id) }

  companion object {
    const val MAX_PLAYLIST_TRACKS = 1000
    private const val MAX_FOLDER_ENTRIES = 5000
    private const val MAX_ARTWORK_BYTES = 2 * 1024 * 1024
    private val audioExtensions = setOf("mp3", "m4a", "aac", "flac", "wav", "ogg", "opus", "oga", "aif", "aiff", "mp4", "amr")
    internal fun isAudioDocument(type: String?, name: String): Boolean =
      type?.startsWith("audio/", ignoreCase = true) == true ||
        type.equals("application/ogg", true) || name.substringAfterLast('.', "").lowercase() in audioExtensions
  }
}
