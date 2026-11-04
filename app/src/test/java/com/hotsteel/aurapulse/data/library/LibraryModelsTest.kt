package com.hotsteel.aurapulse.data.library

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LibraryModelsTest {

  @Test
  fun `stable ids are deterministic per value and differ across values`() {
    val first = stableId("url", "https://example.com/a.mp3")
    val again = stableId("url", "https://example.com/a.mp3")
    val other = stableId("url", "https://example.com/b.mp3")

    assertEquals(first, again)
    assertNotEquals(first, other)
    assertTrue(first.startsWith("url_"))
  }

  @Test
  fun `stable ids survive an id prefix change without colliding`() {
    assertNotEquals(stableId("url", "x"), stableId("remote", "x"))
  }

  @Test
  fun `id lists round trip through json`() {
    val ids = listOf("a", "b", "c")
    assertEquals(ids, decodeIds(encodeIds(ids)))
  }

  @Test
  fun `corrupt id json decodes to an empty list`() {
    assertEquals(emptyList<String>(), decodeIds("not json"))
    assertEquals(emptyList<String>(), decodeIds(""))
    assertEquals(listOf("a"), decodeIds("""["a","",""]"""))
  }

  @Test
  fun `album key groups compilations by album artist`() {
    val one = TrackEntity(id = "1", uri = "u", title = "t", artist = "A", album = "LP", albumArtist = "Various")
    val two = TrackEntity(id = "2", uri = "u", title = "t", artist = "B", album = "LP", albumArtist = "Various")
    val three = TrackEntity(id = "3", uri = "u", title = "t", artist = "A", album = "LP")

    assertEquals(one.albumKey, two.albumKey)
    assertNotEquals(one.albumKey, three.albumKey)
  }

  @Test
  fun `remote sources are flagged as remote`() {
    assertTrue(TrackEntity(id = "1", uri = "u", title = "t", source = Source.RADIO).isRemote)
    assertTrue(!TrackEntity(id = "2", uri = "u", title = "t", source = Source.LOCAL).isRemote)
  }
}
