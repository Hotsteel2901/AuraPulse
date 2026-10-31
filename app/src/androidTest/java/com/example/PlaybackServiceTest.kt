package com.example

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.audio.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs on a device/emulator: the service must decode a real WAV and report real playback progress. */
@RunWith(AndroidJUnit4::class)
class PlaybackServiceTest {
  @Test fun playsAndSeeksAnActualFileThroughTheMediaSession() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val audio = File(context.cacheDir, "playback-test.wav")
    writeWav(audio)
    val instrumentation = InstrumentationRegistry.getInstrumentation()
    lateinit var future: ListenableFuture<MediaController>
    instrumentation.runOnMainSync {
      future = MediaController.Builder(context, SessionToken(context, ComponentName(context, PlaybackService::class.java))).buildAsync()
    }
    val controller = future.get(15, TimeUnit.SECONDS)
    val ready = CountDownLatch(1)
    val playing = CountDownLatch(1)
    val seeked = CountDownLatch(1)
    try {
      instrumentation.runOnMainSync {
        controller.addListener(object : Player.Listener {
          override fun onPlaybackStateChanged(state: Int) { if (state == Player.STATE_READY) ready.countDown() }
          override fun onIsPlayingChanged(isPlaying: Boolean) { if (isPlaying) playing.countDown() }
          override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
            if (reason == Player.DISCONTINUITY_REASON_SEEK && newPosition.positionMs in 950..1050) seeked.countDown()
          }
        })
        controller.setMediaItem(MediaItem.fromUri(Uri.fromFile(audio)))
        controller.prepare()
        controller.play()
      }
      assertTrue("WAV never became ready", ready.await(15, TimeUnit.SECONDS))
      assertTrue("Playback did not start", playing.await(15, TimeUnit.SECONDS))
      Thread.sleep(500)
      var position = 0L
      instrumentation.runOnMainSync { position = controller.currentPosition; controller.pause(); controller.seekTo(1000) }
      assertTrue("Playback position did not advance", position > 100)
      assertTrue("Seek was not reported by the media session", seeked.await(10, TimeUnit.SECONDS))
    } finally {
      instrumentation.runOnMainSync { controller.pause(); MediaController.releaseFuture(future) }
      audio.delete()
    }
  }

  private fun writeWav(file: File) {
    val sampleRate = 16000
    val samples = sampleRate * 3
    val pcm = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
    pcm.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
    pcm.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate)
    pcm.putInt(sampleRate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
    repeat(samples) { i -> pcm.putShort((kotlin.math.sin(i * 2.0 * Math.PI * 440 / sampleRate) * 8000).toInt().toShort()) }
    file.writeBytes(pcm.array())
  }
}
