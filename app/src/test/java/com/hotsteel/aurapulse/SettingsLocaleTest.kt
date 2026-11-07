package com.hotsteel.aurapulse

import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.data.ThemeMode
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.Route
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * The Settings screen used the experimental ButtonGroup for the theme picker.
 * With the Chinese labels the group overflowed, its measure policy copied valid
 * constraints into an invalid maxWidth, and opening Settings crashed. The
 * weighted toggle row that replaced it must lay out in both locales.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "zh-rCN-w411dp-h891dp-xhdpi")
class SettingsLocaleTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun settingsRendersWithChineseLabelsAndBlackTheme() {
    compose.mainClock.autoAdvance = false
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    app.container.settings.update {
      it.copy(onboardingDone = true, language = "zh-CN", theme = ThemeMode.BLACK)
    }
    val viewModel = AuraViewModel(app, connectToService = false)
    compose.setContent {
      AuraRoot(
        vm = viewModel,
        onChangeLanguage = {},
        onChangeDarkTheme = {},
        initialRoute = Route.Settings,
      )
    }
    compose.mainClock.advanceTimeBy(64)
  }
}
