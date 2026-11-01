package com.example

import android.net.Uri
import androidx.compose.ui.test.assertDoesNotExist
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.ViewModelProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.ui.player.MusicViewModel
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Device test: explicit play opens the whole window, while a later next-track does not. */
@RunWith(AndroidJUnit4::class)
class FullPlayerTest {
  @get:Rule val compose = createAndroidComposeRule<MainActivity>()

  @Test fun explicitPlayOpensFullScreenAndCanBeMinimized() {
    val activity = compose.activity
    val audio = File(activity.cacheDir, "full-player-test.wav").also(::writeWav)
    val vm = ViewModelProvider(activity)[MusicViewModel::class.java]
    val tracks = listOf("One", "Two").map { name ->
      LibraryTrack(id = "full-player-test-$name", uri = Uri.fromFile(audio).toString(),
        title = "Full player $name", artist = "Device test", source = TrackSource.LOCAL)
    }
    try {
      compose.waitUntil(timeoutMillis = 15_000) {
        var ready = false
        compose.runOnUiThread { ready = vm.playerReady }
        ready
      }
      compose.runOnUiThread { vm.play(tracks.first(), tracks) }
      compose.onNodeWithTag("now-playing-screen").assertIsDisplayed()
      val rootSize = compose.onRoot().fetchSemanticsNode().size
      val playerSize = compose.onNodeWithTag("now-playing-screen").fetchSemanticsNode().size
      assertEquals("Player should occupy the window width", rootSize.width, playerSize.width)
      assertEquals("Player should occupy the window height", rootSize.height, playerSize.height)
      compose.onNodeWithText(tracks.first().title).assertIsDisplayed()
      compose.onNodeWithContentDescription(activity.getString(R.string.track_progress)).assertIsDisplayed()
      compose.onNodeWithContentDescription(activity.getString(R.string.minimize_player)).performClick()
      compose.onNodeWithTag("now-playing-screen").assertDoesNotExist()
      compose.runOnUiThread { vm.next() }
      compose.onNodeWithTag("now-playing-screen").assertDoesNotExist()
      compose.waitUntil(timeoutMillis = 5_000) { vm.playback.value.track?.id == tracks.last().id }
      compose.onNodeWithText(tracks.last().title).performClick()
      compose.onNodeWithTag("now-playing-screen").assertIsDisplayed()
      compose.runOnUiThread { activity.onBackPressedDispatcher.onBackPressed() }
      compose.onNodeWithTag("now-playing-screen").assertDoesNotExist()
      compose.runOnUiThread { vm.play(tracks.first(), tracks) }
      compose.onNodeWithTag("now-playing-screen").assertIsDisplayed()
    } finally {
      compose.runOnUiThread { if (vm.playback.value.isPlaying) vm.togglePlay() }
      audio.delete()
    }
  }

  private fun writeWav(file: File) {
    val sampleRate = 16_000
    val samples = sampleRate * 8
    val pcm = ByteBuffer.allocate(44 + samples * 2).order(ByteOrder.LITTLE_ENDIAN)
    pcm.put("RIFF".toByteArray()).putInt(36 + samples * 2).put("WAVE".toByteArray())
    pcm.put("fmt ".toByteArray()).putInt(16).putShort(1).putShort(1).putInt(sampleRate)
    pcm.putInt(sampleRate * 2).putShort(2).putShort(16).put("data".toByteArray()).putInt(samples * 2)
    repeat(samples) { index -> pcm.putShort((kotlin.math.sin(index * 2.0 * Math.PI * 440 / sampleRate) * 8000).toInt().toShort()) }
    file.writeBytes(pcm.array())
  }
}
