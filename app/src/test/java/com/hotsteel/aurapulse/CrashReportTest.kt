package com.hotsteel.aurapulse

import android.os.Looper
import android.view.View
import android.widget.ScrollView
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.core.CrashLog
import java.io.File
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

/**
 * The crash screen has to render with platform views only: it is the screen the
 * user sees when the Compose theme itself is what crashed.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CrashReportTest {

  @Test
  fun previousCrashIsShownInsteadOfTheComposeUi() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    val dir = File(app.filesDir, "crashes").apply { mkdirs() }
    File(dir, "last.txt").writeText("java.lang.IllegalStateException: boom\n\tat Example.run(Example.kt:1)")

    val controller = Robolectric.buildActivity(MainActivity::class.java).setup()
    shadowOf(Looper.getMainLooper()).idle()

    val content = controller.get().findViewById<View>(android.R.id.content)
    assertNotNull(content)
    val first = (content as android.view.ViewGroup).getChildAt(0)
    assertTrue("expected a plain crash screen, got ${first?.javaClass}", first is ScrollView)

    CrashLog.clear(app)
    controller.pause().stop().destroy()
  }
}
