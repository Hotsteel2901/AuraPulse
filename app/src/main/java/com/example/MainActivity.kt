package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.data.local.OfflineVaultDatabase
import com.example.data.local.OfflineVaultRepository
import com.example.data.remote.CustomMusicSourceBridge
import com.example.data.repository.AuraFirestoreRepository
import com.example.ui.auth.AuraAccountSession
import com.example.ui.auth.AuthProviderType
import com.example.ui.auth.authStateFlow
import com.example.ui.auth.signOut
import com.example.ui.i18n.AppLanguage
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.i18n.resolveAuraStrings
import com.example.ui.screens.CloudOfflineScreen
import com.example.ui.screens.ConfigAboutScreen
import com.example.ui.screens.EqualizerScreen
import com.example.ui.screens.HiFiDeckScreen
import com.example.ui.screens.PlaylistsScreen
import com.example.ui.theme.AuraPulseTheme
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.AuraDestination
import com.example.ui.viewmodel.AuraPlayerViewModel
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.FirebaseFirestore

class MainActivity : ComponentActivity() {
  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    setContent { AuraPulseApp() }
  }
}

@Composable
fun AuraPulseApp(auth: FirebaseAuth = Firebase.auth) {
  val currentUser by
    auth.authStateFlow().collectAsStateWithLifecycle(initialValue = auth.currentUser)

  // Launches directly into the full Local-First Studio Shell without any mandatory login gate.
  AuraStudioShell(firebaseUser = currentUser)
}

@Composable
private fun AuraStudioShell(
  firebaseUser: com.google.firebase.auth.FirebaseUser?,
) {
  val context = LocalContext.current
  val scope = rememberCoroutineScope()
  val credentialManager = remember(context) { CredentialManager.create(context) }

  val viewModel: AuraPlayerViewModel =
    viewModel(
      key = "aura_studio_main_vm",
      factory =
        viewModelFactory {
          initializer {
            val app =
              checkNotNull(this[APPLICATION_KEY]) {
                "APPLICATION_KEY missing from CreationExtras"
              }
            val databaseId = app.getString(R.string.firestore_database_id)
            val db = FirebaseFirestore.getInstance(databaseId)
            val roomDb = OfflineVaultDatabase.getInstance(app)
            AuraPlayerViewModel(
              firestoreRepository = AuraFirestoreRepository(db),
              offlineVaultRepository = OfflineVaultRepository(roomDb.offlineTrackDao(), app),
              customSourceBridge = CustomMusicSourceBridge(),
              currentUserId = firebaseUser?.uid ?: "local_studio_user",
            )
          }
        },
    )

  // Synchronize Firebase Auth state changes if the user signs in with Google or GitHub OAuth
  LaunchedEffect(firebaseUser?.uid) {
    if (firebaseUser != null) {
      val providerIds = firebaseUser.providerData.map { it.providerId }
      val isGitHub = providerIds.any { it.contains("github", ignoreCase = true) }
      viewModel.updateAccountSession(
        AuraAccountSession(
          uid = firebaseUser.uid,
          displayName =
            firebaseUser.displayName
              ?: firebaseUser.email?.substringBefore("@")
              ?: "Authenticated Studio User",
          handleOrEmail = firebaseUser.email ?: firebaseUser.uid,
          provider = if (isGitHub) AuthProviderType.GITHUB else AuthProviderType.GOOGLE,
          isFirebaseCloudAuthenticated = true,
        )
      )
    }
  }

  val playerState by viewModel.playerState.collectAsStateWithLifecycle()
  val tracks by viewModel.tracksState.collectAsStateWithLifecycle()
  val playlistsState by viewModel.playlistsState.collectAsStateWithLifecycle()
  val customSourcesState by viewModel.customSourcesState.collectAsStateWithLifecycle()
  val soundProfilesState by viewModel.soundProfilesState.collectAsStateWithLifecycle()
  val syncStatesState by viewModel.syncStatesState.collectAsStateWithLifecycle()
  val spectrumBins by viewModel.spectrumBins.collectAsStateWithLifecycle()
  val rmsLevelDb by viewModel.rmsLevelDb.collectAsStateWithLifecycle()

  val activeTrack = playerState.currentTrack ?: tracks.firstOrNull()
  val activeStrings =
    remember(playerState.appLanguageCode) { resolveAuraStrings(playerState.appLanguageCode) }
  val effectiveAccentHex =
    playerState.customThemeAccentHex ?: activeTrack?.accentHex ?: "#00F5D4"

  CompositionLocalProvider(LocalAuraStrings provides activeStrings) {
    AuraPulseTheme(
      accentHex = effectiveAccentHex,
      secondaryHex = activeTrack?.secondaryHex ?: "#7B2CBF",
      useSystemDynamicWallpaper = playerState.useSystemDynamicWallpaper,
    ) {
      BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val isExpandedWidth = maxWidth >= 700.dp

        Scaffold(
          contentWindowInsets = WindowInsets.safeDrawing,
          topBar = {
            StudioQuickStatusHeader(
              languageCode = playerState.appLanguageCode,
              regionCode = playerState.selectedGlobalRegion,
              sampleRateKhz = playerState.targetSampleRateKhz,
              accountProvider = playerState.accountSession.provider,
              onCycleLanguage = {
                val ordered = listOf("SYSTEM", "zh-CN", "zh-TW", "en", "ja", "ko")
                val idx = ordered.indexOfFirst { it.equals(playerState.appLanguageCode, true) }
                val next = ordered[(idx + 1).mod(ordered.size)]
                viewModel.selectAppLanguage(next)
              },
              onOpenAccountOrSync = { viewModel.selectDestination(AuraDestination.CLOUD_SYNC) },
              onOpenConfigAbout = { viewModel.selectDestination(AuraDestination.CONFIG_ABOUT) },
            )
          },
          bottomBar = {
            Column {
              AnimatedVisibility(
                visible =
                  activeTrack != null && playerState.currentDestination != AuraDestination.DECK
              ) {
                if (activeTrack != null) {
                  ExpressiveMiniPlayerBar(
                    trackTitle = activeTrack.title,
                    artist = activeTrack.artist,
                    codecBadge = "${activeTrack.bitDepth}-bit/${activeTrack.sampleRateKhz}kHz",
                    accentHex = effectiveAccentHex,
                    isPlaying = playerState.isPlaying,
                    onTogglePlay = viewModel::togglePlayPause,
                    onSkipNext = viewModel::skipNext,
                    onOpenDeck = { viewModel.selectDestination(AuraDestination.DECK) },
                  )
                }
              }
              if (!isExpandedWidth) {
                AuraBottomNavBar(
                  currentDestination = playerState.currentDestination,
                  onSelectDestination = viewModel::selectDestination,
                )
              }
            }
          },
        ) { innerPadding ->
          Row(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            if (isExpandedWidth) {
              AuraSideNavigationRail(
                currentDestination = playerState.currentDestination,
                onSelectDestination = viewModel::selectDestination,
              )
            }

            Column(modifier = Modifier.weight(1f).fillMaxHeight()) {
              AnimatedVisibility(visible = playerState.statusBannerMessage != null) {
                Surface(
                  color = MaterialTheme.colorScheme.primaryContainer,
                  shape = RoundedCornerShape(12.dp),
                  modifier =
                    Modifier.fillMaxWidth()
                      .padding(horizontal = 16.dp, vertical = 6.dp)
                      .clickable { viewModel.clearBannerMessage() }
                      .testTag("status_feedback_banner"),
                ) {
                  Text(
                    text = playerState.statusBannerMessage ?: "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                  )
                }
              }

              Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                AnimatedContent(
                  targetState = playerState.currentDestination,
                  transitionSpec = { fadeIn() togetherWith fadeOut() },
                  label = "destinationTransition",
                ) { destination ->
                  when (destination) {
                    AuraDestination.DECK ->
                      HiFiDeckScreen(
                        playerState = playerState,
                        tracks = tracks,
                        spectrumBins = spectrumBins,
                        rmsLevelDb = rmsLevelDb,
                        onSelectTrack = viewModel::selectAndPlayTrack,
                        onTogglePlayPause = viewModel::togglePlayPause,
                        onSkipNext = viewModel::skipNext,
                        onSkipPrevious = viewModel::skipPrevious,
                        onSeekTo = viewModel::seekTo,
                        onToggleOfflineDownload = viewModel::toggleTrackOfflineDownload,
                        onSelectServiceFilter = viewModel::selectServiceFilter,
                        onUpdateSearchQuery = viewModel::updateSearchQuery,
                        onToggleDynamicWallpaper = viewModel::toggleSystemDynamicWallpaper,
                      )

                    AuraDestination.PLAYLISTS ->
                      PlaylistsScreen(
                        playlistsState = playlistsState,
                        allTracks = tracks,
                        selectedPlaylistId = playerState.selectedPlaylistId,
                        onSelectPlaylist = viewModel::selectPlaylistDetail,
                        onCreatePlaylist = viewModel::createCustomPlaylist,
                        onToggleTrackInPlaylist = viewModel::toggleTrackInPlaylist,
                        onTogglePlaylistOfflinePin = viewModel::togglePlaylistOfflinePin,
                        onDeletePlaylist = viewModel::deletePlaylist,
                        onPlayTrack = viewModel::selectAndPlayTrack,
                        onBackToDeck = { viewModel.selectDestination(AuraDestination.DECK) },
                      )

                    AuraDestination.EQUALIZER ->
                      EqualizerScreen(
                        playerState = playerState,
                        soundProfilesState = soundProfilesState,
                        onUpdateBand = viewModel::updateEqBand,
                        onUpdatePreamp = viewModel::updatePreampGain,
                        onUpdateBassBoost = viewModel::updateBassBoost,
                        onUpdateSpatializer = viewModel::updateSpatializer,
                        onToggleBitPerfect = viewModel::toggleBitPerfectMode,
                        onSelectPresetCategory = viewModel::applyPresetCategory,
                        onSaveSoundProfile = viewModel::saveCurrentSoundProfile,
                        onLoadSoundProfile = viewModel::loadSoundProfile,
                        onDeleteSoundProfile = viewModel::deleteSoundProfile,
                        onBackToDeck = { viewModel.selectDestination(AuraDestination.DECK) },
                      )

                    AuraDestination.CLOUD_SYNC ->
                      CloudOfflineScreen(
                        playerState = playerState,
                        allTracks = tracks,
                        customSourcesState = customSourcesState,
                        syncStatesState = syncStatesState,
                        onSelectGlobalRegion = viewModel::selectGlobalRegion,
                        onAddCustomSource = viewModel::addCustomMusicSource,
                        onAddKnownTemplate = viewModel::addKnownTemplateSource,
                        onToggleCustomSourceEnabled = viewModel::toggleCustomSourceEnabled,
                        onDeleteCustomSource = viewModel::deleteCustomSource,
                        onTestCustomSource = viewModel::testCustomMusicSource,
                        onQueryCustomSources = viewModel::queryCustomMusicSources,
                        onPublishSyncState = viewModel::publishCurrentDeviceSyncState,
                        onResumeFromSyncedDevice = viewModel::resumeFromSyncedDevice,
                        onToggleOfflineDownload = viewModel::toggleTrackOfflineDownload,
                        onPlayTrack = viewModel::selectAndPlayTrack,
                        onSessionUpdated = viewModel::updateAccountSession,
                        onSignOut = {
                          signOut(
                            context = context,
                            credentialManager = credentialManager,
                            onSignOutComplete = { viewModel.handleSignOutToLocalMode() },
                            scope = scope,
                          )
                        },
                        onShowStatusMessage = viewModel::showStatusMessage,
                        onBackToDeck = { viewModel.selectDestination(AuraDestination.DECK) },
                      )

                    AuraDestination.CONFIG_ABOUT ->
                      ConfigAboutScreen(
                        playerState = playerState,
                        spectrumBins = spectrumBins,
                        onSelectLanguage = viewModel::selectAppLanguage,
                        onSelectTargetSampleRate = viewModel::selectTargetSampleRate,
                        onSelectBufferSizeMs = viewModel::selectBufferSizeMs,
                        onUpdateCrossfadeSeconds = viewModel::updateCrossfadeSeconds,
                        onToggleReplayGain = viewModel::toggleReplayGain,
                        onToggleAutoPinOffline = viewModel::toggleAutoPinOfflineOnPlay,
                        onSelectCustomThemeAccent = viewModel::selectCustomThemeAccent,
                        onToggleDynamicWallpaper = viewModel::toggleSystemDynamicWallpaper,
                        onSelectVisualizerStyle = viewModel::selectVisualizerStyle,
                        onSendDeveloperPulse = viewModel::sendDeveloperPulse,
                        onShowStatusMessage = viewModel::showStatusMessage,
                        onBackToDeck = { viewModel.selectDestination(AuraDestination.DECK) },
                      )
                  }
                }
              }
            }
          }
        }
      }
    }
  }
}

@Composable
private fun StudioQuickStatusHeader(
  languageCode: String,
  regionCode: String,
  sampleRateKhz: Int,
  accountProvider: AuthProviderType,
  onCycleLanguage: () -> Unit,
  onOpenAccountOrSync: () -> Unit,
  onOpenConfigAbout: () -> Unit,
) {
  val langBadge = remember(languageCode) { AppLanguage.fromCode(languageCode).badge }
  Surface(
    color = MaterialTheme.colorScheme.surface,
    tonalElevation = 2.dp,
    modifier = Modifier.fillMaxWidth().testTag("studio_top_status_bar"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
      ) {
        Text(
          text = "AuraPulse",
          style = MaterialTheme.typography.titleMedium,
          fontWeight = FontWeight.ExtraBold,
          color = MaterialTheme.colorScheme.primary,
        )
        Surface(
          color = MaterialTheme.colorScheme.secondaryContainer,
          shape = RoundedCornerShape(50),
          modifier =
            Modifier.clickable(onClick = onOpenConfigAbout)
              .testTag("top_bar_developer_badge"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Filled.Verified,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.onSecondaryContainer,
              modifier = Modifier.size(13.dp),
            )
            Text(
              text = "github@Hotsteel2901 • AGPL-3.0+",
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSecondaryContainer,
            )
          }
        }
      }

      Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        Surface(
          color = MaterialTheme.colorScheme.surfaceVariant,
          shape = RoundedCornerShape(50),
          modifier =
            Modifier.clickable(onClick = onOpenAccountOrSync)
              .testTag("top_bar_account_mode_chip"),
        ) {
          Text(
            text =
              when (accountProvider) {
                AuthProviderType.GOOGLE -> "Google • $regionCode"
                AuthProviderType.GITHUB -> "GitHub • $regionCode"
                AuthProviderType.LOCAL_GUEST -> "${sampleRateKhz}kHz • $regionCode"
              },
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
          )
        }

        Surface(
          color = MaterialTheme.colorScheme.primaryContainer,
          shape = RoundedCornerShape(50),
          modifier =
            Modifier.clickable(onClick = onCycleLanguage)
              .testTag("quick_language_cycle_chip"),
        ) {
          Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
          ) {
            Icon(
              Icons.Filled.Language,
              contentDescription = "Switch Language",
              tint = MaterialTheme.colorScheme.onPrimaryContainer,
              modifier = Modifier.size(14.dp),
            )
            Text(
              text = langBadge,
              style = MaterialTheme.typography.labelSmall,
              fontWeight = FontWeight.Bold,
              color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
          }
        }
      }
    }
  }
}

@Composable
private fun ExpressiveMiniPlayerBar(
  trackTitle: String,
  artist: String,
  codecBadge: String,
  accentHex: String,
  isPlaying: Boolean,
  onTogglePlay: () -> Unit,
  onSkipNext: () -> Unit,
  onOpenDeck: () -> Unit,
) {
  val accentColor = parseHexColor(accentHex, MaterialTheme.colorScheme.primary)
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    tonalElevation = 6.dp,
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onOpenDeck)
        .testTag("mini_player_bar"),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = trackTitle,
          style = MaterialTheme.typography.titleMedium,
          color = accentColor,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Text(
          text = "$artist • $codecBadge",
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
      }
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
          onClick = onTogglePlay,
          modifier = Modifier.size(48.dp).testTag("mini_player_play_pause"),
        ) {
          Icon(
            imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = if (isPlaying) "Pause" else "Play",
            tint = accentColor,
          )
        }
        IconButton(
          onClick = onSkipNext,
          modifier = Modifier.size(48.dp).testTag("mini_player_skip_next"),
        ) {
          Icon(
            imageVector = Icons.Filled.SkipNext,
            contentDescription = "Next Track",
          )
        }
      }
    }
  }
}

@Composable
private fun AuraBottomNavBar(
  currentDestination: AuraDestination,
  onSelectDestination: (AuraDestination) -> Unit,
) {
  val strings = LocalAuraStrings.current
  NavigationBar(modifier = Modifier.testTag("aura_bottom_nav")) {
    NavigationBarItem(
      selected = currentDestination == AuraDestination.DECK,
      onClick = { onSelectDestination(AuraDestination.DECK) },
      icon = { Icon(Icons.Filled.Album, contentDescription = null) },
      label = { Text(strings.navDeck, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      modifier = Modifier.testTag("nav_tab_deck"),
    )
    NavigationBarItem(
      selected = currentDestination == AuraDestination.PLAYLISTS,
      onClick = { onSelectDestination(AuraDestination.PLAYLISTS) },
      icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
      label = { Text(strings.navPlaylists, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      modifier = Modifier.testTag("nav_tab_playlists"),
    )
    NavigationBarItem(
      selected = currentDestination == AuraDestination.EQUALIZER,
      onClick = { onSelectDestination(AuraDestination.EQUALIZER) },
      icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
      label = { Text(strings.navEqualizer, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      modifier = Modifier.testTag("nav_tab_equalizer"),
    )
    NavigationBarItem(
      selected = currentDestination == AuraDestination.CLOUD_SYNC,
      onClick = { onSelectDestination(AuraDestination.CLOUD_SYNC) },
      icon = { Icon(Icons.Filled.Public, contentDescription = null) },
      label = { Text(strings.navCustomApis, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      modifier = Modifier.testTag("nav_tab_cloud_sync"),
    )
    NavigationBarItem(
      selected = currentDestination == AuraDestination.CONFIG_ABOUT,
      onClick = { onSelectDestination(AuraDestination.CONFIG_ABOUT) },
      icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
      label = { Text(strings.navConfigAbout, maxLines = 1, overflow = TextOverflow.Ellipsis) },
      modifier = Modifier.testTag("nav_tab_config_about"),
    )
  }
}

@Composable
private fun AuraSideNavigationRail(
  currentDestination: AuraDestination,
  onSelectDestination: (AuraDestination) -> Unit,
) {
  val strings = LocalAuraStrings.current
  NavigationRail(modifier = Modifier.testTag("aura_side_nav_rail")) {
    NavigationRailItem(
      selected = currentDestination == AuraDestination.DECK,
      onClick = { onSelectDestination(AuraDestination.DECK) },
      icon = { Icon(Icons.Filled.Album, contentDescription = null) },
      label = { Text(strings.navDeck) },
    )
    NavigationRailItem(
      selected = currentDestination == AuraDestination.PLAYLISTS,
      onClick = { onSelectDestination(AuraDestination.PLAYLISTS) },
      icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
      label = { Text(strings.navPlaylists) },
    )
    NavigationRailItem(
      selected = currentDestination == AuraDestination.EQUALIZER,
      onClick = { onSelectDestination(AuraDestination.EQUALIZER) },
      icon = { Icon(Icons.Filled.GraphicEq, contentDescription = null) },
      label = { Text(strings.navEqualizer) },
    )
    NavigationRailItem(
      selected = currentDestination == AuraDestination.CLOUD_SYNC,
      onClick = { onSelectDestination(AuraDestination.CLOUD_SYNC) },
      icon = { Icon(Icons.Filled.Public, contentDescription = null) },
      label = { Text(strings.navCustomApis) },
    )
    NavigationRailItem(
      selected = currentDestination == AuraDestination.CONFIG_ABOUT,
      onClick = { onSelectDestination(AuraDestination.CONFIG_ABOUT) },
      icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
      label = { Text(strings.navConfigAbout) },
    )
  }
}
