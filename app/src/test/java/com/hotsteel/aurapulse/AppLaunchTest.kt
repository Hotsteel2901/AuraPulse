package com.hotsteel.aurapulse

import android.app.Application
import androidx.activity.ComponentActivity
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Boots the real application and the onboarding screen a fresh install shows.
 *
 * The clock is frozen because the aura backdrop animates forever; driving the
 * looper with idle() spins until Robolectric's trace buffer exhausts the heap.
 * Per-screen composition is covered in [ScreenComposeTest], and the reflective
 * ViewModel contract in [com.hotsteel.aurapulse.ui.ViewModelCreationTest].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AppLaunchTest {

  @get:Rule
  val compose = createAndroidComposeRule<ComponentActivity>()

  @Test
  fun applicationStarts() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    assertNotNull(app.container)
    assertNotNull(app.getSystemService(Application.NOTIFICATION_SERVICE))
  }

  @Test
  fun onboardingReachesFirstFrame() {
    compose.mainClock.autoAdvance = false
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    app.container.settings.update { it.copy(onboardingDone = false) }
    val viewModel = AuraViewModel(app, connectToService = false)
    compose.setContent {
      AuraRoot(vm = viewModel, onChangeLanguage = {}, onChangeDarkTheme = {})
    }
    compose.mainClock.advanceTimeBy(64)
    assertNotNull(compose.activity.window.decorView)
  }
}
