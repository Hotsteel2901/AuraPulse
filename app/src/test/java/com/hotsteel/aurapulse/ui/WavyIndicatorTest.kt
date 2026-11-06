package com.hotsteel.aurapulse.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The player feeds waveSpeed = 0.dp and amplitude = 0f whenever playback stops.
 * Material3 derives the wave animation duration from those values, so the
 * transition between "playing" and "paused" is exercised here in isolation.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class WavyIndicatorTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun togglingTheWaveBetweenMovingAndStillCompletes() {
    compose.mainClock.autoAdvance = false
    var playing by mutableStateOf(true)
    compose.setContent {
      Box(Modifier.fillMaxWidth()) {
        LinearWavyProgressIndicator(
          progress = { 0.4f },
          modifier = Modifier.fillMaxWidth(),
          waveSpeed = if (playing) 16.dp else 0.dp,
          amplitude = { if (playing) 1f else 0f },
        )
      }
    }
    compose.mainClock.advanceTimeBy(64)

    playing = false
    compose.mainClock.advanceTimeBy(64)

    playing = true
    compose.mainClock.advanceTimeBy(64)

    assertTrue(true)
  }
}
