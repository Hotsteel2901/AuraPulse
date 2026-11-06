package com.hotsteel.aurapulse.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Explore
import androidx.compose.material.icons.filled.Insights
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.ui.components.AuraBackdrop
import com.hotsteel.aurapulse.ui.components.MiniPlayer
import com.hotsteel.aurapulse.ui.screens.AboutScreen
import com.hotsteel.aurapulse.ui.screens.AccountScreen
import com.hotsteel.aurapulse.ui.screens.AlbumScreen
import com.hotsteel.aurapulse.ui.screens.AllTracksScreen
import com.hotsteel.aurapulse.ui.screens.ArtistScreen
import com.hotsteel.aurapulse.ui.screens.DiscoverScreen
import com.hotsteel.aurapulse.ui.screens.DownloadsScreen
import com.hotsteel.aurapulse.ui.screens.EqualizerScreen
import com.hotsteel.aurapulse.ui.screens.FavoritesScreen
import com.hotsteel.aurapulse.ui.screens.InsightsScreen
import com.hotsteel.aurapulse.ui.screens.LibraryScreen
import com.hotsteel.aurapulse.ui.screens.NowPlayingScreen
import com.hotsteel.aurapulse.ui.screens.OnboardingScreen
import com.hotsteel.aurapulse.ui.screens.PlaylistDetailScreen
import com.hotsteel.aurapulse.ui.screens.PlaylistsScreen
import com.hotsteel.aurapulse.ui.screens.SearchScreen
import com.hotsteel.aurapulse.ui.screens.SettingsScreen
import com.hotsteel.aurapulse.ui.theme.AuraTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

private data class Tab(val route: Route, val label: Int, val icon: ImageVector)

private val tabs = listOf(
  Tab(Route.Library, R.string.nav_library, Icons.Default.LibraryMusic),
  Tab(Route.Discover, R.string.nav_discover, Icons.Default.Explore),
  Tab(Route.Insights, R.string.nav_insights, Icons.Default.Insights),
  Tab(Route.Settings, R.string.nav_settings, Icons.Default.Settings),
)

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun AuraRoot(
  vm: AuraViewModel,
  onChangeLanguage: (String) -> Unit,
  onChangeDarkTheme: (Boolean) -> Unit = {},
  initialRoute: Route = Route.Library,
  safeMode: Boolean = false,
) {
  val settings by vm.settings.collectAsStateWithLifecycle()
  // Only the two facts the shell needs. Collecting the whole PlaybackUi made the
  // entire theme, scaffold and navigation bar recompose every 400 ms while audio
  // played, which starved the main thread.
  val hasTrack by remember(vm) {
    vm.playback.map { it.track != null }.distinctUntilChanged()
  }.collectAsStateWithLifecycle(initialValue = false)
  val isPlaying by remember(vm) {
    vm.playback.map { it.playing }.distinctUntilChanged()
  }.collectAsStateWithLifecycle(initialValue = false)
  val resources = LocalContext.current.resources
  val snackbar = remember { SnackbarHostState() }
  val stack = remember { mutableStateListOf(initialRoute) }
  var playerOpen by rememberSaveable { mutableStateOf(false) }
  var onboarded by rememberSaveable { mutableStateOf(settings.onboardingDone || safeMode) }

  AuraTheme(settings = settings, systemDark = isSystemInDarkTheme(), safeMode = safeMode) {
    val darkSurface = MaterialTheme.colorScheme.surface.luminance() < 0.5f
    LaunchedEffect(darkSurface) { onChangeDarkTheme(darkSurface) }
    LaunchedEffect(Unit) {
      vm.notices.collect { notice ->
        snackbar.showSnackbar(resources.getString(notice.resource, *notice.args.toTypedArray()))
      }
    }
    LaunchedEffect(Unit) {
      vm.openPlayerRequests.collect { playerOpen = true }
    }
    BackHandler(enabled = playerOpen) { playerOpen = false }
    BackHandler(enabled = !playerOpen && stack.size > 1) { stack.removeAt(stack.lastIndex) }

    val current = stack.last()
    val motion = MaterialTheme.motionScheme

    Scaffold(
      containerColor = Color.Transparent,
      contentWindowInsets = WindowInsets.navigationBars,
      snackbarHost = { SnackbarHost(snackbar, Modifier.padding(bottom = 8.dp)) },
      bottomBar = {
        Column {
          if (hasTrack) {
            MiniPlayer(vm, onOpen = { playerOpen = true })
          }
          if (current.isRoot) {
            NavigationBar(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.92f)) {
              tabs.forEach { tab ->
                val selected = current == tab.route
                NavigationBarItem(
                  selected = selected,
                  onClick = {
                    if (stack.size == 1) stack[0] = tab.route
                    else { stack.clear(); stack.add(tab.route) }
                  },
                  icon = {
                    Icon(
                      tab.icon,
                      contentDescription = null,
                      modifier = Modifier.padding(vertical = 1.dp),
                    )
                  },
                  label = { Text(stringResource(tab.label), maxLines = 1) },
                  colors = NavigationBarItemDefaults.colors(
                    indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    selectedTextColor = MaterialTheme.colorScheme.onSurface,
                  ),
                )
              }
            }
          }
        }
      },
    ) { padding ->
      Box(Modifier.fillMaxSize()) {
        AuraBackdrop(Modifier.fillMaxSize(), playing = isPlaying)
        AnimatedContent(
          targetState = current,
          modifier = Modifier.fillMaxSize().padding(padding),
          transitionSpec = {
            if (targetState.isRoot && initialState.isRoot) {
              fadeIn(motion.fastEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec())
            } else {
              (slideInHorizontally(motion.defaultSpatialSpec()) { it / 10 } + fadeIn(motion.fastEffectsSpec())) togetherWith
                (slideOutHorizontally(motion.fastSpatialSpec()) { -it / 16 } + fadeOut(motion.fastEffectsSpec()))
            }
          },
          label = "route",
        ) { route ->
          when (route) {
            Route.Library -> LibraryScreen(
              vm,
              onOpenSearch = { stack.add(Route.Search) },
              onOpenAllTracks = { stack.add(Route.AllTracks) },
              onOpenFavorites = { stack.add(Route.Favorites) },
              onOpenPlaylists = { stack.add(Route.Playlists) },
              onOpenPlaylist = { stack.add(Route.Playlist(it)) },
              onOpenAlbum = { stack.add(Route.Album(it)) },
              onOpenArtist = { stack.add(Route.Artist(it)) },
              onOpenDownloads = { stack.add(Route.Downloads) },
            )
            Route.Discover -> DiscoverScreen(vm, onOpenDownloads = { stack.add(Route.Downloads) })
            Route.Insights -> InsightsScreen(vm)
            Route.Settings -> SettingsScreen(
              vm,
              onOpenAccount = { stack.add(Route.Account) },
              onOpenEqualizer = { stack.add(Route.Equalizer) },
              onOpenAbout = { stack.add(Route.About) },
              onChangeLanguage = onChangeLanguage,
            )
            Route.Search -> SearchScreen(
              vm,
              onBack = { stack.removeAt(stack.lastIndex) },
              onOpenAlbum = { stack.add(Route.Album(it)) },
              onOpenArtist = { stack.add(Route.Artist(it)) },
            )
            Route.AllTracks -> AllTracksScreen(vm, onBack = { stack.removeAt(stack.lastIndex) })
            Route.Favorites -> FavoritesScreen(vm, onBack = { stack.removeAt(stack.lastIndex) })
            Route.Playlists -> PlaylistsScreen(
              vm,
              onBack = { stack.removeAt(stack.lastIndex) },
              onOpen = { stack.add(Route.Playlist(it)) },
            )
            is Route.Playlist -> PlaylistDetailScreen(
              vm, route.id,
              onBack = { stack.removeAt(stack.lastIndex) },
              onOpenAlbum = { stack.add(Route.Album(it)) },
              onOpenArtist = { stack.add(Route.Artist(it)) },
            )
            is Route.Album -> AlbumScreen(
              vm, route.key,
              onBack = { stack.removeAt(stack.lastIndex) },
              onOpenArtist = { stack.add(Route.Artist(it)) },
            )
            is Route.Artist -> ArtistScreen(
              vm, route.name,
              onBack = { stack.removeAt(stack.lastIndex) },
              onOpenAlbum = { stack.add(Route.Album(it)) },
            )
            Route.Equalizer -> EqualizerScreen(vm) { stack.removeAt(stack.lastIndex) }
            Route.Account -> AccountScreen(vm) { stack.removeAt(stack.lastIndex) }
            Route.Downloads -> DownloadsScreen(vm) { stack.removeAt(stack.lastIndex) }
            Route.About -> AboutScreen { stack.removeAt(stack.lastIndex) }
          }
        }
      }
    }

    AnimatedVisibility(
      visible = playerOpen,
      enter = slideInVertically(motion.defaultSpatialSpec()) { it } + fadeIn(motion.fastEffectsSpec()),
      exit = slideOutVertically(motion.fastSpatialSpec()) { it } + fadeOut(motion.fastEffectsSpec()),
    ) {
      NowPlayingScreen(
        vm,
        onDismiss = { playerOpen = false },
        onOpenEqualizer = {
          playerOpen = false
          if (stack.size == 1) stack.add(Route.Equalizer) else stack.add(Route.Equalizer)
        },
        onOpenArtist = { name ->
          playerOpen = false
          stack.add(Route.Artist(name))
        },
      )
    }

    AnimatedVisibility(
      visible = !onboarded,
      enter = fadeIn(motion.defaultEffectsSpec()),
      exit = fadeOut(motion.defaultEffectsSpec()),
    ) {
      OnboardingScreen(
        onDone = {
          vm.updateSettings { it.copy(onboardingDone = true) }
          onboarded = true
        },
      )
    }
  }
}
