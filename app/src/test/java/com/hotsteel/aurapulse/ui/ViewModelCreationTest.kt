package com.hotsteel.aurapulse.ui

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.test.core.app.ApplicationProvider
import com.hotsteel.aurapulse.AuraApp
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Regression cover for the launch crash in 2.0.1.
 *
 * The activity resolved its ViewModel with the default factory, which looks up a
 * single-argument `(Application)` constructor reflectively. Kotlin default
 * arguments do not emit that overload, so every cold start died with
 * `NoSuchMethodException` before the first frame.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "w411dp-h891dp-xhdpi")
class ViewModelCreationTest {

  @Test
  fun exposesAnApplicationOnlyConstructor() {
    // The reflective contract the default AndroidViewModelFactory relies on.
    val constructor = AuraViewModel::class.java.getConstructor(Application::class.java)
    assertNotNull(constructor)
  }

  @Test
  fun defaultFactoryCanInstantiateTheViewModel() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    val owner = ViewModelStore()
    val provider = ViewModelProvider(owner, ViewModelProvider.AndroidViewModelFactory.getInstance(app))

    val viewModel = provider[AuraViewModel::class.java]

    assertNotNull(viewModel)
    assertTrue(viewModel.settings.value.language.isNotEmpty())
    // The looper is deliberately not driven: Robolectric's service stub hands
    // Media3 a null ComponentName, which fails inside the library rather than in
    // this constructor. Creation is the only thing that has to happen here.
  }
}
