package com.hotsteel.aurapulse

import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * Boots the real application and the first screen the launcher shows.
 *
 * The onboarding screen is composed because a fresh install lands there, which
 * is exactly the state the app crashed in when the emphasized typography scaled
 * an unspecified letter spacing. Per-screen composition is covered separately
 * in [ScreenComposeTest], which freezes the animation clock.
 *
 * The ViewModel is created without a playback service: Robolectric cannot bind
 * a real MediaSessionService, and the UI is expected to stay usable (idle) when
 * the service is unavailable anyway.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp-xhdpi")
class AppLaunchTest {

  @Test
  fun applicationStarts() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    assertNotNull(app.container)
  }

  @Test
  fun firstScreenReachesFirstFrame() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    val controller = Robolectric.buildActivity(ComponentActivity::class.java).setup()
    val activity = controller.get()
    val viewModel = AuraViewModel(app, connectToService = false)
    activity.setContent {
      AuraRoot(vm = viewModel, onChangeLanguage = {}, onChangeDarkTheme = {})
    }
    shadowOf(Looper.getMainLooper()).idle()
    assertNotNull(activity.window.decorView)
    controller.pause().stop().destroy()
  }
}
