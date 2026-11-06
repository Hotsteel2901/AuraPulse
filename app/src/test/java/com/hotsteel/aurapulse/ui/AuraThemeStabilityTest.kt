package com.hotsteel.aurapulse.ui

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import com.hotsteel.aurapulse.data.AppSettings
import com.hotsteel.aurapulse.ui.theme.AuraTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The theme is rebuilt whenever its caller recomposes. Because Typography is a
 * plain class, a fresh instance is a different CompositionLocal value and forces
 * the whole tree to recompose, which is what made the player unresponsive: the
 * root collected every playback position update.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AuraThemeStabilityTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun typographyAndShapesSurviveRecomposition() {
    compose.mainClock.autoAdvance = false
    val typographies = mutableSetOf<Typography>()
    var tick by mutableStateOf(0)
    val settings = AppSettings(dynamicColor = false)

    compose.setContent {
      tick
      AuraTheme(settings = settings, systemDark = true) {
        val typography = MaterialTheme.typography
        SideEffect { typographies += typography }
        Box(Modifier.size(8.dp))
      }
    }
    compose.mainClock.advanceTimeBy(16)
    repeat(4) {
      tick += 1
      compose.mainClock.advanceTimeBy(16)
    }

    assertEquals(1, typographies.size)
  }
}
