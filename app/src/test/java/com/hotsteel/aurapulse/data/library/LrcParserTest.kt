package com.hotsteel.aurapulse.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LrcParserTest {

  private val sample = """
    [ti:Evening Light]
    [ar:Aura]
    [offset:100]
    [00:01.00]First line
    [00:02.50]Second line
    [00:03.00][00:04.00]Repeated chorus
    [00:05.20]
  """.trimIndent()

  @Test
  fun `parses metadata timestamps and the global offset`() {
    val lyrics = LrcParser.parse(sample)

    assertTrue(lyrics.synced)
    assertEquals("Evening Light", lyrics.title)
    assertEquals("Aura", lyrics.artist)
    // [offset:100] shifts every stamp 100 ms earlier.
    assertEquals(listOf(900L, 2400L, 2900L, 3900L, 5100L), lyrics.lines.map { it.timeMs })
    assertEquals("Repeated chorus", lyrics.lines[2].text)
  }

  @Test
  fun `supports two digit and three digit fractions`() {
    val lyrics = LrcParser.parse("[00:01.5]half\n[00:02.125]eighth")
    assertEquals(1500L, lyrics.lines[0].timeMs)
    assertEquals(2125L, lyrics.lines[1].timeMs)
  }

  @Test
  fun `keeps text without stamps as unsynchronised lines`() {
    val lyrics = LrcParser.parse("Just a line\nAnother line")
    assertFalse(lyrics.synced)
    assertEquals(2, lyrics.lines.size)
    assertEquals(listOf(0L, 4000L), lyrics.lines.map { it.timeMs })
  }

  @Test
  fun `indexAt finds the active line and returns minus one before the first stamp`() {
    val lyrics = LrcParser.parse("[00:01.00]a\n[00:02.00]b\n[00:03.00]c")

    assertEquals(-1, lyrics.indexAt(0))
    assertEquals(0, lyrics.indexAt(1000))
    assertEquals(0, lyrics.indexAt(1999))
    assertEquals(1, lyrics.indexAt(2000))
    assertEquals(2, lyrics.indexAt(60_000))
  }

  @Test
  fun `indexAt never lies for unsynchronised lyrics`() {
    val lyrics = LrcParser.parse("no timestamps here")
    assertEquals(-1, lyrics.indexAt(5_000))
  }

  @Test
  fun `empty input degrades to an empty result instead of throwing`() {
    val lyrics = LrcParser.parse("")
    assertTrue(lyrics.lines.isEmpty())
    assertFalse(lyrics.synced)
  }

  @Test
  fun `companion lyric name swaps the extension only`() {
    assertEquals("track.lrc", LrcParser.companionName("track.flac"))
    assertEquals("no-extension.lrc", LrcParser.companionName("no-extension"))
  }
}
