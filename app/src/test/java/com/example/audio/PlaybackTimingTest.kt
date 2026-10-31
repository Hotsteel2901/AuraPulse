package com.example.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackTimingTest {
  @Test fun countdownUsesMonotonicDeadlineAndRoundsUp() {
    assertEquals(60, remainingSleepSeconds(61_000L, 1_000L))
    assertEquals(1, remainingSleepSeconds(61_000L, 60_999L))
    assertEquals(0, remainingSleepSeconds(61_000L, 61_000L))
    assertEquals(0, remainingSleepSeconds(61_000L, 75_000L))
  }

  @Test fun loopNeedsValidPointsAndOnlySeeksAtOrAfterB() {
    assertFalse(shouldLoop(9_000, null, 5_000))
    assertFalse(shouldLoop(9_000, 1_000, 1_499))
    assertFalse(shouldLoop(4_999, 1_000, 5_000))
    assertTrue(shouldLoop(5_000, 1_000, 5_000))
    assertTrue(shouldLoop(5_200, 1_000, 5_000))
  }
}
