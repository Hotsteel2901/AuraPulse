package com.example.ui.viewmodel

import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audio.HiFiAudioEngine
import com.example.data.local.OfflineVaultRepository
import com.example.data.model.CustomMusicSource
import com.example.data.model.OfflineTrackEntity
import com.example.data.model.Playlist
import com.example.data.model.SoundProfile
import com.example.data.model.SyncState
import com.example.data.remote.CustomMusicSourceBridge
import com.example.data.remote.EndpointTestResult
import com.example.data.remote.KnownApiTemplate
import com.example.data.repository.AuraFirestoreRepository
import com.example.ui.auth.AuraAccountSession
import com.google.firebase.Firebase
import com.google.firebase.Timestamp
import com.google.firebase.auth.auth
import java.util.UUID
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

sealed interface UiState<out T> {
  data object Loading : UiState<Nothing>
  data class Success<T>(val data: T) : UiState<T>
  data class Error(val message: String) : UiState<Nothing>
}

enum class AuraDestination {
  DECK,
  PLAYLISTS,
  EQUALIZER,
  CLOUD_SYNC,
  CONFIG_ABOUT,
}

data class PlayerUiState(
  val currentDestination: AuraDestination = AuraDestination.DECK,
  val currentTrack: OfflineTrackEntity? = null,
  val isPlaying: Boolean = false,
  val positionMs: Long = 0L,
  val activeServiceFilter: String = "ALL",
  val searchQuery: String = "",
  val selectedPlaylistId: String? = null,
  val bandsTenthsDb: List<Long> = listOf(20L, 15L, 5L, 0L, -5L, 5L, 15L, 25L, 20L, 15L),
  val preampTenthsDb: Long = 0L,
  val bassBoostPercent: Long = 25L,
  val spatializerStrength: Long = 35L,
  val dacBitPerfectMode: Boolean = false,
  val activePresetCategory: String = "HARMAN_TARGET",
  val useSystemDynamicWallpaper: Boolean = false,
  val selectedGlobalRegion: String = "GLOBAL",
  val activeCustomSourceId: String? = null,
  val lastEndpointDiagnostics: Map<String, EndpointTestResult> = emptyMap(),
  val statusBannerMessage: String? = null,
  val isCloudSearching: Boolean = false,
  // Config & Localization & Developer Showcase State
  val appLanguageCode: String = "SYSTEM",
  val targetSampleRateKhz: Int = 192,
  val audioBufferSizeMs: Int = 128,
  val crossfadeSeconds: Int = 2,
  val replayGainEnabled: Boolean = true,
  val autoPinOfflineOnPlay: Boolean = false,
  val customThemeAccentHex: String? = null,
  val visualizerStyle: String = "VINYL_FFT",
  val developerPulseCount: Int = 128,
  val accountSession: AuraAccountSession = AuraAccountSession(),
)

class AuraPlayerViewModel(
  private val firestoreRepository: AuraFirestoreRepository,
  private val offlineVaultRepository: OfflineVaultRepository,
  private val customSourceBridge: CustomMusicSourceBridge,
  private val currentUserId: String,
) : ViewModel() {

  private val audioEngine = HiFiAudioEngine(viewModelScope)

  val spectrumBins: StateFlow<List<Float>> = audioEngine.spectrumBins
  val rmsLevelDb: StateFlow<Float> = audioEngine.rmsLevelDb

  private val _playerState = MutableStateFlow(PlayerUiState())
  val playerState: StateFlow<PlayerUiState> = _playerState.asStateFlow()

  private val _localPlaylists = MutableStateFlow(defaultLocalPlaylists(currentUserId))
  private val _localCustomSources = MutableStateFlow(defaultLocalCustomSources(currentUserId))
  private val _localSoundProfiles = MutableStateFlow(defaultLocalSoundProfiles(currentUserId))
  private val _localSyncStates = MutableStateFlow(defaultLocalSyncStates(currentUserId))

  private var cloudSyncJobs: List<Job> = emptyList()

  val tracksState: StateFlow<List<OfflineTrackEntity>> =
    offlineVaultRepository.allTracks
      .catch { e ->
        Log.e(TAG, "Error observing local catalog", e)
        emit(OfflineVaultRepository.defaultStudioMasterCatalog())
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = OfflineVaultRepository.defaultStudioMasterCatalog(),
      )

  val playlistsState: StateFlow<UiState<List<Playlist>>> =
    _localPlaylists
      .map<List<Playlist>, UiState<List<Playlist>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt })
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Success(_localPlaylists.value),
      )

  val customSourcesState: StateFlow<UiState<List<CustomMusicSource>>> =
    _localCustomSources
      .map<List<CustomMusicSource>, UiState<List<CustomMusicSource>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt })
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Success(_localCustomSources.value),
      )

  val soundProfilesState: StateFlow<UiState<List<SoundProfile>>> =
    _localSoundProfiles
      .map<List<SoundProfile>, UiState<List<SoundProfile>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt })
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Success(_localSoundProfiles.value),
      )

  val syncStatesState: StateFlow<UiState<List<SyncState>>> =
    _localSyncStates
      .map<List<SyncState>, UiState<List<SyncState>>> { list ->
        UiState.Success(list.sortedByDescending { it.updatedAt ?: it.createdAt })
      }
      .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000L),
        initialValue = UiState.Success(_localSyncStates.value),
      )

  init {
    viewModelScope.launch {
      offlineVaultRepository.seedInitialCatalogIfEmpty()
      val initialTrack = OfflineVaultRepository.defaultStudioMasterCatalog().first()
      _playerState.update { it.copy(currentTrack = initialTrack) }
      audioEngine.configureDsp(
        bandsDb = _playerState.value.bandsTenthsDb,
        preampGainTenths = _playerState.value.preampTenthsDb,
        bassBoost = _playerState.value.bassBoostPercent,
        spatializer = _playerState.value.spatializerStrength,
        bitPerfect = _playerState.value.dacBitPerfectMode,
      )
      if (Firebase.auth.currentUser != null) {
        attachCloudObserversIfAuthenticated(Firebase.auth.currentUser!!.uid)
      }
    }
  }

  private fun isCloudSessionReady(): Boolean = Firebase.auth.currentUser != null

  private fun attachCloudObserversIfAuthenticated(uid: String) {
    cloudSyncJobs.forEach { it.cancel() }
    val j1 =
      viewModelScope.launch {
        seedDefaultCloudEntitiesIfNeeded()
        firestoreRepository
          .observePlaylists(uid)
          .catch { e -> Log.w(TAG, "Cloud playlist listener fallback to local", e) }
          .collect { remoteList ->
            if (remoteList.isNotEmpty()) {
              _localPlaylists.value = remoteList
            }
          }
      }
    val j2 =
      viewModelScope.launch {
        firestoreRepository
          .observeCustomSources(uid)
          .catch { e -> Log.w(TAG, "Cloud custom sources listener fallback to local", e) }
          .collect { remoteList ->
            if (remoteList.isNotEmpty()) {
              _localCustomSources.value = remoteList
            }
          }
      }
    val j3 =
      viewModelScope.launch {
        firestoreRepository
          .observeSoundProfiles(uid)
          .catch { e -> Log.w(TAG, "Cloud sound profiles listener fallback to local", e) }
          .collect { remoteList ->
            if (remoteList.isNotEmpty()) {
              _localSoundProfiles.value = remoteList
            }
          }
      }
    val j4 =
      viewModelScope.launch {
        firestoreRepository
          .observeSyncStates(uid)
          .catch { e -> Log.w(TAG, "Cloud sync states listener fallback to local", e) }
          .collect { remoteList ->
            if (remoteList.isNotEmpty()) {
              _localSyncStates.value = remoteList
            }
          }
      }
    cloudSyncJobs = listOf(j1, j2, j3, j4)
  }

  fun updateAccountSession(session: AuraAccountSession) {
    _playerState.update { it.copy(accountSession = session) }
    val fbUser = Firebase.auth.currentUser
    if (fbUser != null && session.isFirebaseCloudAuthenticated) {
      attachCloudObserversIfAuthenticated(fbUser.uid)
    } else {
      cloudSyncJobs.forEach { it.cancel() }
      cloudSyncJobs = emptyList()
    }
  }

  fun handleSignOutToLocalMode() {
    cloudSyncJobs.forEach { it.cancel() }
    cloudSyncJobs = emptyList()
    _playerState.update {
      it.copy(
        accountSession = AuraAccountSession(),
        statusBannerMessage =
          "Switched to Local Studio Mode (All features remain active offline/locally)",
      )
    }
  }

  private suspend fun seedDefaultCloudEntitiesIfNeeded() {
    if (!isCloudSessionReady()) return
    val existingPlaylists = firestoreRepository.getUserPlaylists().getOrNull()
    if (existingPlaylists != null && existingPlaylists.isEmpty()) {
      firestoreRepository.createPlaylist(
        title = "Late Night 24-Bit Reference",
        description = "Audiophile studio masters & spatial soundstage showcase",
        accentHex = "#00F5D4",
        sourceService = "AURA_HIFI",
        isOfflinePinned = true,
        trackIds = listOf("trk_neon_01", "trk_velvet_03", "trk_amber_05"),
        customId = "pl_default_reference",
      )
      firestoreRepository.createPlaylist(
        title = "World Custom API Lossless Mix",
        description = "Global multi-region custom API streams (JP, KR, DE, BR, IN)",
        accentHex = "#FF9F1C",
        sourceService = "CUSTOM_API",
        isOfflinePinned = false,
        trackIds = listOf("trk_solar_02", "trk_cyber_04", "trk_emerald_06"),
        customId = "pl_default_global_custom",
      )
      firestoreRepository.createSoundProfile(
        name = "Harman 2026 Reference Curve",
        presetCategory = "HARMAN_TARGET",
        bandsDb = listOf(25L, 20L, 10L, 0L, -5L, 5L, 15L, 25L, 20L, 10L),
        preampGainTenths = -15L,
        bassBoostPercent = 25L,
        spatializerStrength = 35L,
        dacBitPerfectMode = false,
        isActive = true,
        customId = "eq_default_harman",
      )
    }

    val existingSources = firestoreRepository.getUserCustomSources().getOrNull()
    if (existingSources != null && existingSources.isEmpty()) {
      firestoreRepository.createCustomSource(
        name = "Global Radio-Browser Lossless API",
        baseUrl = "https://de1.api.radio-browser.info",
        searchPath = "/json/stations/search",
        protocolType = "RADIO_BROWSER_GLOBAL",
        regionCode = "GLOBAL",
        customHeaderName = "",
        customHeaderValue = "",
        preferredCodec = "FLAC / Lossless Stream",
        isEnabled = true,
        customId = "src_default_radiobrowser",
      )
      firestoreRepository.createCustomSource(
        name = "Multi-Country Music Catalog JSON API",
        baseUrl = "https://itunes.apple.com",
        searchPath = "search?media=music&entity=song&limit=15&country={region}&term={query}",
        protocolType = "OPEN_REST_JSON",
        regionCode = "GLOBAL",
        customHeaderName = "",
        customHeaderValue = "",
        preferredCodec = "24-Bit / 96kHz Master",
        isEnabled = true,
        customId = "src_default_global_json",
      )
      firestoreRepository.createCustomSource(
        name = "Internet Archive Lossless Concert Vault",
        baseUrl = "https://archive.org",
        searchPath = "/advancedsearch.php",
        protocolType = "ARCHIVE_LOSSLESS",
        regionCode = "GLOBAL",
        customHeaderName = "",
        customHeaderValue = "",
        preferredCodec = "FLAC 24-Bit Archive",
        isEnabled = true,
        customId = "src_default_archive",
      )
      publishCurrentDeviceSyncState()
    }
  }

  fun selectDestination(destination: AuraDestination) {
    _playerState.update { it.copy(currentDestination = destination) }
  }

  fun selectPlaylistDetail(playlistId: String?) {
    _playerState.update { it.copy(selectedPlaylistId = playlistId) }
  }

  fun updateSearchQuery(query: String) {
    _playerState.update { it.copy(searchQuery = query) }
  }

  fun selectServiceFilter(service: String) {
    _playerState.update { it.copy(activeServiceFilter = service) }
  }

  fun toggleSystemDynamicWallpaper() {
    _playerState.update { it.copy(useSystemDynamicWallpaper = !it.useSystemDynamicWallpaper) }
  }

  fun clearBannerMessage() {
    _playerState.update { it.copy(statusBannerMessage = null) }
  }

  fun showStatusMessage(message: String) {
    _playerState.update { it.copy(statusBannerMessage = message) }
  }

  // --- CONFIG & LOCALIZATION & DEVELOPER SHOWCASE ACTIONS ---

  fun selectAppLanguage(languageCode: String) {
    _playerState.update {
      it.copy(
        appLanguageCode = languageCode,
        statusBannerMessage =
          when {
            languageCode.equals("zh-CN", ignoreCase = true) -> "已切换界面语言为：简体中文"
            languageCode.startsWith("zh", ignoreCase = true) -> "已切換介面語言為：繁體中文"
            languageCode.equals("ja", ignoreCase = true) -> "表示言語を日本語に切り替えました"
            languageCode.equals("ko", ignoreCase = true) -> "인터페이스 언어가 한국어로 변경되었습니다"
            else -> "Language updated to $languageCode"
          },
      )
    }
  }

  fun selectTargetSampleRate(rateKhz: Int) {
    _playerState.update {
      it.copy(
        targetSampleRateKhz = rateKhz,
        statusBannerMessage = "Target PCM output sample rate set to $rateKhz kHz",
      )
    }
  }

  fun selectBufferSizeMs(bufferMs: Int) {
    _playerState.update {
      it.copy(
        audioBufferSizeMs = bufferMs,
        statusBannerMessage = "Hardware AudioTrack buffer configured to ${bufferMs}ms",
      )
    }
  }

  fun updateCrossfadeSeconds(seconds: Int) {
    _playerState.update { it.copy(crossfadeSeconds = seconds.coerceIn(0, 12)) }
  }

  fun toggleReplayGain() {
    _playerState.update {
      val next = !it.replayGainEnabled
      it.copy(
        replayGainEnabled = next,
        statusBannerMessage =
          if (next) "ReplayGain -14 LUFS loudness normalization enabled"
          else "ReplayGain disabled (0 dB pure master level)",
      )
    }
  }

  fun toggleAutoPinOfflineOnPlay() {
    _playerState.update {
      val next = !it.autoPinOfflineOnPlay
      it.copy(
        autoPinOfflineOnPlay = next,
        statusBannerMessage =
          if (next) "Auto-caching played tracks to Offline FLAC Vault enabled"
          else "Auto-caching played tracks disabled",
      )
    }
  }

  fun selectCustomThemeAccent(hex: String?) {
    _playerState.update {
      it.copy(
        customThemeAccentHex = hex,
        statusBannerMessage =
          if (hex == null) "Theme accent set to Auto Track Artwork Palette"
          else "Custom M3 Expressive theme accent set to $hex",
      )
    }
  }

  fun selectVisualizerStyle(style: String) {
    _playerState.update {
      it.copy(
        visualizerStyle = style,
        statusBannerMessage = "Visualizer engine switched to $style",
      )
    }
  }

  fun sendDeveloperPulse() {
    _playerState.update {
      val nextCount = it.developerPulseCount + 1
      it.copy(
        developerPulseCount = nextCount,
        statusBannerMessage =
          "Sonic Star Pulse #$nextCount sent to Developer github@Hotsteel2901!",
      )
    }
  }

  fun selectGlobalRegion(regionCode: String) {
    val normalized = regionCode.trim().uppercase().ifEmpty { "GLOBAL" }
    _playerState.update {
      it.copy(
        selectedGlobalRegion = normalized,
        statusBannerMessage = "Switched active API routing region to $normalized",
      )
    }
    viewModelScope.launch { publishCurrentDeviceSyncState() }
  }

  fun selectActiveCustomSource(sourceId: String?) {
    _playerState.update { it.copy(activeCustomSourceId = sourceId) }
  }

  fun selectAndPlayTrack(track: OfflineTrackEntity) {
    _playerState.update {
      it.copy(
        currentTrack = track,
        positionMs = 0L,
        isPlaying = true,
      )
    }
    startEngineForCurrentTrack(0L)
    viewModelScope.launch {
      if (_playerState.value.autoPinOfflineOnPlay && !track.isDownloadedOffline) {
        offlineVaultRepository.toggleOfflineDownload(track)
      }
      publishCurrentDeviceSyncState()
    }
  }

  fun togglePlayPause() {
    val state = _playerState.value
    val track = state.currentTrack ?: tracksState.value.firstOrNull() ?: return
    if (state.isPlaying) {
      audioEngine.stopPlayback()
      _playerState.update { it.copy(isPlaying = false) }
    } else {
      _playerState.update { it.copy(currentTrack = track, isPlaying = true) }
      startEngineForCurrentTrack(state.positionMs)
    }
    viewModelScope.launch { publishCurrentDeviceSyncState() }
  }

  fun skipNext() {
    val catalog = tracksState.value
    if (catalog.isEmpty()) return
    val currentId = _playerState.value.currentTrack?.trackId
    val idx = catalog.indexOfFirst { it.trackId == currentId }
    val nextTrack = catalog[(idx + 1).mod(catalog.size)]
    selectAndPlayTrack(nextTrack)
  }

  fun skipPrevious() {
    val catalog = tracksState.value
    if (catalog.isEmpty()) return
    val currentId = _playerState.value.currentTrack?.trackId
    val idx = catalog.indexOfFirst { it.trackId == currentId }
    val prevTrack = catalog[(if (idx <= 0) catalog.size - 1 else idx - 1)]
    selectAndPlayTrack(prevTrack)
  }

  fun seekTo(positionMs: Long) {
    val track = _playerState.value.currentTrack ?: return
    val clamped = positionMs.coerceIn(0L, track.durationMs)
    _playerState.update { it.copy(positionMs = clamped) }
    audioEngine.seekTo(clamped)
    if (_playerState.value.isPlaying) {
      startEngineForCurrentTrack(clamped)
    }
  }

  private fun startEngineForCurrentTrack(startPosMs: Long) {
    val track = _playerState.value.currentTrack ?: return
    audioEngine.startPlayback(
      track = track,
      initialPositionMs = startPosMs,
      onProgressMs = { pos -> _playerState.update { it.copy(positionMs = pos) } },
      onTrackEnded = { skipNext() },
    )
  }

  // --- OFFLINE STORAGE VAULT ---

  fun toggleTrackOfflineDownload(track: OfflineTrackEntity) {
    viewModelScope.launch {
      val downloaded = offlineVaultRepository.toggleOfflineDownload(track)
      val refreshedCurrent =
        if (_playerState.value.currentTrack?.trackId == track.trackId) {
          track.copy(isDownloadedOffline = downloaded)
        } else {
          _playerState.value.currentTrack
        }
      _playerState.update {
        it.copy(
          currentTrack = refreshedCurrent,
          statusBannerMessage =
            if (downloaded) {
              "Pinned '${track.title}' (${track.codec}) to Offline Vault"
            } else {
              "Removed '${track.title}' from Offline Vault"
            },
        )
      }
    }
  }

  // --- CUSTOM PLAYLISTS CRUD (HYBRID LOCAL-FIRST + OPTIONAL CLOUD) ---

  fun createCustomPlaylist(
    title: String,
    description: String,
    accentHex: String,
    sourceService: String,
    isOfflinePinned: Boolean,
    selectedTrackIds: List<String>,
  ) {
    val now = Timestamp.now()
    val newId = "pl_${UUID.randomUUID().toString().take(12)}"
    val newPlaylist =
      Playlist(
        id = newId,
        userId = _playerState.value.accountSession.uid,
        title = title.trim().take(120),
        description = description.trim().take(400),
        accentHex = accentHex,
        sourceService = sourceService,
        isOfflinePinned = isOfflinePinned,
        trackIds = selectedTrackIds.take(100),
        createdAt = now,
        updatedAt = now,
      )
    _localPlaylists.update { listOf(newPlaylist) + it }

    viewModelScope.launch {
      if (isOfflinePinned) {
        val catalog = tracksState.value
        selectedTrackIds.forEach { id ->
          catalog.find { it.trackId == id && !it.isDownloadedOffline }?.let { track ->
            offlineVaultRepository.toggleOfflineDownload(track)
          }
        }
      }
      if (isCloudSessionReady()) {
        firestoreRepository.createPlaylist(
          title = title,
          description = description,
          accentHex = accentHex,
          sourceService = sourceService,
          isOfflinePinned = isOfflinePinned,
          trackIds = selectedTrackIds,
          customId = newId,
        )
      }
      _playerState.update {
        it.copy(statusBannerMessage = "Created playlist '$title'")
      }
    }
  }

  fun toggleTrackInPlaylist(playlist: Playlist, trackId: String) {
    val updatedTracks =
      if (playlist.trackIds.contains(trackId)) {
        playlist.trackIds - trackId
      } else {
        (playlist.trackIds + trackId).take(100)
      }
    val updated = playlist.copy(trackIds = updatedTracks, updatedAt = Timestamp.now())
    _localPlaylists.update { list -> list.map { if (it.id == playlist.id) updated else it } }

    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.updatePlaylist(updated)
      }
    }
  }

  fun togglePlaylistOfflinePin(playlist: Playlist) {
    val newPinned = !playlist.isOfflinePinned
    val updated = playlist.copy(isOfflinePinned = newPinned, updatedAt = Timestamp.now())
    _localPlaylists.update { list -> list.map { if (it.id == playlist.id) updated else it } }

    viewModelScope.launch {
      if (newPinned) {
        val catalog = tracksState.value
        playlist.trackIds.forEach { id ->
          catalog.find { it.trackId == id && !it.isDownloadedOffline }?.let { trk ->
            offlineVaultRepository.toggleOfflineDownload(trk)
          }
        }
      }
      if (isCloudSessionReady()) {
        firestoreRepository.updatePlaylist(updated)
      }
      _playerState.update {
        it.copy(
          statusBannerMessage =
            if (newPinned) "Playlist '${playlist.title}' pinned for Offline Lossless playback"
            else "Offline pin disabled for '${playlist.title}'"
        )
      }
    }
  }

  fun deletePlaylist(playlistId: String) {
    _localPlaylists.update { list -> list.filterNot { it.id == playlistId } }
    _playerState.update { state ->
      state.copy(
        selectedPlaylistId =
          if (state.selectedPlaylistId == playlistId) null else state.selectedPlaylistId,
        statusBannerMessage = "Playlist deleted",
      )
    }
    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.deletePlaylist(playlistId)
      }
    }
  }

  // --- 10-BAND PARAMETRIC EQUALIZER & DSP ---

  fun updateEqBand(bandIndex: Int, tenthsDb: Long) {
    val currentBands = _playerState.value.bandsTenthsDb.toMutableList()
    if (bandIndex in currentBands.indices) {
      currentBands[bandIndex] = tenthsDb.coerceIn(-120L, 120L)
      _playerState.update {
        it.copy(bandsTenthsDb = currentBands, activePresetCategory = "CUSTOM")
      }
      syncDspToEngine()
    }
  }

  fun updatePreampGain(tenthsDb: Long) {
    _playerState.update { it.copy(preampTenthsDb = tenthsDb.coerceIn(-120L, 120L)) }
    syncDspToEngine()
  }

  fun updateBassBoost(percent: Long) {
    _playerState.update { it.copy(bassBoostPercent = percent.coerceIn(0L, 100L)) }
    syncDspToEngine()
  }

  fun updateSpatializer(percent: Long) {
    _playerState.update { it.copy(spatializerStrength = percent.coerceIn(0L, 100L)) }
    syncDspToEngine()
  }

  fun toggleBitPerfectMode() {
    _playerState.update { it.copy(dacBitPerfectMode = !it.dacBitPerfectMode) }
    syncDspToEngine()
  }

  fun applyPresetCategory(category: String) {
    val presetBands =
      when (category) {
        "FLAT_STUDIO" -> listOf(0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L, 0L)
        "WARM_TUBE" -> listOf(30L, 25L, 20L, 10L, -5L, -10L, 5L, 15L, 10L, 5L)
        "BASS_SURGE" -> listOf(65L, 55L, 35L, 10L, -10L, -5L, 10L, 20L, 25L, 15L)
        "VOCAL_CLARITY" -> listOf(-15L, -10L, 0L, 20L, 35L, 45L, 35L, 20L, 10L, 5L)
        "HARMAN_TARGET" -> listOf(25L, 20L, 10L, 0L, -5L, 5L, 15L, 25L, 20L, 10L)
        else -> _playerState.value.bandsTenthsDb
      }
    _playerState.update {
      it.copy(activePresetCategory = category, bandsTenthsDb = presetBands)
    }
    syncDspToEngine()
  }

  fun saveCurrentSoundProfile(profileName: String) {
    val state = _playerState.value
    val now = Timestamp.now()
    val newId = "eq_${UUID.randomUUID().toString().take(12)}"
    val newProfile =
      SoundProfile(
        id = newId,
        userId = state.accountSession.uid,
        name = profileName.trim().take(80),
        presetCategory = state.activePresetCategory,
        bandsDb = state.bandsTenthsDb.take(10),
        preampGainTenths = state.preampTenthsDb,
        bassBoostPercent = state.bassBoostPercent,
        spatializerStrength = state.spatializerStrength,
        dacBitPerfectMode = state.dacBitPerfectMode,
        isActive = true,
        createdAt = now,
        updatedAt = now,
      )
    _localSoundProfiles.update { listOf(newProfile) + it }
    _playerState.update {
      it.copy(statusBannerMessage = "Saved EQ profile '$profileName'")
    }

    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.createSoundProfile(
          name = profileName,
          presetCategory = state.activePresetCategory,
          bandsDb = state.bandsTenthsDb,
          preampGainTenths = state.preampTenthsDb,
          bassBoostPercent = state.bassBoostPercent,
          spatializerStrength = state.spatializerStrength,
          dacBitPerfectMode = state.dacBitPerfectMode,
          isActive = true,
          customId = newId,
        )
      }
    }
  }

  fun loadSoundProfile(profile: SoundProfile) {
    _playerState.update {
      it.copy(
        bandsTenthsDb = (profile.bandsDb + List(10) { 0L }).take(10),
        preampTenthsDb = profile.preampGainTenths,
        bassBoostPercent = profile.bassBoostPercent,
        spatializerStrength = profile.spatializerStrength,
        dacBitPerfectMode = profile.dacBitPerfectMode,
        activePresetCategory = profile.presetCategory,
        statusBannerMessage = "Activated sound profile '${profile.name}'",
      )
    }
    syncDspToEngine()
  }

  fun deleteSoundProfile(profileId: String) {
    _localSoundProfiles.update { list -> list.filterNot { it.id == profileId } }
    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.deleteSoundProfile(profileId)
      }
    }
  }

  private fun syncDspToEngine() {
    val state = _playerState.value
    audioEngine.configureDsp(
      bandsDb = state.bandsTenthsDb,
      preampGainTenths = state.preampTenthsDb,
      bassBoost = state.bassBoostPercent,
      spatializer = state.spatializerStrength,
      bitPerfect = state.dacBitPerfectMode,
    )
  }

  // --- USER-DEFINED CUSTOM MUSIC SOURCE APIs (ALL COUNTRIES/AREAS) ---

  fun addCustomMusicSource(
    name: String,
    baseUrl: String,
    searchPath: String,
    protocolType: String,
    regionCode: String,
    customHeaderName: String,
    customHeaderValue: String,
    preferredCodec: String,
  ) {
    val now = Timestamp.now()
    val newId = "src_${UUID.randomUUID().toString().take(12)}"
    val newSource =
      CustomMusicSource(
        id = newId,
        userId = _playerState.value.accountSession.uid,
        name = name.trim().take(120),
        baseUrl = baseUrl.trim().take(500),
        searchPath = searchPath.trim().take(300),
        protocolType = protocolType,
        regionCode = regionCode.trim().uppercase().take(16).ifEmpty { "GLOBAL" },
        customHeaderName = customHeaderName.trim().take(80),
        customHeaderValue = customHeaderValue.trim().take(200),
        preferredCodec = preferredCodec.trim().take(40).ifEmpty { "FLAC 24-Bit" },
        isEnabled = true,
        createdAt = now,
        updatedAt = now,
      )
    _localCustomSources.update { listOf(newSource) + it }
    _playerState.update {
      it.copy(
        statusBannerMessage = "Added custom music source '$name' [${regionCode.uppercase()}]"
      )
    }

    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.createCustomSource(
          name = name,
          baseUrl = baseUrl,
          searchPath = searchPath,
          protocolType = protocolType,
          regionCode = regionCode,
          customHeaderName = customHeaderName,
          customHeaderValue = customHeaderValue,
          preferredCodec = preferredCodec,
          isEnabled = true,
          customId = newId,
        )
      }
    }
  }

  fun addKnownTemplateSource(template: KnownApiTemplate, regionCode: String) {
    addCustomMusicSource(
      name = template.name,
      baseUrl = template.baseUrl,
      searchPath = template.searchPath,
      protocolType = template.protocolType,
      regionCode = regionCode.ifBlank { template.defaultRegion },
      customHeaderName = "",
      customHeaderValue = "",
      preferredCodec = template.preferredCodec,
    )
  }

  fun toggleCustomSourceEnabled(source: CustomMusicSource) {
    val updated = source.copy(isEnabled = !source.isEnabled, updatedAt = Timestamp.now())
    _localCustomSources.update { list -> list.map { if (it.id == source.id) updated else it } }
    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.updateCustomSource(updated)
      }
    }
  }

  fun deleteCustomSource(sourceId: String) {
    _localCustomSources.update { list -> list.filterNot { it.id == sourceId } }
    _playerState.update {
      it.copy(statusBannerMessage = "Removed custom music source API")
    }
    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.deleteCustomSource(sourceId)
      }
    }
  }

  fun testCustomMusicSource(source: CustomMusicSource) {
    viewModelScope.launch {
      _playerState.update {
        it.copy(statusBannerMessage = "Testing '${source.name}' [${source.regionCode}]...")
      }
      val diag = customSourceBridge.testCustomEndpoint(source)
      _playerState.update {
        it.copy(
          lastEndpointDiagnostics = it.lastEndpointDiagnostics + (source.id to diag),
          statusBannerMessage = diag.summary,
        )
      }
    }
  }

  fun queryCustomMusicSources(query: String, targetSourceId: String? = null) {
    if (query.isBlank()) return
    val allSources = (customSourcesState.value as? UiState.Success)?.data ?: emptyList()
    val sourcesToQuery =
      when {
        targetSourceId != null -> allSources.filter { it.id == targetSourceId }
        _playerState.value.activeCustomSourceId != null ->
          allSources.filter { it.id == _playerState.value.activeCustomSourceId }
        else -> allSources.filter { it.isEnabled }
      }

    if (sourcesToQuery.isEmpty()) {
      _playerState.update {
        it.copy(
          statusBannerMessage = "Enable or add at least one Custom Music Source API to query."
        )
      }
      return
    }

    _playerState.update { it.copy(isCloudSearching = true) }
    val activeRegion = _playerState.value.selectedGlobalRegion
    viewModelScope.launch {
      var totalImported = 0
      var lastError: String? = null
      for (src in sourcesToQuery) {
        val effectiveRegion =
          if (activeRegion != "GLOBAL") activeRegion else src.regionCode
        val res = customSourceBridge.queryCustomSource(src, query, effectiveRegion)
        res
          .onSuccess { tracks ->
            tracks.forEach { offlineVaultRepository.upsertExternalTrack(it) }
            totalImported += tracks.size
          }
          .onFailure { err -> lastError = err.localizedMessage }
      }
      _playerState.update {
        it.copy(
          isCloudSearching = false,
          statusBannerMessage =
            if (totalImported > 0) {
              "Imported $totalImported tracks across ${sourcesToQuery.size} custom API source(s) [$activeRegion]"
            } else {
              lastError ?: "No tracks found for '$query' in region $activeRegion"
            },
        )
      }
    }
  }

  fun publishCurrentDeviceSyncState() {
    val state = _playerState.value
    val track = state.currentTrack ?: return
    val deviceId = "android_${Build.MODEL.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)}"
    val existingDoc = _localSyncStates.value.find { it.id == deviceId }
    val activeSources = _localCustomSources.value
    val primarySourceName =
      activeSources.firstOrNull { it.isEnabled }?.name ?: "Aura Hi-Res Core"

    val now = Timestamp.now()
    val updatedSync =
      SyncState(
        id = deviceId,
        userId = state.accountSession.uid,
        deviceName =
          "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Hi-Res Deck)",
        activeTrackId = track.trackId,
        positionMs = state.positionMs,
        isPlaying = state.isPlaying,
        activeService =
          when (track.sourceProvider) {
            "CUSTOM_API" -> "CUSTOM_API"
            "SUBSONIC" -> "SUBSONIC"
            "OPEN_GLOBAL" -> "OPEN_GLOBAL"
            else -> "AURA_HIFI"
          },
        audioQualityBadge = "${track.bitDepth}-bit/${track.sampleRateKhz}kHz ${track.codec}",
        activeSourceName = primarySourceName,
        regionCode = state.selectedGlobalRegion,
        createdAt = existingDoc?.createdAt ?: now,
        updatedAt = now,
      )

    _localSyncStates.update { current ->
      listOf(updatedSync) + current.filterNot { it.id == deviceId }
    }

    viewModelScope.launch {
      if (isCloudSessionReady()) {
        firestoreRepository.publishSyncState(
          deviceId = deviceId,
          deviceName = updatedSync.deviceName,
          activeTrackId = updatedSync.activeTrackId,
          positionMs = updatedSync.positionMs,
          isPlaying = updatedSync.isPlaying,
          activeService = updatedSync.activeService,
          audioQualityBadge = updatedSync.audioQualityBadge,
          activeSourceName = updatedSync.activeSourceName,
          regionCode = updatedSync.regionCode,
          existingCreatedAt = existingDoc?.createdAt,
        )
      }
    }
  }

  fun resumeFromSyncedDevice(syncState: SyncState) {
    val catalog = tracksState.value
    val targetTrack =
      catalog.find { it.trackId == syncState.activeTrackId } ?: catalog.firstOrNull() ?: return
    _playerState.update {
      it.copy(
        currentTrack = targetTrack,
        positionMs = syncState.positionMs.coerceIn(0L, targetTrack.durationMs),
        isPlaying = true,
        selectedGlobalRegion = syncState.regionCode,
        statusBannerMessage =
          "Handed off '${targetTrack.title}' from ${syncState.deviceName} [${syncState.regionCode}]",
      )
    }
    startEngineForCurrentTrack(syncState.positionMs)
  }

  override fun onCleared() {
    super.onCleared()
    audioEngine.stopPlayback()
  }

  companion object {
    private const val TAG = "AuraPlayerVM"

    private fun defaultLocalPlaylists(userId: String): List<Playlist> {
      val now = Timestamp.now()
      return listOf(
        Playlist(
          id = "pl_default_reference",
          userId = userId,
          title = "Late Night 24-Bit Reference",
          description = "Audiophile studio masters & spatial soundstage showcase",
          accentHex = "#00F5D4",
          sourceService = "AURA_HIFI",
          isOfflinePinned = true,
          trackIds = listOf("trk_neon_01", "trk_velvet_03", "trk_amber_05"),
          createdAt = now,
          updatedAt = now,
        ),
        Playlist(
          id = "pl_default_global_custom",
          userId = userId,
          title = "World Custom API Lossless Mix",
          description = "Global multi-region custom API streams (JP, KR, DE, BR, IN)",
          accentHex = "#FF9F1C",
          sourceService = "CUSTOM_API",
          isOfflinePinned = false,
          trackIds = listOf("trk_solar_02", "trk_cyber_04", "trk_emerald_06"),
          createdAt = now,
          updatedAt = now,
        ),
      )
    }

    private fun defaultLocalCustomSources(userId: String): List<CustomMusicSource> {
      val now = Timestamp.now()
      return listOf(
        CustomMusicSource(
          id = "src_default_radiobrowser",
          userId = userId,
          name = "Global Radio-Browser Lossless API",
          baseUrl = "https://de1.api.radio-browser.info",
          searchPath = "/json/stations/search",
          protocolType = "RADIO_BROWSER_GLOBAL",
          regionCode = "GLOBAL",
          customHeaderName = "",
          customHeaderValue = "",
          preferredCodec = "FLAC / Lossless Stream",
          isEnabled = true,
          createdAt = now,
          updatedAt = now,
        ),
        CustomMusicSource(
          id = "src_default_global_json",
          userId = userId,
          name = "Multi-Country Music Catalog JSON API",
          baseUrl = "https://itunes.apple.com",
          searchPath = "search?media=music&entity=song&limit=15&country={region}&term={query}",
          protocolType = "OPEN_REST_JSON",
          regionCode = "GLOBAL",
          customHeaderName = "",
          customHeaderValue = "",
          preferredCodec = "24-Bit / 96kHz Master",
          isEnabled = true,
          createdAt = now,
          updatedAt = now,
        ),
        CustomMusicSource(
          id = "src_default_archive",
          userId = userId,
          name = "Internet Archive Lossless Concert Vault",
          baseUrl = "https://archive.org",
          searchPath = "/advancedsearch.php",
          protocolType = "ARCHIVE_LOSSLESS",
          regionCode = "GLOBAL",
          customHeaderName = "",
          customHeaderValue = "",
          preferredCodec = "FLAC 24-Bit Archive",
          isEnabled = true,
          createdAt = now,
          updatedAt = now,
        ),
      )
    }

    private fun defaultLocalSoundProfiles(userId: String): List<SoundProfile> {
      val now = Timestamp.now()
      return listOf(
        SoundProfile(
          id = "eq_default_harman",
          userId = userId,
          name = "Harman 2026 Reference Curve",
          presetCategory = "HARMAN_TARGET",
          bandsDb = listOf(25L, 20L, 10L, 0L, -5L, 5L, 15L, 25L, 20L, 10L),
          preampGainTenths = -15L,
          bassBoostPercent = 25L,
          spatializerStrength = 35L,
          dacBitPerfectMode = false,
          isActive = true,
          createdAt = now,
          updatedAt = now,
        )
      )
    }

    private fun defaultLocalSyncStates(userId: String): List<SyncState> {
      val now = Timestamp.now()
      val deviceId = "android_${Build.MODEL.replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(32)}"
      return listOf(
        SyncState(
          id = deviceId,
          userId = userId,
          deviceName =
            "${Build.MANUFACTURER.replaceFirstChar { it.uppercase() }} ${Build.MODEL} (Local Studio)",
          activeTrackId = "trk_neon_01",
          positionMs = 0L,
          isPlaying = false,
          activeService = "AURA_HIFI",
          audioQualityBadge = "24-bit/192kHz FLAC",
          activeSourceName = "Global Radio-Browser Lossless API",
          regionCode = "GLOBAL",
          createdAt = now,
          updatedAt = now,
        )
      )
    }
  }
}
