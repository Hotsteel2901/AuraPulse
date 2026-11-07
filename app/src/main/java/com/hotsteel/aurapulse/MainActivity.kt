package com.hotsteel.aurapulse

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Typeface
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.LocaleList
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import com.hotsteel.aurapulse.core.CrashLog
import com.hotsteel.aurapulse.core.MainThreadWatchdog
import com.hotsteel.aurapulse.data.SettingsStore
import com.hotsteel.aurapulse.ui.AuraRoot
import com.hotsteel.aurapulse.ui.AuraViewModel
import java.util.Locale

/** What an external intent asks AuraPulse to do. */
sealed interface DeepLinkTarget {
  data class Import(val uri: Uri) : DeepLinkTarget
  data class Stream(val name: String, val url: String) : DeepLinkTarget
  data object Ignore : DeepLinkTarget
}

/**
 * Turns a VIEW/SEND payload into one action without touching the UI.
 *
 * Kept separate so the hostile-input cases can be unit tested: an opaque
 * `aurapulse:foo` URI is not hierarchical and `Uri.getQueryParameter` throws
 * `UnsupportedOperationException` on it, which used to crash onCreate.
 */
fun resolveDeepLink(action: String?, data: Uri?, stream: Uri?): DeepLinkTarget {
  val uri = when (action) {
    Intent.ACTION_SEND -> stream
    Intent.ACTION_VIEW -> data
    else -> null
  } ?: return DeepLinkTarget.Ignore
  return when (uri.scheme) {
    "content" -> DeepLinkTarget.Import(uri)
    "http", "https" -> {
      val name = uri.lastPathSegment?.substringBeforeLast('.')?.ifBlank { null } ?: uri.host ?: "Stream"
      DeepLinkTarget.Stream(name, uri.toString())
    }
    "aurapulse" -> {
      if (!uri.isHierarchical) {
        DeepLinkTarget.Ignore
      } else {
        uri.getQueryParameter("url")?.let {
          DeepLinkTarget.Stream(uri.getQueryParameter("title") ?: "Shared link", it)
        } ?: DeepLinkTarget.Ignore
      }
    }
    else -> DeepLinkTarget.Ignore
  }
}

class MainActivity : ComponentActivity() {
  // Explicit factory: AndroidViewModelFactory looks for a single-argument
  // (Application) constructor by reflection, which Kotlin default parameters do
  // not emit. Naming the constructor here keeps the lookup deterministic.
  private val viewModel: AuraViewModel by viewModels {
    viewModelFactory { initializer { AuraViewModel(application) } }
  }
  private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }
  private var showingCrashReport = false

  override fun attachBaseContext(newBase: Context) {
    val tag = newBase.getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)
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

    // A crash that happened before the first frame is kept on disk. Showing it
    // with plain platform views means the report stays readable even when the
    // Compose theme is what broke.
    val crash = CrashLog.last(this) ?: MainThreadWatchdog.last(this)
    if (crash != null) {
      showingCrashReport = true
      showCrashReport(crash)
      return
    }

    if (Build.VERSION.SDK_INT >= 33) {
      notificationPermission.launch(android.Manifest.permission.POST_NOTIFICATIONS)
    }
    handleIntent(intent)
    val safeMode = prefs().getBoolean(KEY_SAFE_MODE, false)
    setContent {
      AuraRoot(
        vm = viewModel,
        onChangeLanguage = ::applyLanguage,
        onChangeDarkTheme = ::applySystemBarIcons,
        safeMode = safeMode,
      )
    }
  }

  override fun onNewIntent(intent: Intent) {
    super.onNewIntent(intent)
    setIntent(intent)
    if (showingCrashReport) return
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
    // Update the process-wide store, not a throwaway instance. The ViewModel's
    // copy is retained across recreate(), and a stale copy wrote the old
    // language back the next time any other setting changed.
    AuraApp.container.settings.setLanguage(tag)
    recreate()
  }

  /** Share targets, VIEW intents and aurapulse:// deep links all land here. */
  private fun handleIntent(intent: Intent?) {
    intent ?: return
    when (val target = resolveDeepLink(
      intent.action,
      intent.data,
      intent.getParcelableExtra(Intent.EXTRA_STREAM) as? Uri,
    )) {
      is DeepLinkTarget.Import -> viewModel.importUris(listOf(target.uri))
      is DeepLinkTarget.Stream -> viewModel.addDirectUrl(target.name, target.url)
      DeepLinkTarget.Ignore -> Unit
    }
  }

  private fun prefs() = getSharedPreferences(SettingsStore.PREFS, Context.MODE_PRIVATE)

  private fun showCrashReport(report: String) {
    val pad = (20 * resources.displayMetrics.density).toInt()
    val scroll = ScrollView(this).apply { setBackgroundColor(0xFF0E1512.toInt()) }
    val column = LinearLayout(this).apply {
      orientation = LinearLayout.VERTICAL
      setPadding(pad, pad * 2, pad, pad * 2)
    }

    column.addView(TextView(this).apply {
      text = getString(R.string.crash_title)
      setTextColor(0xFFFFD08A.toInt())
      textSize = 22f
      setTypeface(typeface, Typeface.BOLD)
    })
    column.addView(TextView(this).apply {
      text = getString(R.string.crash_hint)
      setTextColor(0xFFC7D3CC.toInt())
      textSize = 13f
      setPadding(0, pad / 2, 0, pad)
    })
    column.addView(TextView(this).apply {
      text = report
      setTextColor(0xFFE7F0EA.toInt())
      textSize = 10f
      typeface = Typeface.MONOSPACE
      setTextIsSelectable(true)
      setPadding(0, 0, 0, pad)
    })

    fun action(label: Int, onClick: () -> Unit): Button = Button(this).apply {
      text = getString(label)
      isAllCaps = false
      gravity = Gravity.CENTER
      setOnClickListener { onClick() }
    }

    column.addView(action(R.string.crash_copy) {
      val clipboard = getSystemService(ClipboardManager::class.java)
      clipboard?.setPrimaryClip(ClipData.newPlainText("aurapulse-crash", report))
      Toast.makeText(this, R.string.crash_copied, Toast.LENGTH_SHORT).show()
    })
    column.addView(action(R.string.crash_safe_mode) {
      CrashLog.clear(this)
      MainThreadWatchdog.clear(this)
      prefs().edit().putBoolean(KEY_SAFE_MODE, true).apply()
      recreate()
    })
    column.addView(action(R.string.crash_normal_start) {
      CrashLog.clear(this)
      MainThreadWatchdog.clear(this)
      prefs().edit().putBoolean(KEY_SAFE_MODE, false).apply()
      recreate()
    })

    scroll.addView(column)
    setContentView(scroll)
  }

  private companion object {
    const val KEY_SAFE_MODE = "safe_mode"
  }
}
