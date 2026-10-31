package com.example.data.library

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioResponseTest {
  @Test fun detectsAdaptivePlaylistsEvenWhenTheServerMislabelsThem() {
    assertTrue(AudioResponse.isStreamingPlaylist("audio/x-mpegurl", "/stream", byteArrayOf()))
    assertTrue(AudioResponse.isStreamingPlaylist("application/octet-stream", "/master.m3u8", byteArrayOf()))
    assertTrue(AudioResponse.isStreamingPlaylist("audio/mpeg", "/stream", "  #EXTM3U\n#EXT-X-VERSION:3".toByteArray()))
    assertTrue(AudioResponse.isStreamingPlaylist("application/octet-stream", "/stream", "<?xml version=\"1.0\"?><MPD type=\"static\">".toByteArray()))
    assertFalse(AudioResponse.isStreamingPlaylist("audio/mpeg", "/song.mp3", byteArrayOf(0x49, 0x44, 0x33)))
  }
}
