package com.hotsteel.aurapulse.playback

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SleepTimerTest {

  @After
  fun tearDown() = SleepTimer.cancel()

  @Test
  fun `starting a timer schedules a fade window in the future`() {
    SleepTimer.start(30)
    val state = SleepTimer.state.value
    val endsAt = state.endsAt

    assertNotNull(endsAt)
    assertTrue(state.active)
    assertTrue(endsAt!! > System.currentTimeMillis())
  }

  @Test
  fun `fade factor is one outside the window and zero at the end`() {
    SleepTimer.start(1)
    val endsAt = SleepTimer.state.value.endsAt!!

    assertEquals(1f, SleepTimer.fadeFactor(now = endsAt - 60_000), 0.001f)
    assertEquals(0.5f, SleepTimer.fadeFactor(now = endsAt - SleepTimer.fadeWindowMs / 2), 0.02f)
    assertEquals(0f, SleepTimer.fadeFactor(now = endsAt), 0.001f)
    assertEquals(0f, SleepTimer.fadeFactor(now = endsAt + 5_000), 0.001f)
  }

  @Test
  fun `end of track mode is active without a countdown`() {
    SleepTimer.endOfTrack()
    val state = SleepTimer.state.value

    assertTrue(state.active)
    assertTrue(state.atTrackEnd)
    assertEquals(null, state.endsAt)
    assertEquals(null, state.remainingMs())
  }

  @Test
  fun `cancelling clears everything`() {
    SleepTimer.start(15)
    SleepTimer.cancel()

    assertFalse(SleepTimer.state.value.active)
    assertEquals(1f, SleepTimer.fadeFactor(), 0.001f)
  }

  @Test
  fun `non positive minutes are ignored`() {
    SleepTimer.cancel()
    SleepTimer.start(0)
    assertFalse(SleepTimer.state.value.active)
  }
}
