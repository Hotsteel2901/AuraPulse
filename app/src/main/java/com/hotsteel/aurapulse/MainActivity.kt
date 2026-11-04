package com.hotsteel.aurapulse

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.hotsteel.aurapulse.data.SettingsStore
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import java.util.Locale

class MainActivity : ComponentActivity() {
  private val viewModel: AuraViewModel by viewModels()
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

  override fun attachBaseContext(newBase: Context) {
    val tag = newBase.getSharedPreferences("aurapulse_settings", Context.MODE_PRIVATE)
      .getString("language", "system") ?: "system"
    if (tag == "system" || tag.isBlank()) {
      super.attachBaseContext(newBase)
      return
    }
    val locale = Locale.forLanguageTag(tag)
    val configuration = Configuration(newBase.resources.configuration).apply {
      setLocale(locale)
      setLocales(LocaleList(locale))
      setLayoutDirection(locale)
    }
    super.attachBaseContext(newBase.createConfigurationContext(configuration))
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    installSplashScreen()
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    if (Build.VERSION.SDK_INT >= 33) notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    handleIntent(intent)
    setContent {
      AuraRoot(vm = viewModel, onChangeLanguage = ::applyLanguage, onChangeDarkTheme = ::applySystemBarIcons)
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    handleIntent(intent)
  }

  /** Keeps status/navigation bar icons readable for the resolved light or dark surface. */
  private fun applySystemBarIcons(dark: Boolean) {
    WindowCompat.getInsetsController(window, window.decorView).apply {
      isAppearanceLightStatusBars = !dark
      isAppearanceLightNavigationBars = !dark
    }
  }

  private fun applyLanguage(tag: String) {
    SettingsStore(this).setLanguage(tag)
    recreate()
  }

  /** Share targets, VIEW intents and aurapulse:// deep links all land here. */
  private fun handleIntent(intent: Intent?) {
    intent ?: return
    val uri = when (intent.action) {
      Intent.ACTION_SEND -> intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
      Intent.ACTION_VIEW -> intent.data
      else -> null
    } ?: return
    when (uri.scheme) {
      "content" -> viewModel.importUris(listOf(uri))
      "http", "https" -> {
        val name = uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: uri.host ?: "Stream"
        viewModel.addDirectUrl(name, uri.toString())
      }
      "aurapulse" -> uri.getQueryParameter("url")?.let {
        viewModel.addDirectUrl(uri.getQueryParameter("title") ?: "Shared link", it)
      }
    }
  }
}
