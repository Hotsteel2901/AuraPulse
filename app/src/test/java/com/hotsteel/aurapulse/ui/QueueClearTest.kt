package com.hotsteel.aurapulse.ui

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * "Clear queue" must leave exactly the track that is playing.
 *
 * The old implementation filtered by an index captured before any removal, so
 * removing an item before the current one shifted the rest and the loop started
 * deleting the wrong entries — including the playing track. These cases cover
 * the current item at the start, in the middle and at the end of the queue.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class QueueClearTest {

  @Test
  fun `keeps the playing track when it is first`() = withQueue(current = 0) { player ->
    clearQueueKeepingCurrent(player)
    assertEquals(1, player.mediaItemCount)
    assertEquals("track-0", player.getMediaItemAt(0).mediaId)
  }

  @Test
  fun `keeps the playing track when it is in the middle`() = withQueue(current = 2) { player ->
    clearQueueKeepingCurrent(player)
    assertEquals(1, player.mediaItemCount)
    assertEquals("track-2", player.getMediaItemAt(0).mediaId)
  }

  @Test
  fun `keeps the playing track when it is last`() = withQueue(current = 5) { player ->
    clearQueueKeepingCurrent(player)
    assertEquals(1, player.mediaItemCount)
    assertEquals("track-5", player.getMediaItemAt(0).mediaId)
  }

  @Test
  fun `single item queue is left alone`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val player = ExoPlayer.Builder(context).build()
    try {
      player.setMediaItem(MediaItem.fromUri("https://example.invalid/only.mp3"))
      clearQueueKeepingCurrent(player)
      assertEquals(1, player.mediaItemCount)
    } finally {
      player.release()
    }
  }

  private fun withQueue(current: Int, block: (ExoPlayer) -> Unit) {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val player = ExoPlayer.Builder(context).build()
    try {
      val items = (0..5).map { index ->
        MediaItem.Builder()
          .setMediaId("track-$index")
          .setUri("https://example.invalid/track-$index.mp3")
          .build()
      }
      player.setMediaItems(items, current, 0L)
      block(player)
    } finally {
      player.release()
    }
  }
}
