package com.hotsteel.aurapulse.data.library

import android.content.Context
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.OkHttpClient
import okhttp3.Request

class SegmentedStreamException : IOException("segmented stream")
class DownloadTooLargeException : IOException("too large")

/**
 * Downloads a user-supplied direct URL into app-private storage. Only a fully
 * received file is moved into place, so a partial download is never reported as
 * available. Segmented playlists (HLS/DASH) are rejected rather than faked.
 */
class DownloadManager(context: Context) {
  private val app = context.applicationContext
  private val client = OkHttpClient.Builder()
    .connectTimeout(20, TimeUnit.SECONDS)
    .readTimeout(30, TimeUnit.SECONDS)
    .build()
  private val running = mutableMapOf<String, Call>()

  suspend fun download(track: TrackEntity, onProgress: (Long, Long?) -> Unit): String = withContext(Dispatchers.IO) {
    if (track.uri.contains(".m3u8", true) || track.uri.contains(".mpd", true)) throw SegmentedStreamException()
    val dir = File(app.filesDir, "downloads").apply { mkdirs() }
    val target = File(dir, "${track.id}.${extension(track)}")
    if (target.isFile) return@withContext target.absolutePath
    val partial = File(dir, "${track.id}.part")
    val request = Request.Builder().url(track.uri).header("User-Agent", "AuraPulse/2.0").build()
    val call = client.newCall(request)
    running[track.id] = call
    try {
      call.execute().use { response ->
        if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
        val type = response.header("Content-Type").orEmpty().lowercase()
        if (type.contains("mpegurl") || type.contains("dash+xml")) throw SegmentedStreamException()
        val length = response.body?.contentLength()?.takeIf { it > 0 }
        if (length != null && length > MAX_BYTES) throw DownloadTooLargeException()
        val sink = partial.outputStream().buffered()
        var total = 0L
        var lastReport = 0L
        try {
          response.body?.byteStream()?.use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
              val read = input.read(buffer)
              if (read < 0) break
              total += read
              if (total > MAX_BYTES) throw DownloadTooLargeException()
              sink.write(buffer, 0, read)
              val now = System.currentTimeMillis()
              if (now - lastReport > 200) {
                lastReport = now
                onProgress(total, length)
              }
            }
          }
          sink.flush()
        } finally {
          runCatching { sink.close() }
        }
        if (total <= 0) throw IOException("empty response")
        if (!partial.renameTo(target)) {
          partial.copyTo(target, overwrite = true)
          partial.delete()
        }
        onProgress(total, length ?: total)
        target.absolutePath
      }
    } catch (e: CancellationException) {
      partial.delete()
      throw e
    } catch (e: Exception) {
      partial.delete()
      throw e
    } finally {
      running.remove(track.id)
    }
  }

  fun cancel(id: String) {
    running.remove(id)?.cancel()
  }

  fun remove(path: String?) {
    val file = path?.let(::File) ?: return
    val dir = File(app.filesDir, "downloads").canonicalFile
    if (file.canonicalFile.parentFile == dir) file.delete()
  }

  private fun extension(track: TrackEntity): String {
    val fromMime = when {
      track.mimeType.contains("mpeg") -> "mp3"
      track.mimeType.contains("mp4") || track.mimeType.contains("m4a") -> "m4a"
      track.mimeType.contains("flac") -> "flac"
      track.mimeType.contains("ogg") -> "ogg"
      track.mimeType.contains("opus") -> "opus"
      track.mimeType.contains("wav") -> "wav"
      else -> null
    }
    if (fromMime != null) return fromMime
    val ext = track.uri.substringBefore('?').substringAfterLast('.', "").lowercase()
    return if (ext.length in 2..5 && ext.all { it.isLetterOrDigit() }) ext else "audio"
  }

  private companion object {
    const val MAX_BYTES = 512L * 1024 * 1024
  }
}
