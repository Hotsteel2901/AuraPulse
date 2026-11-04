package com.hotsteel.aurapulse.core

import org.junit.Assert.assertEquals
import org.junit.Test

class FormatTest {

  @Test
  fun `durations switch to hours only when needed`() {
    assertEquals("0:00", fmt.duration(0))
    assertEquals("0:07", fmt.duration(7_400))
    assertEquals("1:05", fmt.duration(65_000))
    assertEquals("1:01:05", fmt.duration(3_665_000))
  }

  @Test
  fun `negative durations are clamped instead of printing garbage`() {
    assertEquals("0:00", fmt.duration(-5_000))
  }

  @Test
  fun `byte sizes use binary units`() {
    assertEquals("0 B", fmt.bytes(0))
    assertEquals("999 B", fmt.bytes(999))
    assertEquals("1.5 KB", fmt.bytes(1536))
    assertEquals("2.0 MB", fmt.bytes(2L * 1024 * 1024))
  }

  @Test
  fun `speed drops trailing zeros`() {
    assertEquals("1", fmt.speed(1f))
    assertEquals("1.25", fmt.speed(1.25f))
    assertEquals("0.75", fmt.speed(0.75f))
  }

  @Test
  fun `day keys are stable within a day and differ across days`() {
    val morning = 1_700_000_000_000L
    assertEquals(fmt.dayKey(morning), fmt.dayKey(morning + 60_000))
    assertEquals(fmt.dayKey(morning) == fmt.dayKey(morning + 86_400_000L), false)
  }
}
