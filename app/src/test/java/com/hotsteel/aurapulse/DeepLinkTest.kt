package com.hotsteel.aurapulse

import android.content.Intent
import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * External VIEW/SEND intents are attacker-controlled. An opaque
 * `aurapulse:foo` link is not hierarchical, and Uri.getQueryParameter throws
 * UnsupportedOperationException on it — that used to crash onCreate.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeepLinkTest {

  @Test
  fun `an opaque aurapulse link is ignored instead of crashing`() {
    assertEquals(
      DeepLinkTarget.Ignore,
      resolveDeepLink(Intent.ACTION_VIEW, Uri.parse("aurapulse:foo"), null),
    )
  }

  @Test
  fun `a hierarchical aurapulse link carries its stream`() {
    val target = resolveDeepLink(
      Intent.ACTION_VIEW,
      Uri.parse("aurapulse://share?url=https%3A%2F%2Fexample.com%2Fa.mp3&title=Night"),
      null,
    )
    assertEquals(DeepLinkTarget.Stream("Night", "https://example.com/a.mp3"), target)
  }

  @Test
  fun `a hierarchical link without a url is ignored`() {
    assertEquals(
      DeepLinkTarget.Ignore,
      resolveDeepLink(Intent.ACTION_VIEW, Uri.parse("aurapulse://share"), null),
    )
  }

  @Test
  fun `an http view intent becomes a stream`() {
    assertEquals(
      DeepLinkTarget.Stream("song", "https://example.com/song.mp3"),
      resolveDeepLink(Intent.ACTION_VIEW, Uri.parse("https://example.com/song.mp3"), null),
    )
  }

  @Test
  fun `a content send intent is imported`() {
    val uri = Uri.parse("content://media/external/audio/media/18")
    assertEquals(
      DeepLinkTarget.Import(uri),
      resolveDeepLink(Intent.ACTION_SEND, null, uri),
    )
  }

  @Test
  fun `unrelated intents are ignored`() {
    assertEquals(DeepLinkTarget.Ignore, resolveDeepLink(Intent.ACTION_MAIN, null, null))
  }
}
