package com.hotsteel.aurapulse.data.discovery

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoveryClientTest {

  @Test
  fun `custom api templates must be https with a query placeholder`() {
    assertTrue(DiscoveryClient.isValidTemplate("https://api.example.com/search?q={query}"))
    assertFalse(DiscoveryClient.isValidTemplate("http://api.example.com/search?q={query}"))
    assertFalse(DiscoveryClient.isValidTemplate("https://api.example.com/search"))
    assertFalse(DiscoveryClient.isValidTemplate("nonsense"))
  }

  @Test
  fun `templates with embedded credentials are rejected`() {
    assertFalse(DiscoveryClient.isValidTemplate("https://user:secret@api.example.com/?q={query}"))
  }

  @Test
  fun `stream urls must be http or https without credentials`() {
    assertTrue(DiscoveryClient.isValidStream("https://cdn.example.com/a.mp3"))
    assertTrue(DiscoveryClient.isValidStream("http://cdn.example.com/a.mp3"))
    assertFalse(DiscoveryClient.isValidStream("file:///etc/passwd"))
    assertFalse(DiscoveryClient.isValidStream("ftp://cdn.example.com/a.mp3"))
    assertFalse(DiscoveryClient.isValidStream("https://user:secret@cdn.example.com/a.mp3"))
  }
}
