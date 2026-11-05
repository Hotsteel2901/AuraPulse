package com.hotsteel.aurapulse.playback

import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.AuraApp
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Creates the real MediaSessionService. The UI tests disable the service bind,
 * so without this the whole playback start-up path is untested — and a throw in
 * onCreate kills the app process before the first frame, which looks exactly
 * like "opens and immediately closes".
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PlaybackServiceTest {

  @Test
  fun serviceCreatesAndDestroys() {
    ApplicationProvider.getApplicationContext<AuraApp>()
    val controller = Robolectric.buildService(PlaybackService::class.java)
    val service = controller.create().get()
    assertNotNull(service)
    controller.destroy()
  }
}
