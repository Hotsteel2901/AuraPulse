package com.example.audio

import android.content.Context
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.test.core.app.ApplicationProvider
import com.example.data.library.ListeningStore
import java.lang.reflect.Proxy
import java.time.Duration
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.LooperMode

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
@LooperMode(LooperMode.Mode.PAUSED)
class ServiceListeningTrackerTest {
  @Test fun serviceKeepsSavingPositionAfterUiIsGoneAndResetsAtEnd() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    context.getSharedPreferences("listening", Context.MODE_PRIVATE).edit().clear().commit()
    var position = 1_000L
    var playing = true
    var playbackState = Player.STATE_READY
    val item = MediaItem.Builder().setMediaId("service-track").setUri("file:///music.wav").build()
    val player = Proxy.newProxyInstance(Player::class.java.classLoader, arrayOf(Player::class.java)) { _, method, _ ->
      when (method.name) {
        "addListener", "removeListener" -> null
        "isPlaying", "getPlayWhenReady", "isCurrentMediaItemSeekable" -> playing
        "getCurrentMediaItem" -> item
        "getCurrentPosition" -> position
        "getPlaybackState" -> playbackState
        else -> when (method.returnType) {
          java.lang.Boolean.TYPE -> false
          java.lang.Integer.TYPE -> 0
          java.lang.Long.TYPE -> 0L
          java.lang.Float.TYPE -> 0f
          java.lang.Double.TYPE -> 0.0
          else -> null
        }
      }
    } as Player
    val listening = ListeningStore.get(context)
    val tracker = ServiceListeningTracker(context, player)
    assertTrue("service-track" in listening.history.value)
    position = 8_000L
    shadowOf(Looper.getMainLooper()).idleFor(Duration.ofSeconds(5))
    assertEquals(8_000L, listening.resume.value?.positionMs)
    playing = false
    playbackState = Player.STATE_ENDED
    tracker.onPlaybackStateChanged(Player.STATE_ENDED)
    assertEquals(0L, listening.resume.value?.positionMs)
    tracker.close()
  }
}
