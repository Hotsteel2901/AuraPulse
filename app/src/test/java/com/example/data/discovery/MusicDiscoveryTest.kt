package com.example.data.discovery

import com.example.data.library.TrackSource
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class MusicDiscoveryTest {
  private val discovery = MusicDiscovery()

  @Test fun previewsOnlyIncludeProvidedUrlsAndNeverClaimFullSongDuration() {
    val json = """{"results":[
      {"trackName":"Sample","artistName":"Artist","trackTimeMillis":240000,"previewUrl":"https://audio.example/sample.m4a"},
      {"trackName":"No audio"}
    ]}"""
    val result = discovery.parseResults(DiscoverySource.PREVIEWS, json, "https://itunes.apple.com/search")
    assertEquals(1, result.size)
    assertEquals(TrackSource.PREVIEW, result[0].source)
    assertEquals(0L, result[0].durationMs)
    assertEquals("https://audio.example/sample.m4a", result[0].uri)
  }

  @Test fun radioUsesTheActualStreamAndDoesNotInventOne() {
    val json = """[{"name":"Station","url_resolved":"http://radio.example/live"},{"name":"No stream"}]"""
    val result = discovery.parseResults(DiscoverySource.RADIO, json, "https://radio.example")
    assertEquals(1, result.size)
    assertEquals("http://radio.example/live", result[0].uri)
    assertEquals(TrackSource.RADIO, result[0].source)
  }

  @Test fun customJsonAcceptsRelativeAudioButRejectsMissingAndCredentialUrls() {
    val json = """{"tracks":[
      {"title":"Real","streamUrl":"/audio.mp3"},
      {"title":"Missing"},
      {"title":"Credentials","streamUrl":"https://name:secret@host.example/audio"}
    ]}"""
    val result = discovery.parseResults(DiscoverySource.CUSTOM, json, "https://host.example/search")
    assertEquals(1, result.size)
    assertEquals("https://host.example/audio.mp3", result.single().uri)
  }

  @Test fun malformedResponseIsAnErrorNotAValidEmptyCatalog() {
    assertThrows(Exception::class.java) { discovery.parseResults(DiscoverySource.CUSTOM, "not json", "https://example.org") }
  }
}
