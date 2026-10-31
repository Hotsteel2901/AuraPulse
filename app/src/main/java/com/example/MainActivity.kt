package com.example

import android.content.Context
import android.content.res.Configuration
import android.os.Bundle
import android.os.LocaleList
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.player.DiscoveryScreen
import com.example.ui.player.LibraryScreen
import com.example.ui.player.MiniPlayer
import com.example.ui.player.MusicViewModel
import com.example.ui.player.NowPlayingSheet
import com.example.ui.player.PlaylistsScreen
import com.example.ui.player.SettingsScreen
import com.example.ui.theme.AuraPulseTheme
import java.util.Locale

class MainActivity : ComponentActivity() {
  override fun attachBaseContext(newBase: Context) {
    val language = newBase.getSharedPreferences("language", Context.MODE_PRIVATE).getString("locale", "system")
    if (language.isNullOrBlank() || language == "system") {
      super.attachBaseContext(newBase)
    } else {
      val locale = Locale.forLanguageTag(language)
      val configuration = Configuration(newBase.resources.configuration).apply {
        setLocale(locale)
        setLocales(LocaleList(locale))
        setLayoutDirection(locale)
      }
      super.attachBaseContext(newBase.createConfigurationContext(configuration))
    }
  }

  fun changeLanguage(code: String) {
    getSharedPreferences("language", Context.MODE_PRIVATE).edit().putString("locale", code).apply()
    recreate()
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { AuraPulseTheme { AuraApp(this) } }
  }
}

private enum class Destination(val label: Int, val icon: ImageVector) {
  LIBRARY(R.string.nav_library, Icons.Default.LibraryMusic),
  DISCOVER(R.string.nav_discover, Icons.Default.Explore),
  PLAYLISTS(R.string.nav_playlists, Icons.AutoMirrored.Filled.QueueMusic),
  SETTINGS(R.string.nav_settings, Icons.Default.Settings),
}

@Composable
private fun AuraApp(activity: MainActivity, vm: MusicViewModel = viewModel()) {
  val playback by vm.playback.collectAsStateWithLifecycle()
  val importing by vm.importing.collectAsStateWithLifecycle()
  var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  val snackbar = remember { SnackbarHostState() }
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importFiles(uris) }
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::importFolder) }
  LaunchedEffect(vm, activity) {
    vm.notices.collect { notice -> snackbar.showSnackbar(activity.getString(notice.resource, *notice.args.toTypedArray())) }
  }
  BackHandler(enabled = destination != Destination.LIBRARY && !playerOpen) { destination = Destination.LIBRARY }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints {
      val wide = maxWidth >= 760.dp
      Row(Modifier.fillMaxSize()) {
        if (wide) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            Destination.entries.forEach { tab ->
              NavigationRailItem(
                selected = destination == tab, onClick = { destination = tab },
                icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(stringResource(tab.label)) },
              )
            }
          }
        }
        Scaffold(
          modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { SnackbarHost(snackbar) },
          bottomBar = {
            Column {
              playback.track?.let {
                MiniPlayer(state = playback, onOpen = { playerOpen = true }, onToggle = vm::togglePlay, onNext = vm::next)
              }
              if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                  Destination.entries.forEach { tab ->
                    NavigationBarItem(
                      selected = destination == tab, onClick = { destination = tab },
                      icon = { Icon(tab.icon, contentDescription = null) },
                      label = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                  }
                }
              }
            }
          },
        ) { padding ->
          Box(Modifier.fillMaxSize().padding(padding)) {
            when (destination) {
              Destination.LIBRARY -> LibraryScreen(
                vm = vm, importing = importing,
                onPickFiles = { filePicker.launch(arrayOf("audio/*", "application/ogg")) },
                onPickFolder = { folderPicker.launch(null) },
                onDiscover = { destination = Destination.DISCOVER },
              )
              Destination.DISCOVER -> DiscoveryScreen(vm)
              Destination.PLAYLISTS -> PlaylistsScreen(vm)
              Destination.SETTINGS -> SettingsScreen(vm, activity)
            }
          }
        }
      }
      if (playerOpen && playback.track != null) {
        NowPlayingSheet(state = playback, vm = vm, onDismiss = { playerOpen = false })
      }
    }
  }
}
