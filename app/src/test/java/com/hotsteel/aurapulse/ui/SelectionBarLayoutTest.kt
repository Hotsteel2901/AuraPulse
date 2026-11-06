package com.hotsteel.aurapulse.ui

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.AuraApp
import com.hotsteel.aurapulse.R
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The library lives inside AnimatedContent, which measures its children with
 * fill-max minimum constraints. The multi-select toolbar used to be a bare
 * Surface sibling there, so it was stretched to the full content height: a huge
 * opaque panel covered the list and its vertically centred row drifted into the
 * middle of the screen, where it also swallowed every tap.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class SelectionBarLayoutTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun selectionBarIsACompactBarAtTheTop() {
    compose.mainClock.autoAdvance = false
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    app.container.settings.update { it.copy(onboardingDone = true) }
    val viewModel = AuraViewModel(app, connectToService = false)
    // Selection only needs the id set; no library entry is required for the bar.
    viewModel.startSelection("track-1")

    compose.setContent {
      AuraRoot(
        vm = viewModel,
        onChangeLanguage = {},
        onChangeDarkTheme = {},
        initialRoute = Route.Library,
      )
    }
    compose.mainClock.advanceTimeBy(640)

    val label = compose.activity.getString(R.string.selected_count, 1)
    val bar = compose.onNodeWithText(label)
    bar.assertExists()
    // A stretched bar would put its label roughly halfway down the screen.
    val bounds = bar.getUnclippedBoundsInRoot()
    val height = bounds.bottom - bounds.top
    assertTrue("selection bar must stay at the top, its label was at ${bounds.top}", bounds.top < 160.dp)
    assertTrue("bar must not fill the screen, its height was $height", height < 96.dp)
  }
}
