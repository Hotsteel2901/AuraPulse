package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.library.AudioDownloadManager
import com.example.data.library.LibraryDatabase
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.data.library.UnsupportedStreamingPlaylistException
import com.example.data.library.stableTrackId
import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Network/download persistence checks against an in-process HTTP server, not a fabricated app download. */
@RunWith(AndroidJUnit4::class)
class AudioDownloadManagerTest {
  @Test fun storesBytesAndReportsTheServersLength(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
    val payload = ByteArray(32_768) { (it % 251).toByte() }
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val worker = Thread {
        server.accept().use { socket ->
          val request = socket.getInputStream().bufferedReader()
          while (request.readLine()?.isNotEmpty() == true) { /* consume request headers */ }
          socket.getOutputStream().use { out ->
            out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${payload.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(payload)
          }
        }
      }.apply { start() }
      val url = "http://127.0.0.1:${server.localPort}/audio"
      val track = LibraryTrack(stableTrackId(url), url, "Test", source = TrackSource.DIRECT)
      database.libraryDao().insertTrack(track)
      var lastBytes = 0L
      var total: Long? = null
      try {
        AudioDownloadManager(context, database).download(track) { bytes, size -> lastBytes = bytes; total = size }
        val file = File(database.libraryDao().getTrack(track.id)!!.downloadedPath!!)
        assertArrayEquals(payload, file.readBytes())
        assertEquals(payload.size.toLong(), lastBytes)
        assertEquals(payload.size.toLong(), total)
        file.delete()
      } finally {
        worker.join(5_000)
        database.close()
      }
    }
  }

  @Test fun doesNotPretendAnHlsManifestIsAnOfflineAudioFile(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
    val manifest = "#EXTM3U\n#EXT-X-STREAM-INF:BANDWIDTH=64000\nsegment.m3u8\n".toByteArray()
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val worker = Thread {
        server.accept().use { socket ->
          val request = socket.getInputStream().bufferedReader()
          while (request.readLine()?.isNotEmpty() == true) { /* consume request headers */ }
          socket.getOutputStream().use { out ->
            // Deliberately mislabel the playlist as audio: the body must also be inspected.
            out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: ${manifest.size}\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(manifest)
          }
        }
      }.apply { start() }
      val url = "http://127.0.0.1:${server.localPort}/stream"
      val track = LibraryTrack(stableTrackId(url), url, "Test", source = TrackSource.DIRECT)
      database.libraryDao().insertTrack(track)
      try {
        var rejected = false
        try { AudioDownloadManager(context, database).download(track) { _, _ -> } }
        catch (_: UnsupportedStreamingPlaylistException) { rejected = true }
        assertTrue("Streaming manifest should not be marked as an offline file", rejected)
        assertEquals(null, database.libraryDao().getTrack(track.id)?.downloadedPath)
        assertFalse(File(context.filesDir, "downloads/${track.id}.audio").exists())
      } finally {
        worker.join(5_000)
        database.close()
      }
    }
  }

  @Test fun cancellationInterruptsTheRequestAndDeletesThePartialFile(): Unit = runBlocking {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).build()
    val firstChunkSent = CountDownLatch(1)
    val releaseServer = CountDownLatch(1)
    ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
      val worker = Thread {
        try {
          server.accept().use { socket ->
            val request = socket.getInputStream().bufferedReader()
            while (request.readLine()?.isNotEmpty() == true) { /* consume request headers */ }
            val out = socket.getOutputStream()
            out.write("HTTP/1.1 200 OK\r\nContent-Type: audio/mpeg\r\nContent-Length: 50000\r\nConnection: close\r\n\r\n".toByteArray())
            out.write(ByteArray(8192))
            out.flush()
            firstChunkSent.countDown()
            releaseServer.await(10, TimeUnit.SECONDS)
          }
        } catch (_: Exception) { /* The client closes its socket when cancelled. */ }
      }.apply { start() }
      val url = "http://127.0.0.1:${server.localPort}/slow-audio"
      val track = LibraryTrack(stableTrackId(url), url, "Test", source = TrackSource.DIRECT)
      database.libraryDao().insertTrack(track)
      val manager = AudioDownloadManager(context, database)
      val job = async(Dispatchers.IO) { manager.download(track) { _, _ -> } }
      try {
        assertTrue("Server never sent data", firstChunkSent.await(5, TimeUnit.SECONDS))
        job.cancel(CancellationException("test cancellation"))
        manager.cancel(track.id)
        job.cancelAndJoin()
        assertEquals(null, database.libraryDao().getTrack(track.id)?.downloadedPath)
        assertFalse(File(context.filesDir, "downloads/${track.id}.part").exists())
        assertFalse(File(context.filesDir, "downloads/${track.id}.audio").exists())
      } finally {
        releaseServer.countDown()
        worker.join(5_000)
        database.close()
      }
    }
  }
}
