package com.hotsteel.aurapulse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Composes each tab with a frozen clock, so the continuous aura/equaliser
 * animations cannot spin the Robolectric looper forever.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class ScreenComposeTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun library() = composeScreen(Route.Library)

  @Test
  fun discover() = composeScreen(Route.Discover)

  @Test
  fun insights() = composeScreen(Route.Insights)

  @Test
  fun settings() = composeScreen(Route.Settings)

  @Test
  fun search() = composeScreen(Route.Search)

  @Test
  fun allTracks() = composeScreen(Route.AllTracks)

  @Test
  fun favorites() = composeScreen(Route.Favorites)

  @Test
  fun playlists() = composeScreen(Route.Playlists)

  @Test
  fun downloads() = composeScreen(Route.Downloads)

  @Test
  fun equalizer() = composeScreen(Route.Equalizer)

  @Test
  fun account() = composeScreen(Route.Account)

  @Test
  fun about() = composeScreen(Route.About)

  private fun composeScreen(route: Route) {
    compose.mainClock.autoAdvance = false
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    app.container.settings.update { it.copy(onboardingDone = true) }
    val viewModel = AuraViewModel(app, connectToService = false)
    compose.setContent {
      AuraRoot(
        vm = viewModel,
        onChangeLanguage = {},
        onChangeDarkTheme = {},
        initialRoute = route,
      )
    }
    compose.mainClock.advanceTimeBy(64)
  }
}
