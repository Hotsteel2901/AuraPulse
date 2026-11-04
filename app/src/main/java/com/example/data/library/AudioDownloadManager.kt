package com.example.data.library

import android.content.Context
import androidx.room.withTransaction
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Call
import okhttp3.Request
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Downloads only explicitly supplied, finite direct audio files; never radio or preview streams. */
class AudioDownloadManager(private val context: Context, private val db: LibraryDatabase) {
  private val client = OkHttpClient.Builder().connectTimeout(15, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS).callTimeout(15, TimeUnit.MINUTES).build()
  private class ActiveCall(val call: Call, val userCancelled: AtomicBoolean = AtomicBoolean(false))
  private val calls = ConcurrentHashMap<String, ActiveCall>()
  fun cancel(trackId: String) {
    calls[trackId]?.let { active -> active.userCancelled.set(true); active.call.cancel() }
  }

  suspend fun download(track: LibraryTrack, onBytes: (Long, Long?) -> Unit) = withContext(Dispatchers.IO) {
    require(track.source == TrackSource.DIRECT || track.source == TrackSource.CUSTOM)
    val url = track.uri.toHttpUrlOrNull() ?: error("Invalid audio URL")
    require(url.username.isEmpty() && url.password.isEmpty())
    val dir = File(context.filesDir, "downloads").apply { mkdirs() }
    val partial = File(dir, "${track.id}.part")
    val target = File(dir, "${track.id}.audio")
    val call = client.newCall(Request.Builder().url(url).header("User-Agent", "AuraPulse/1.0").build())
    val active = ActiveCall(call)
    calls[track.id] = active
    var committed = false
    try {
      currentCoroutineContext().ensureActive()
      call.execute().use { response ->
        if (!response.isSuccessful) error("HTTP ${response.code}")
        val body = response.body ?: error("Empty response")
        val length = body.contentLength().takeIf { it >= 0 }
        if (length != null && length > MAX_BYTES) error("File exceeds the download limit")
        val type = response.header("Content-Type").orEmpty().substringBefore(';').trim().lowercase()
        val path = response.request.url.encodedPath
        if (AudioResponse.isStreamingPlaylist(type, path, ByteArray(0))) throw UnsupportedStreamingPlaylistException()
        require(type.isEmpty() || type.startsWith("audio/") || type in setOf("application/octet-stream", "application/ogg", "video/mp4")) {
          "The response is not an audio file ($type)"
        }
        var written = 0L
        body.byteStream().buffered().use { input ->
          // Inspect the start too: some servers mislabel manifests as audio/octet-stream.
          input.mark(1024)
          val sample = ByteArray(512)
          var sampled = 0
          while (sampled < sample.size) {
            currentCoroutineContext().ensureActive()
            val count = input.read(sample, sampled, sample.size - sampled)
            if (count < 0) break
            sampled += count
          }
          input.reset()
          if (AudioResponse.isStreamingPlaylist(type, path, sample.copyOf(sampled))) throw UnsupportedStreamingPlaylistException()
          partial.outputStream().buffered().use { output ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
              currentCoroutineContext().ensureActive()
              val count = input.read(buffer)
              if (count < 0) break
              written += count
              if (written > MAX_BYTES) error("File exceeds the download limit")
              output.write(buffer, 0, count)
              onBytes(written, length)
            }
          }
        }
        check(written > 0 && (length == null || written == length)) { "Incomplete download" }
      }
      check(partial.renameTo(target)) { "Could not save the audio file" }
      db.withTransaction {
        // The track might have been removed while the network request was running.
        val latest = db.libraryDao().getTrack(track.id)
        if (latest != null) db.libraryDao().putTrack(latest.copy(downloadedPath = target.absolutePath))
        else target.delete()
      }
      committed = true
    } catch (error: IOException) {
      // OkHttp aborts a blocking read with a SocketException. If the caller
      // cancelled, preserve coroutine cancellation instead of reporting a
      // spurious download failure (or failing its parent scope).
      currentCoroutineContext().ensureActive()
      if (active.userCancelled.get()) throw CancellationException("Download cancelled").also { it.initCause(error) }
      throw error
    } finally {
      calls.remove(track.id, active)
      partial.delete()
      if (!committed) {
        // Cancellation can arrive just after Room commits but before this coroutine resumes.
        // Never delete a file that the committed row already references.
        val referenced = withContext(NonCancellable) {
          runCatching { db.libraryDao().getTrack(track.id)?.downloadedPath == target.absolutePath }
            .getOrDefault(true)
        }
        if (!referenced) target.delete()
      }
    }
  }

  suspend fun removeDownload(track: LibraryTrack) = withContext(Dispatchers.IO) {
    val path = db.withTransaction {
      val latest = db.libraryDao().getTrack(track.id) ?: return@withTransaction null
      val saved = latest.downloadedPath
      if (saved != null) db.libraryDao().putTrack(latest.copy(downloadedPath = null))
      saved
    } ?: return@withContext
    val dir = File(context.filesDir, "downloads").canonicalFile
    val file = File(path).canonicalFile
    if (file.parentFile == dir) file.delete()
  }

  companion object { private const val MAX_BYTES = 512L * 1024 * 1024 }
}
