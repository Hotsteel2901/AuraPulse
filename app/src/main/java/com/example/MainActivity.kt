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
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.player.AboutScreen
import com.example.ui.player.DiscoveryScreen
import com.example.ui.player.LibraryScreen
import com.example.ui.player.MiniPlayer
import com.example.ui.player.MusicViewModel
import com.example.ui.player.NowPlayingScreen
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
  ABOUT(R.string.about_title, Icons.Default.Info),
}

private val navigationDestinations = Destination.entries.filter { it != Destination.ABOUT }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun AuraApp(activity: MainActivity, vm: MusicViewModel = viewModel()) {
  val importing by vm.importing.collectAsStateWithLifecycle()
  var destination by rememberSaveable { mutableStateOf(Destination.LIBRARY) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  var focusEqualizer by rememberSaveable { mutableStateOf(false) }
  val motion = MaterialTheme.motionScheme
  val snackbar = remember { SnackbarHostState() }
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris -> vm.importFiles(uris) }
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri -> uri?.let(vm::importFolder) }
  LaunchedEffect(vm, activity) {
    vm.notices.collect { notice -> snackbar.showSnackbar(activity.getString(notice.resource, *notice.args.toTypedArray())) }
  }
  LaunchedEffect(vm) { vm.openPlayerRequests.collect { playerOpen = true } }
  BackHandler(enabled = destination != Destination.LIBRARY && !playerOpen) {
    destination = if (destination == Destination.ABOUT) Destination.SETTINGS else Destination.LIBRARY
  }

  Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
    BoxWithConstraints {
      val wide = maxWidth >= 760.dp
      Row(Modifier.fillMaxSize()) {
        if (wide) {
          NavigationRail(containerColor = MaterialTheme.colorScheme.surface) {
            navigationDestinations.forEach { tab ->
              NavigationRailItem(
                selected = destination == tab || (destination == Destination.ABOUT && tab == Destination.SETTINGS), onClick = { destination = tab },
                icon = { Icon(tab.icon, contentDescription = null) }, label = { Text(stringResource(tab.label)) },
              )
            }
          }
        }
        Scaffold(
          modifier = Modifier.weight(1f), containerColor = MaterialTheme.colorScheme.background,
          contentWindowInsets = WindowInsets.safeDrawing,
          snackbarHost = { if (!playerOpen) SnackbarHost(snackbar) },
          bottomBar = {
            Column {
              MiniPlayerHost(vm, onOpen = { playerOpen = true })
              if (!wide) {
                NavigationBar(containerColor = MaterialTheme.colorScheme.surface) {
                  navigationDestinations.forEach { tab ->
                    NavigationBarItem(
                      selected = destination == tab || (destination == Destination.ABOUT && tab == Destination.SETTINGS), onClick = { destination = tab },
                      icon = { Icon(tab.icon, contentDescription = null) },
                      label = { Text(stringResource(tab.label), maxLines = 1) },
                    )
                  }
                }
              }
            }
          },
        ) { padding ->
          AnimatedContent(
            targetState = destination, modifier = Modifier.fillMaxSize().padding(padding),
            transitionSpec = {
              (fadeIn(motion.defaultEffectsSpec()) + slideInVertically(motion.fastSpatialSpec()) { it / 24 }) togetherWith
                fadeOut(motion.fastEffectsSpec())
            }, label = "page transition",
          ) { page ->
            when (page) {
              Destination.LIBRARY -> LibraryScreen(
                vm = vm, importing = importing,
                onPickFiles = { filePicker.launch(arrayOf("audio/*", "application/ogg")) },
                onPickFolder = { folderPicker.launch(null) },
                onDiscover = { destination = Destination.DISCOVER },
              )
              Destination.DISCOVER -> DiscoveryScreen(vm)
              Destination.PLAYLISTS -> PlaylistsScreen(vm)
              Destination.SETTINGS -> SettingsScreen(vm, activity, onAbout = { destination = Destination.ABOUT },
                focusEqualizer = focusEqualizer, onFocusHandled = { focusEqualizer = false })
              Destination.ABOUT -> AboutScreen(onBack = { destination = Destination.SETTINGS })
            }
          }
        }
      }
      AnimatedVisibility(
        visible = playerOpen, modifier = Modifier.fillMaxSize(),
        enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()),
        exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
      ) {
        PlayerScreenHost(vm, visible = playerOpen, onDismiss = { playerOpen = false }, onEqualizer = {
          playerOpen = false; destination = Destination.SETTINGS; focusEqualizer = true
        })
      }
      if (playerOpen) SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).safeDrawingPadding().padding(16.dp))
    }
  }
}


@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MiniPlayerHost(vm: MusicViewModel, onOpen: () -> Unit) {
  val playback by vm.playback.collectAsStateWithLifecycle()
  val motion = MaterialTheme.motionScheme
  AnimatedVisibility(visible = playback.track != null,
    enter = fadeIn(motion.fastEffectsSpec()), exit = fadeOut(motion.fastEffectsSpec())) {
    MiniPlayer(state = playback, onOpen = onOpen, onToggle = vm::togglePlay, onNext = vm::next)
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerScreenHost(vm: MusicViewModel, visible: Boolean, onDismiss: () -> Unit, onEqualizer: () -> Unit) {
  val playback by vm.playback.collectAsStateWithLifecycle()
  var hadTrack by remember { mutableStateOf(false) }
  BackHandler(enabled = visible) { onDismiss() }
  LaunchedEffect(playback.track) {
    if (playback.track != null) hadTrack = true else if (hadTrack) onDismiss()
  }
  if (playback.track != null) {
    NowPlayingScreen(state = playback, vm = vm, onDismiss = onDismiss, onEqualizer = onEqualizer)
  } else {
    // The controller can reconnect after activity recreation; let it recover without
    // closing the player before the restored queue has arrived.
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
      Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
    }
  }
}
