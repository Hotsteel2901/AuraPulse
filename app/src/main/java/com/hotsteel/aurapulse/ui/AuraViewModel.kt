package com.hotsteel.aurapulse.ui

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.hotsteel.aurapulse.AuraApp
import com.hotsteel.aurapulse.BuildConfig
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.AppSettings
import com.hotsteel.aurapulse.data.LibraryFilter
import com.hotsteel.aurapulse.data.SettingsStore
import com.hotsteel.aurapulse.data.TrackSort
import com.hotsteel.aurapulse.data.discovery.DiscoverySource
import com.hotsteel.aurapulse.data.discovery.RemoteTrack
import com.hotsteel.aurapulse.data.github.AuthState
import com.hotsteel.aurapulse.data.github.DeviceCode
import com.hotsteel.aurapulse.data.github.DeviceTokenResult
import com.hotsteel.aurapulse.data.github.GitHubException
import com.hotsteel.aurapulse.data.github.SyncState
import com.hotsteel.aurapulse.data.library.LyricEntity
import com.hotsteel.aurapulse.data.library.Lyrics
import com.hotsteel.aurapulse.data.library.LrcParser
import com.hotsteel.aurapulse.data.library.PlayEventEntity
import com.hotsteel.aurapulse.data.library.PlayStat
import com.hotsteel.aurapulse.data.library.PlaylistEntity
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.data.library.stableId
import com.hotsteel.aurapulse.playback.EqualizerController
import com.hotsteel.aurapulse.playback.EqPreset
import com.hotsteel.aurapulse.playback.PlaybackEnhancements
import com.hotsteel.aurapulse.playback.PlaybackService
import com.hotsteel.aurapulse.playback.SleepTimer
import java.io.File
import java.util.Calendar
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class Notice(@param:StringRes val resource: Int, val args: List<Any> = emptyList())

data class PlaybackUi(
  val track: TrackEntity? = null,
  val playing: Boolean = false,
  val buffering: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val canSeek: Boolean = false,
  val shuffle: Boolean = false,
  val repeat: Int = Player.REPEAT_MODE_OFF,
  val index: Int = -1,
)

data class DiscoveryUi(
  val source: DiscoverySource = DiscoverySource.PREVIEWS,
  val query: String = "",
  val country: String = "US",
  val customName: String = "",
  val customTemplate: String = "",
  val loading: Boolean = false,
  val searched: Boolean = false,
  val results: List<RemoteTrack> = emptyList(),
  val error: String? = null,
)

data class DownloadProgress(val bytes: Long, val total: Long?)

data class InsightsUi(
  val totalMs: Long = 0,
  val plays: Long = 0,
  val distinctTracks: Int = 0,
  val streak: Int = 0,
  val topTracks: List<Pair<TrackEntity, PlayStat>> = emptyList(),
  val topArtists: List<Pair<String, Long>> = emptyList(),
  val week: List<Long> = List(7) { 0 },
  val hours: List<Int> = List(24) { 0 },
)

data class DeviceFlow(val code: DeviceCode, val waiting: Boolean = true)

data class LyricsBundle(val trackId: String, val lyrics: Lyrics, val offsetMs: Int)

class AuraViewModel(application: Application) : AndroidViewModel(application) {
  private val container = AuraApp.container
  private val library = container.library
  private val listening = container.listening
  private val settingsStore = container.settings
  private val account = container.account
  private val gist = container.gistSync

  val settings: StateFlow<AppSettings> = settingsStore.state
  val tracks: StateFlow<List<TrackEntity>> = library.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val playlists: StateFlow<List<PlaylistEntity>> = library.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val favorites: StateFlow<Set<String>> = listening.favorites
  val history: StateFlow<List<String>> = listening.history
  val stats: StateFlow<Map<String, PlayStat>> = library.stats.stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())
  val eq = EqualizerController.state
  val enhancements = PlaybackEnhancements.state
  val sleep = SleepTimer.state
  val auth: StateFlow<AuthState> = account.state

  private val mutableQueue = MutableStateFlow<List<TrackEntity>>(emptyList())
  val queue: StateFlow<List<TrackEntity>> = mutableQueue.asStateFlow()

  private val mutablePlayback = MutableStateFlow(PlaybackUi())
  val playback: StateFlow<PlaybackUi> = mutablePlayback.asStateFlow()

  private val mutableImporting = MutableStateFlow(false)
  val importing: StateFlow<Boolean> = mutableImporting.asStateFlow()

  private val mutableDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
  val downloads: StateFlow<Map<String, DownloadProgress>> = mutableDownloads.asStateFlow()

  private val mutableSelection = MutableStateFlow<Set<String>>(emptySet())
  val selection: StateFlow<Set<String>> = mutableSelection.asStateFlow()

  private val mutableSearch = MutableStateFlow("")
  val searchQuery: StateFlow<String> = mutableSearch.asStateFlow()

  private val mutableSearching = MutableStateFlow(false)
  val searching: StateFlow<Boolean> = mutableSearching.asStateFlow()

  private val mutableDiscovery = MutableStateFlow(
    DiscoveryUi(
      country = Locale.getDefault().country.uppercase(Locale.US).takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US",
      customName = prefs().getString("custom_name", "").orEmpty(),
      customTemplate = prefs().getString("custom_template", "").orEmpty(),
    ),
  )
  val discovery: StateFlow<DiscoveryUi> = mutableDiscovery.asStateFlow()

  private val mutableSync = MutableStateFlow(gist.state())
  val sync: StateFlow<SyncState> = mutableSync.asStateFlow()

  private val mutableSyncBusy = MutableStateFlow(false)
  val syncBusy: StateFlow<Boolean> = mutableSyncBusy.asStateFlow()

  private val mutableDeviceFlow = MutableStateFlow<DeviceFlow?>(null)
  val deviceFlow: StateFlow<DeviceFlow?> = mutableDeviceFlow.asStateFlow()

  private val mutableInsights = MutableStateFlow(InsightsUi())
  val insights: StateFlow<InsightsUi> = mutableInsights.asStateFlow()

  private val mutableLyrics = MutableStateFlow<LyricsBundle?>(null)
  val lyrics: StateFlow<LyricsBundle?> = mutableLyrics.asStateFlow()

  private val mutableReady = MutableStateFlow(false)
  val ready: StateFlow<Boolean> = mutableReady.asStateFlow()

  private val noticesChannel = Channel<Notice>(Channel.BUFFERED)
  val notices = noticesChannel.receiveAsFlow()
  private val openPlayerChannel = Channel<Unit>(Channel.CONFLATED)
  val openPlayerRequests = openPlayerChannel.receiveAsFlow()

  private val searchIndex = mutableMapOf<String, TrackEntity>()
  private val sessionTracks = mutableMapOf<String, TrackEntity>()
  private val downloadJobs = mutableMapOf<String, Job>()
  private var searchJob: Job? = null
  private var deviceJob: Job? = null
  private var controller: MediaController? = null
  private var restored = false

  private val controllerFuture = MediaController.Builder(
    application, SessionToken(application, android.content.ComponentName(application, PlaybackService::class.java)),
  ).buildAsync()

  private val listener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue(player)
      refresh(player)
    }

    override fun onPlayerError(error: PlaybackException) {
      val id = controller?.currentMediaItem?.mediaId
      val known = id?.let { tracks.value.firstOrNull { track -> track.id == it } }
      if (known != null && known.source == Source.LOCAL) {
        viewModelScope.launch { library.markMissing(known.id, true) }
        notify(R.string.track_missing)
      } else {
        notify(R.string.playback_error, safe(error))
      }
      controller?.let { player ->
        if (player.mediaItemCount > player.currentMediaItemIndex + 1) player.seekToNextMediaItem()
      }
    }
  }

  init {
    controllerFuture.addListener({
      runCatching { controllerFuture.get() }.onSuccess { media ->
        controller = media
        media.addListener(listener)
        refreshQueue(media)
        refresh(media)
        mutableReady.value = true
        maybeRestoreSession()
      }.onFailure { notify(R.string.player_unavailable, safe(it)) }
    }, androidx.core.content.ContextCompat.getMainExecutor(application))

    viewModelScope.launch {
      library.tracks.collect { list ->
        searchIndex.clear()
        list.forEach { searchIndex[it.id] = it }
        controller?.let { refreshQueue(it); refresh(it) }
      }
    }

    viewModelScope.launch {
      while (isActive) {
        val player = controller
        val observed = mutablePlayback.subscriptionCount.value > 0
        if (player != null && player.isPlaying && observed) refresh(player)
        delay(if (player?.isPlaying == true && observed) 400 else 1500)
      }
    }

    viewModelScope.launch {
      mutablePlayback.map { it.track?.id }.distinctUntilChanged().collect { id ->
        if (id == null) { mutableLyrics.value = null; return@collect }
        val stored = library.lyrics(id) ?: run { mutableLyrics.value = null; return@collect }
        val parsed = LrcParser.parse(stored.content)
        mutableLyrics.value = LyricsBundle(id, parsed, stored.offsetMs)
      }
    }

    viewModelScope.launch { refreshInsights() }
  }

  // ------------------------------------------------------------------ player

  private fun trackOf(item: MediaItem): TrackEntity =
    searchIndex[item.mediaId] ?: sessionTracks[item.mediaId] ?: TrackEntity(
      id = item.mediaId,
      uri = item.localConfiguration?.uri?.toString().orEmpty(),
      title = item.mediaMetadata.title?.toString().orEmpty().ifBlank { "Unknown" },
      artist = item.mediaMetadata.artist?.toString().orEmpty(),
      album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
      source = Source.DIRECT,
      artworkUri = item.mediaMetadata.artworkUri?.toString(),
    )

  private fun refreshQueue(player: Player) {
    val list = (0 until player.mediaItemCount).map { trackOf(player.getMediaItemAt(it)) }
    if (mutableQueue.value != list) mutableQueue.value = list
  }

  private fun refresh(player: Player) {
    val found = player.currentMediaItem?.let(::trackOf)
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    mutablePlayback.value = PlaybackUi(
      track = found,
      playing = player.isPlaying,
      buffering = player.playbackState == Player.STATE_BUFFERING,
      positionMs = player.currentPosition.coerceAtLeast(0),
      durationMs = duration,
      canSeek = player.isCurrentMediaItemSeekable && duration > 0,
      shuffle = player.shuffleModeEnabled,
      repeat = player.repeatMode,
      index = player.currentMediaItemIndex.takeIf { player.currentMediaItem != null } ?: -1,
    )
  }

  private fun mediaItem(track: TrackEntity): MediaItem {
    val local = track.downloadedPath?.let(::File)?.takeIf { it.isFile }
    val uri = local?.let(Uri::fromFile) ?: Uri.parse(track.uri)
    return MediaItem.Builder()
      .setMediaId(track.id)
      .setUri(uri)
      .setMediaMetadata(
        MediaMetadata.Builder()
          .setTitle(track.title)
          .setArtist(track.artist.ifBlank { null })
          .setAlbumTitle(track.album.ifBlank { null })
          .setArtworkUri(track.artworkUri?.let(Uri::parse))
          .build(),
      )
      .build()
  }

  fun play(track: TrackEntity, list: List<TrackEntity> = listOf(track), startPositionMs: Long = 0L) {
    val player = controller ?: run { notify(R.string.player_connecting); return }
    val playable = list.distinctBy { it.id }.filter { it.uri.isNotBlank() }
    val index = playable.indexOfFirst { it.id == track.id }
    if (index < 0) { notify(R.string.track_unavailable); return }
    playable.forEach { sessionTracks[it.id] = it }
    runCatching {
      player.setMediaItems(playable.map(::mediaItem), index, startPositionMs.coerceAtLeast(0))
      player.prepare()
      player.play()
      refreshQueue(player)
      refresh(player)
      openPlayerChannel.trySend(Unit)
    }.onFailure { notify(R.string.playback_error, safe(it)) }
  }

  fun playShuffled(list: List<TrackEntity>) {
    if (list.isEmpty()) return
    val player = controller ?: return
    player.shuffleModeEnabled = true
    play(list.random(), list)
  }

  fun toggle() {
    val player = controller ?: return
    when {
      player.playbackState == Player.STATE_ENDED -> { player.seekTo(0); player.play() }
      player.isPlaying || player.playWhenReady -> player.pause()
      else -> player.play()
    }
  }

  fun next() = controller?.seekToNextMediaItem()
  fun previous() {
    controller?.let { if (it.isCurrentMediaItemSeekable && it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() }
  }

  fun seek(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)) }
  fun seekToIndex(index: Int) {
    controller?.let { if (index in 0 until it.mediaItemCount) { it.seekTo(index, 0); it.prepare(); it.play() } }
  }
  fun jumpBy(deltaMs: Long) {
    controller?.let { player ->
      if (!player.isCurrentMediaItemSeekable) return
      val end = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
      player.seekTo((player.currentPosition + deltaMs).coerceIn(0, end))
    }
  }

  fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
  fun cycleRepeat() {
    controller?.let {
      it.repeatMode = when (it.repeatMode) {
        Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
        Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
        else -> Player.REPEAT_MODE_OFF
      }
    }
  }

  fun enqueue(track: TrackEntity, playNext: Boolean) {
    val player = controller ?: return
    if (track.uri.isBlank()) { notify(R.string.track_unavailable); return }
    sessionTracks[track.id] = track
    val index = if (playNext && player.currentMediaItem != null) {
      (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount)
    } else player.mediaItemCount
    runCatching {
      player.addMediaItem(index, mediaItem(track))
      if (player.playbackState == Player.STATE_IDLE) player.prepare()
      refreshQueue(player)
    }
    notify(if (playNext) R.string.play_next else R.string.add_to_queue)
  }

  fun moveQueue(from: Int, to: Int) {
    controller?.let { if (from in 0 until it.mediaItemCount && to in 0 until it.mediaItemCount) it.moveMediaItem(from, to) }
  }

  fun removeQueue(index: Int) {
    controller?.let { if (index in 0 until it.mediaItemCount) it.removeMediaItem(index) }
  }

  fun clearQueue() {
    controller?.let {
      val current = it.currentMediaItemIndex
      if (it.mediaItemCount > 1) {
        repeat(it.mediaItemCount) { index -> if (index != current && it.mediaItemCount > 1) it.removeMediaItem(if (index < current) index else current) }
      }
    }
  }

  private fun maybeRestoreSession() {
    if (restored) return
    restored = true
    if (!settingsStore.current.resumeOnLaunch) return
    viewModelScope.launch {
      val player = controller ?: return@launch
      if (player.mediaItemCount > 0) return@launch
      val saved = library.loadQueue() ?: return@launch
      val ids = saved.trackIdsJson
      val list = runCatching {
        org.json.JSONArray(ids).let { array ->
          (0 until array.length()).mapNotNull { index -> array.optString(index).takeIf { it.isNotBlank() } }
        }
      }.getOrDefault(emptyList())
      val known = tracks.value.associateBy { it.id }
      val items = list.mapNotNull(known::get)
      if (items.isEmpty()) return@launch
      player.setMediaItems(items.map(::mediaItem), saved.index.coerceIn(0, items.lastIndex), saved.positionMs)
      player.prepare()
      refreshQueue(player)
      refresh(player)
    }
  }

  // ---------------------------------------------------------------- library

  fun importUris(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      mutableImporting.value = true
      try {
        val result = library.importUris(uris)
        notify(R.string.import_result, result.added, result.skipped, result.failed)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        notify(R.string.import_failed, safe(e))
      } finally {
        mutableImporting.value = false
      }
    }
  }

  fun importTree(uri: Uri) {
    viewModelScope.launch {
      mutableImporting.value = true
      try {
        val result = library.importTree(uri)
        notify(if (result.limited) R.string.import_result_limited else R.string.import_result, result.added, result.skipped, result.failed)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        notify(R.string.import_failed, safe(e))
      } finally {
        mutableImporting.value = false
      }
    }
  }

  fun rescan() {
    viewModelScope.launch {
      mutableImporting.value = true
      try {
        val result = library.rescan()
        notify(R.string.settings_rescan_done, result.added, result.missing)
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        notify(R.string.action_failed, safe(e))
      } finally {
        mutableImporting.value = false
      }
    }
  }

  fun addDirectUrl(title: String, url: String): Boolean {
    val parsed = url.trim().toHttpUrlOrNull()
    if (title.isBlank() || parsed == null || parsed.username.isNotEmpty() || parsed.password.isNotEmpty()) {
      notify(R.string.invalid_stream); return false
    }
    val normalized = parsed.toString()
    viewModelScope.launch {
      val added = library.add(
        TrackEntity(
          id = stableId("url", normalized), uri = normalized, title = title.trim().take(200),
          source = Source.DIRECT, artist = parsed.host,
        ),
      )
      notify(if (added) R.string.added_to_library else R.string.already_in_library)
    }
    return true
  }

  fun addRemote(remote: RemoteTrack): Boolean {
    if (!remote.streamUrl.startsWith("http")) return false
    viewModelScope.launch {
      val added = library.add(
        TrackEntity(
          id = stableId("remote", remote.streamUrl), uri = remote.streamUrl, title = remote.title.take(200),
          artist = remote.artist, album = remote.album, durationMs = remote.durationMs,
          source = remote.source, artworkUri = remote.artworkUrl,
        ),
      )
      notify(if (added) R.string.added_to_library else R.string.already_in_library)
    }
    return true
  }

  fun playRemote(remote: RemoteTrack, list: List<RemoteTrack> = listOf(remote)) {
    val entities = list.map {
      TrackEntity(
        id = stableId("remote", it.streamUrl), uri = it.streamUrl, title = it.title.take(200),
        artist = it.artist, album = it.album, durationMs = it.durationMs,
        source = it.source, artworkUri = it.artworkUrl,
      )
    }
    val target = entities.firstOrNull { it.uri == remote.streamUrl } ?: return
    viewModelScope.launch { entities.forEach { entity -> library.add(entity) } }
    play(target, entities)
  }

  fun toggleFavorite(track: TrackEntity) {
    val added = listening.toggleFavorite(track.id)
    if (added && tracks.value.none { it.id == track.id }) {
      viewModelScope.launch { library.add(track) }
    }
  }

  fun startSelection(id: String) = mutableSelection.update { it + id }
  fun toggleSelection(id: String) = mutableSelection.update { if (id in it) it - id else it + id }
  fun selectAll(ids: Collection<String>) = mutableSelection.update { it + ids }
  fun clearSelection() = mutableSelection.update { emptySet() }

  fun deleteTracks(ids: Collection<String>) {
    viewModelScope.launch {
      val removed = library.delete(ids)
      removed.forEach { listening.removeTrack(it.id) }
      mutableSelection.value = emptySet()
      notify(R.string.tracks_removed, removed.size)
    }
  }

  fun restoreTracks(tracks: List<TrackEntity>) {
    viewModelScope.launch {
      library.restore(tracks)
      notify(R.string.restored)
    }
  }

  fun removeTrack(track: TrackEntity) = deleteTracks(listOf(track.id))

  // --------------------------------------------------------------- playlists

  fun createPlaylist(name: String) = viewModelScope.launch {
    if (library.createPlaylist(name) == null) notify(R.string.action_failed, "empty name") else notify(R.string.playlist_new)
  }

  fun renamePlaylist(id: String, name: String) = viewModelScope.launch { library.renamePlaylist(id, name) }
  fun deletePlaylist(id: String) = viewModelScope.launch { library.deletePlaylist(id) }
  fun duplicatePlaylist(id: String) = viewModelScope.launch { library.duplicatePlaylist(id) }
  fun addToPlaylist(playlistId: String, trackId: String) = viewModelScope.launch {
    library.addToPlaylist(playlistId, trackId)
    notify(R.string.playlist_track_count, (playlists.value.firstOrNull { it.id == playlistId }?.trackIds()?.size ?: 0) + 1)
  }
  fun addManyToPlaylist(playlistId: String, trackIds: List<String>) = viewModelScope.launch {
    trackIds.forEach { library.addToPlaylist(playlistId, it) }
    mutableSelection.value = emptySet()
    notify(R.string.playlist_track_count, playlists.value.firstOrNull { it.id == playlistId }?.trackIds()?.size ?: trackIds.size)
  }
  fun removeFromPlaylist(playlistId: String, trackId: String) = viewModelScope.launch {
    library.removeFromPlaylist(playlistId, trackId)
  }
  fun setPlaylistOrder(playlistId: String, ids: List<String>) = viewModelScope.launch {
    library.setPlaylistTracks(playlistId, ids)
  }

  fun playPlaylist(playlist: PlaylistEntity, shuffled: Boolean = false) {
    val byId = tracks.value.associateBy { it.id }
    val list = playlist.trackIds().mapNotNull(byId::get)
    if (list.isEmpty()) { notify(R.string.playlist_empty); return }
    if (shuffled) playShuffled(list) else play(list.first(), list)
  }

  fun publishPlaylist(playlist: PlaylistEntity, public: Boolean) = viewModelScope.launch {
    mutableSyncBusy.value = true
    try {
      val url = gist.publishPlaylist(playlist, tracks.value, public, android.os.Build.MODEL)
      notify(R.string.sync_published)
      getApplication<Application>().startActivity(
        Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
      )
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.sync_failed, safe(e))
    } finally {
      mutableSyncBusy.value = false
    }
  }

  // ------------------------------------------------------------------ search

  fun setSearchQuery(value: String) {
    mutableSearch.value = value
    mutableSearching.value = value.isNotBlank()
  }

  fun submitSearch(value: String) {
    if (value.isNotBlank()) settingsStore.addSearchTerm(value)
  }

  val searchResults: StateFlow<List<TrackEntity>> = combine(tracks, searchQuery) { list, query ->
    if (query.isBlank()) emptyList() else rank(list, query).take(80)
  }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(2_000), emptyList())

  private fun rank(list: List<TrackEntity>, query: String): List<TrackEntity> {
    val needle = query.trim().lowercase(Locale.getDefault())
    if (needle.isEmpty()) return emptyList()
    val tokens = needle.split(' ').filter { it.isNotBlank() }
    return list.mapNotNull { track ->
      val title = track.title.lowercase(Locale.getDefault())
      val artist = track.artist.lowercase(Locale.getDefault())
      val album = track.album.lowercase(Locale.getDefault())
      val haystack = "$title $artist $album ${track.uri.lowercase(Locale.getDefault())}"
      if (!tokens.all { haystack.contains(it) }) return@mapNotNull null
      var score = 0
      if (title == needle) score += 120
      if (title.startsWith(needle)) score += 70
      if (title.contains(needle)) score += 45
      if (artist.startsWith(needle)) score += 35
      if (artist.contains(needle)) score += 20
      if (album.contains(needle)) score += 12
      if (needle.split(' ').size > 1) score += 10
      (track to score)
    }.sortedWith(compareByDescending<Pair<TrackEntity, Int>> { it.second }.thenBy { it.first.title.lowercase() })
      .map { it.first }
  }

  // --------------------------------------------------------------- discovery

  fun setDiscoverySource(source: DiscoverySource) {
    searchJob?.cancel()
    mutableDiscovery.update { it.copy(source = source, searched = false, results = emptyList(), error = null, loading = false) }
  }

  fun setDiscoveryQuery(value: String) = mutableDiscovery.update { it.copy(query = value) }
  fun setCountry(value: String) = mutableDiscovery.update { it.copy(country = value.uppercase(Locale.US).take(2)) }

  fun saveCustomApi(name: String, template: String): Boolean {
    if (name.isBlank() || !com.hotsteel.aurapulse.data.discovery.DiscoveryClient.isValidTemplate(template)) {
      notify(R.string.invalid_custom_source); return false
    }
    prefs().edit().putString("custom_name", name.trim()).putString("custom_template", template.trim()).apply()
    mutableDiscovery.update { it.copy(customName = name.trim(), customTemplate = template.trim()) }
    return true
  }

  fun searchDiscovery() {
    val state = mutableDiscovery.value
    if (state.source != DiscoverySource.RADIO && state.query.isBlank()) { notify(R.string.enter_search); return }
    if (state.source == DiscoverySource.RADIO && state.query.isBlank() && !state.country.matches(Regex("[A-Z]{2}"))) {
      notify(R.string.invalid_country); return
    }
    if (state.source == DiscoverySource.CUSTOM && state.customTemplate.isBlank()) { notify(R.string.configure_custom_first); return }
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      mutableDiscovery.update { it.copy(loading = true, searched = true, error = null) }
      try {
        val results = when (state.source) {
          DiscoverySource.PREVIEWS -> container.discovery.previews(state.query, state.country)
          DiscoverySource.RADIO -> container.discovery.radio(state.query, state.country)
          DiscoverySource.CUSTOM -> container.discovery.custom(state.customTemplate, state.query)
        }
        mutableDiscovery.update { it.copy(loading = false, results = results) }
      } catch (e: CancellationException) {
        throw e
      } catch (e: Exception) {
        mutableDiscovery.update { it.copy(loading = false, results = emptyList(), error = safe(e)) }
      }
    }
  }

  fun browseRadioTag(tag: String) {
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      mutableDiscovery.update { it.copy(source = DiscoverySource.RADIO, query = tag, loading = true, searched = true, error = null) }
      runCatching { container.discovery.radioByTag(tag) }
        .onSuccess { results -> mutableDiscovery.update { it.copy(loading = false, results = results) } }
        .onFailure { error -> mutableDiscovery.update { it.copy(loading = false, error = safe(error)) } }
    }
  }

  fun importPlaylistFromUrl(url: String) = viewModelScope.launch {
    try {
      val imported = container.importer.fetch(url)
      val playlist = library.createPlaylist(imported.name)
      var added = 0
      val ids = mutableListOf<String>()
      imported.entries.forEach { entry ->
        if (!entry.url.startsWith("http")) return@forEach
        val track = TrackEntity(
          id = stableId("remote", entry.url), uri = entry.url, title = entry.title.take(200),
          artist = entry.artist, durationMs = entry.durationMs, source = Source.DIRECT,
        )
        if (library.add(track)) added++
        ids += track.id
      }
      if (playlist != null) library.setPlaylistTracks(playlist.id, ids)
      notify(R.string.playlist_imported, added)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.playlist_import_failed)
    }
  }

  // --------------------------------------------------------------- downloads

  fun download(track: TrackEntity) {
    if (downloadJobs.containsKey(track.id)) return
    val job = viewModelScope.launch {
      try {
        mutableDownloads.update { it + (track.id to DownloadProgress(0, null)) }
        var last = 0L
        val path = container.downloads.download(track) { bytes, total ->
          val now = System.nanoTime() / 1_000_000
          if (now - last > 250 || (total != null && bytes >= total)) {
            last = now
            mutableDownloads.update { it + (track.id to DownloadProgress(bytes, total)) }
          }
        }
        val fresh = library.tracksSnapshot().firstOrNull { it.id == track.id }
        if (fresh != null) library.add(fresh.copy(downloadedPath = path))
        notify(R.string.download_complete)
      } catch (e: CancellationException) {
        notify(R.string.download_cancelled); throw e
      } catch (_: com.hotsteel.aurapulse.data.library.SegmentedStreamException) {
        notify(R.string.download_stream_not_supported)
      } catch (e: Exception) {
        notify(R.string.download_failed, safe(e))
      } finally {
        mutableDownloads.update { it - track.id }
        downloadJobs.remove(track.id)
      }
    }
    downloadJobs[track.id] = job
  }

  fun cancelDownload(id: String) { downloadJobs[id]?.cancel(); container.downloads.cancel(id) }

  fun removeDownload(track: TrackEntity) = viewModelScope.launch {
    container.downloads.remove(track.downloadedPath)
    library.add(track.copy(downloadedPath = null))
    notify(R.string.download_removed)
  }

  // ------------------------------------------------------------------ lyrics

  fun attachLyrics(trackId: String, uri: Uri) = viewModelScope.launch {
    try {
      val text = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
      } ?: error("unreadable")
      val parsed = LrcParser.parse(text)
      if (parsed.lines.isEmpty()) { notify(R.string.lyrics_parse_failed); return@launch }
      library.putLyrics(LyricEntity(trackId, text.take(400_000), uri.toString(), 0))
      mutableLyrics.value = LyricsBundle(trackId, parsed, 0)
      notify(R.string.lyrics_loaded)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.lyrics_parse_failed)
    }
  }

  fun detachLyrics(trackId: String) = viewModelScope.launch {
    library.deleteLyrics(trackId)
    mutableLyrics.value = null
  }

  fun setLyricsOffset(trackId: String, offsetMs: Int) = viewModelScope.launch {
    val stored = library.lyrics(trackId) ?: return@launch
    library.putLyrics(stored.copy(offsetMs = offsetMs))
    val parsed = LrcParser.parse(stored.content)
    mutableLyrics.value = LyricsBundle(trackId, parsed, offsetMs)
  }

  // --------------------------------------------------------------- equalizer

  fun setEqEnabled(enabled: Boolean) = EqualizerController.setEnabled(enabled)
  fun setEqBand(index: Int, level: Int) = EqualizerController.setBand(index, level)
  fun applyPreset(preset: EqPreset) = EqualizerController.applyPreset(preset)
  fun savePreset(name: String) { EqualizerController.saveCustomPreset(name); notify(R.string.preset_saved) }
  fun deletePreset(id: String) { EqualizerController.deleteCustomPreset(id); notify(R.string.preset_deleted) }
  fun setBass(enabled: Boolean, strength: Int) = EqualizerController.setBass(enabled, strength)
  fun setVirtualizer(enabled: Boolean, strength: Int) = EqualizerController.setVirtualizer(enabled, strength)
  fun setLoudness(enabled: Boolean, gain: Int) = EqualizerController.setLoudness(enabled, gain)
  fun resetEq() = EqualizerController.reset()

  fun setSpeed(value: Float) = PlaybackEnhancements.setSpeed(value)
  fun setPitch(value: Float) = PlaybackEnhancements.setPitch(value)
  fun setVolume(value: Float) = PlaybackEnhancements.setVolume(value)
  fun setSkipSilence(value: Boolean) = PlaybackEnhancements.setSkipSilence(value)
  fun setLoopA() {
    val player = controller ?: return
    if (!player.isCurrentMediaItemSeekable) { notify(R.string.loop_unavailable); return }
    PlaybackEnhancements.setLoopStart(player.currentPosition)
  }
  fun setLoopB() {
    val player = controller ?: return
    if (!PlaybackEnhancements.setLoopEnd(player.currentPosition)) notify(R.string.loop_invalid_end)
  }
  fun clearLoop() = PlaybackEnhancements.clearLoop()
  fun resetSound() = PlaybackEnhancements.reset()

  fun startSleep(minutes: Int) = SleepTimer.start(minutes)
  fun sleepAtTrackEnd() = SleepTimer.endOfTrack()
  fun cancelSleep() = SleepTimer.cancel()

  // ------------------------------------------------------------------ github

  fun signIn(token: String) = viewModelScope.launch {
    try {
      val session = account.signIn(token)
      notify(R.string.account_signed_in, session.user.login)
      refreshSyncState()
    } catch (e: CancellationException) {
      throw e
    } catch (e: GitHubException) {
      notify(if (e.isUnauthorized) R.string.account_token_invalid else R.string.action_failed, safe(e))
    } catch (e: Exception) {
      notify(R.string.action_failed, safe(e))
    }
  }

  fun startDeviceFlow() {
    if (BuildConfig.GITHUB_CLIENT_ID.isBlank()) { notify(R.string.account_client_missing); return }
    deviceJob?.cancel()
    deviceJob = viewModelScope.launch {
      try {
        val code = account.api().requestDeviceCode(BuildConfig.GITHUB_CLIENT_ID, com.hotsteel.aurapulse.data.github.GitHubAccount.SCOPES)
        mutableDeviceFlow.value = DeviceFlow(code)
        val token = account.api().awaitDeviceToken(BuildConfig.GITHUB_CLIENT_ID, code) { result ->
          if (result is DeviceTokenResult.SlowDown) mutableDeviceFlow.update { it?.copy(waiting = true) }
        }
        val session = account.signIn(token)
        mutableDeviceFlow.value = null
        notify(R.string.account_signed_in, session.user.login)
        refreshSyncState()
      } catch (e: CancellationException) {
        mutableDeviceFlow.value = null
        throw e
      } catch (e: Exception) {
        mutableDeviceFlow.value = null
        notify(R.string.action_failed, safe(e))
      }
    }
  }

  fun cancelDeviceFlow() {
    deviceJob?.cancel()
    mutableDeviceFlow.value = null
  }

  fun signOut() {
    account.signOut()
    notify(R.string.account_signed_out)
  }

  fun refreshSyncState() {
    viewModelScope.launch { mutableSync.value = runCatching { gist.check() }.getOrDefault(gist.state()) }
  }

  fun pushBackup() = viewModelScope.launch {
    if (account.session == null) { notify(R.string.sync_needs_account); return@launch }
    mutableSyncBusy.value = true
    try {
      val result = gist.push()
      mutableSync.value = gist.state()
      notify(R.string.sync_push_done, result.tracks, result.playlists)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.sync_failed, safe(e))
    } finally {
      mutableSyncBusy.value = false
    }
  }

  fun pullBackup() = viewModelScope.launch {
    if (account.session == null) { notify(R.string.sync_needs_account); return@launch }
    mutableSyncBusy.value = true
    try {
      val result = gist.pull()
      mutableSync.value = gist.state()
      notify(R.string.sync_pull_done, result.tracks, result.playlists)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.sync_failed, safe(e))
    } finally {
      mutableSyncBusy.value = false
    }
  }

  fun exportBackup(uri: Uri) = viewModelScope.launch {
    try {
      val json = gist.exportJson()
      getApplication<Application>().contentResolver.openOutputStream(uri)?.use { output ->
        output.write(json.toByteArray(Charsets.UTF_8))
      } ?: error("cannot write")
      notify(R.string.sync_push_done, tracks.value.size, playlists.value.size)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.action_failed, safe(e))
    }
  }

  fun importBackup(uri: Uri) = viewModelScope.launch {
    try {
      val json = getApplication<Application>().contentResolver.openInputStream(uri)?.use { input ->
        input.readBytes().toString(Charsets.UTF_8)
      } ?: error("cannot read")
      val result = gist.importJson(json, uri.toString())
      notify(R.string.sync_pull_done, result.tracks, result.playlists)
    } catch (e: CancellationException) {
      throw e
    } catch (e: Exception) {
      notify(R.string.action_failed, safe(e))
    }
  }

  // --------------------------------------------------------------- insights

  fun refreshInsights() = viewModelScope.launch {
    try {
      val since = System.currentTimeMillis() - 8L * 24 * 3600 * 1000
      val events: List<PlayEventEntity> = library.eventsSince(since)
      val all = library.totalMs()
      val statsList = library.topStats()
      val byId = tracks.value.associateBy { it.id }
      val dayKeys = (0..6).map { offset ->
        val calendar = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -offset) }
        calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR) to offset
      }.toMap()
      val week = MutableList(7) { 0L }
      val hours = MutableList(24) { 0 }
      events.forEach { event ->
        val calendar = Calendar.getInstance().apply { timeInMillis = event.startedAt }
        hours[calendar.get(Calendar.HOUR_OF_DAY)] += 1
        val key = calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
        val offset = dayKeys[key] ?: return@forEach
        week[6 - offset] += event.listenedMs
      }
      val playedDays = events.map {
        val calendar = Calendar.getInstance().apply { timeInMillis = it.startedAt }
        calendar.get(Calendar.YEAR) * 1000 + calendar.get(Calendar.DAY_OF_YEAR)
      }.toSet()
      var streak = 0
      var cursor = Calendar.getInstance()
      while (true) {
        val key = cursor.get(Calendar.YEAR) * 1000 + cursor.get(Calendar.DAY_OF_YEAR)
        if (key in playedDays) { streak++; cursor.add(Calendar.DAY_OF_YEAR, -1) } else break
      }
      val topTracks = statsList.sortedByDescending { it.plays }.mapNotNull { stat -> byId[stat.trackId]?.let { it to stat } }.take(5)
      val artists = statsList.mapNotNull { stat -> byId[stat.trackId]?.let { (it.artist.ifBlank { it.albumArtist }).ifBlank { "—" } to stat.plays } }
        .groupBy({ it.first }, { it.second }).map { (artist, plays) -> artist to plays.sum() }
        .sortedByDescending { it.second }.take(5)
      mutableInsights.value = InsightsUi(
        totalMs = all,
        plays = statsList.sumOf { it.plays },
        distinctTracks = statsList.size,
        streak = streak,
        topTracks = topTracks,
        topArtists = artists,
        week = week,
        hours = hours,
      )
    } catch (_: Exception) {
      // Insights are best-effort; the screen simply keeps the previous numbers.
    }
  }

  fun clearListening() = viewModelScope.launch {
    library.clearListening()
    listening.clearHistory()
    refreshInsights()
  }

  // ---------------------------------------------------------------- settings

  fun updateSettings(transform: (AppSettings) -> AppSettings) = settingsStore.update(transform)
  fun setLanguage(tag: String) = settingsStore.setLanguage(tag)
  fun clearSearchHistory() = settingsStore.clearSearchHistory()

  fun clearArtworkCache() {
    library.clearArtworkCache()
    notify(R.string.settings_cache_cleared)
  }

  private val mutableStorage = MutableStateFlow(0L)
  val storage: StateFlow<Long> = mutableStorage.asStateFlow()

  fun refreshStorage() = viewModelScope.launch { mutableStorage.value = library.storageBytes() }

  fun sortedTracks(list: List<TrackEntity>, sort: TrackSort, favorites: Set<String>, stats: Map<String, PlayStat>, filter: LibraryFilter): List<TrackEntity> {
    val filtered = when (filter) {
      LibraryFilter.ALL -> list
      LibraryFilter.FAVORITES -> list.filter { it.id in favorites }
      LibraryFilter.DOWNLOADED -> list.filter { it.downloadedPath != null }
    }
    return when (sort) {
      TrackSort.ADDED -> filtered.sortedByDescending { it.addedAt }
      TrackSort.TITLE -> filtered.sortedBy { it.title.lowercase() }
      TrackSort.ARTIST -> filtered.sortedWith(compareBy({ it.artist.ifBlank { "\uFFFF" }.lowercase() }, { it.album.lowercase() }, { it.trackNumber }))
      TrackSort.DURATION -> filtered.sortedByDescending { it.durationMs }
      TrackSort.PLAYS -> filtered.sortedByDescending { stats[it.id]?.plays ?: 0 }
    }
  }

  // ---------------------------------------------------------------- internal

  private fun prefs() = getApplication<Application>().getSharedPreferences("aurapulse_discovery", Application.MODE_PRIVATE)

  private fun notify(@StringRes resource: Int, vararg args: Any) {
    noticesChannel.trySend(Notice(resource, args.toList()))
  }

  private fun safe(error: Throwable): String =
    (error.cause?.localizedMessage ?: error.localizedMessage ?: error.javaClass.simpleName)
      .replace(Regex("[\\r\\n]+"), " ").take(200)

  override fun onCleared() {
    downloadJobs.values.forEach { it.cancel() }
    controller?.removeListener(listener)
    MediaController.releaseFuture(controllerFuture)
    noticesChannel.close()
    openPlayerChannel.close()
    super.onCleared()
  }
}
