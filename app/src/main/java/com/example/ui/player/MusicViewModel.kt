package com.example.ui.player

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.PlaybackException
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.example.R
import com.example.audio.DeviceEqualizer
import com.example.audio.PlaybackEnhancements
import com.example.audio.PlaybackService
import com.example.data.cloud.CloudBackup
import com.example.data.cloud.CredentialFallbackCancelled
import com.example.data.cloud.GoogleAccount
import com.example.data.cloud.MissingFirebaseConfiguration
import com.example.data.discovery.DiscoverySource
import com.example.data.discovery.MusicDiscovery
import com.example.data.library.AudioDownloadManager
import com.example.data.library.LibraryDatabase
import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryRepository
import com.example.data.library.LibraryTrack
import com.example.data.library.ListeningStore
import com.example.data.library.TrackSource
import com.example.data.library.UnsupportedStreamingPlaylistException
import com.example.data.library.stableTrackId
import com.google.android.gms.common.api.ApiException
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.FirebaseFirestore
import java.io.File
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

data class Notice(@param:StringRes val resource: Int, val args: List<Any> = emptyList())
data class PlaybackUiState(
  val track: LibraryTrack? = null,
  val isPlaying: Boolean = false,
  val isBuffering: Boolean = false,
  val positionMs: Long = 0,
  val durationMs: Long = 0,
  val canSeek: Boolean = false,
  val shuffle: Boolean = false,
  val repeatMode: Int = Player.REPEAT_MODE_OFF,
  val queueIndex: Int = -1,
)
data class DiscoveryUiState(
  val source: DiscoverySource = DiscoverySource.PREVIEWS,
  val query: String = "",
  val country: String = "US",
  val customName: String = "",
  val customTemplate: String = "",
  val loading: Boolean = false,
  val hasSearched: Boolean = false,
  val results: List<LibraryTrack> = emptyList(),
  val error: String? = null,
)
data class DownloadProgress(val bytes: Long, val total: Long?)

class MusicViewModel(application: Application) : AndroidViewModel(application) {
  private val database = LibraryDatabase.get(application)
  private val library = LibraryRepository(application, database)
  private val listening = ListeningStore.get(application)
  private val downloads = AudioDownloadManager(application, database)
  private val discovery = MusicDiscovery()
  private val prefs = application.getSharedPreferences("discovery", Application.MODE_PRIVATE)
  private val auth: FirebaseAuth? = runCatching {
    if (FirebaseApp.getApps(application).isNotEmpty()) FirebaseAuth.getInstance() else null
  }.getOrNull()
  private val authListener = FirebaseAuth.AuthStateListener { mutableUser.value = it.currentUser }
  private val downloadJobs = mutableMapOf<String, Job>()
  private var searchJob: Job? = null
  private val sessionTracks = mutableMapOf<String, LibraryTrack>()
  private var indexedTracks: Map<String, LibraryTrack> = emptyMap()
  private var controller: MediaController? = null
  val playerReady: Boolean get() = controller != null

  val tracks = library.tracks.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val playlists = library.playlists.stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
  val equalizer = DeviceEqualizer.state
  val enhancements = PlaybackEnhancements.state
  val favorites = listening.favorites
  val history = listening.history
  val resume = listening.resume
  private val mutableQueue = MutableStateFlow<List<LibraryTrack>>(emptyList())
  val queue = mutableQueue.asStateFlow()
  private val mutablePlayback = MutableStateFlow(PlaybackUiState())
  val playback: StateFlow<PlaybackUiState> = mutablePlayback.asStateFlow()
  private val mutableDiscovery = MutableStateFlow(DiscoveryUiState(
    country = prefs.getString("country", null) ?: Locale.getDefault().country.uppercase().takeIf { it.matches(Regex("[A-Z]{2}")) } ?: "US",
    customName = prefs.getString("custom_name", "").orEmpty(),
    customTemplate = prefs.getString("custom_template", "").orEmpty(),
  ))
  val discoveryState = mutableDiscovery.asStateFlow()
  private val mutableUser = MutableStateFlow(auth?.currentUser)
  val user: StateFlow<FirebaseUser?> = mutableUser.asStateFlow()
  val firebaseConfigured = auth != null && application.resources.getIdentifier("default_web_client_id", "string", application.packageName).let { id ->
    id != 0 && application.getString(id).isNotBlank()
  }
  private val mutableSignInIssue = MutableStateFlow<String?>(null)
  val signInIssue = mutableSignInIssue.asStateFlow()
  private val mutableBusy = MutableStateFlow(false)
  val busy = mutableBusy.asStateFlow()
  private val mutableImporting = MutableStateFlow(false)
  val importing = mutableImporting.asStateFlow()
  private val mutableDownloads = MutableStateFlow<Map<String, DownloadProgress>>(emptyMap())
  val downloadState = mutableDownloads.asStateFlow()
  // Queue errors raised during startup until the activity is ready to display them.
  private val mutableNotices = Channel<Notice>(Channel.BUFFERED)
  val notices = mutableNotices.receiveAsFlow()
  // Only deliberate play requests open the full player. Media transitions, notification
  // controls and automatic next tracks do not interrupt the current screen.
  private val mutableOpenPlayerRequests = Channel<Unit>(Channel.CONFLATED)
  val openPlayerRequests = mutableOpenPlayerRequests.receiveAsFlow()

  private val controllerFuture = MediaController.Builder(
    application, SessionToken(application, ComponentName(application, PlaybackService::class.java))
  ).buildAsync()

  private val playerListener = object : Player.Listener {
    override fun onEvents(player: Player, events: Player.Events) {
      if (events.contains(Player.EVENT_TIMELINE_CHANGED)) refreshQueue(player)
      refreshPlayback(player)
    }
    override fun onPlayerError(error: PlaybackException) {
      notify(R.string.playback_error, safeError(error))
      controller?.let(::refreshPlayback)
    }
  }

  init {
    auth?.addAuthStateListener(authListener)
    controllerFuture.addListener({
      try {
        controller = controllerFuture.get().also {
          it.addListener(playerListener)
          refreshQueue(it)
          refreshPlayback(it)
        }
      } catch (e: Exception) { notify(R.string.player_unavailable, safeError(e)) }
    }, ContextCompat.getMainExecutor(application))
    viewModelScope.launch {
      tracks.collect { list ->
        indexedTracks = list.associateBy { it.id }
        controller?.let { refreshQueue(it); refreshPlayback(it) }
      }
    }
    viewModelScope.launch {
      while (isActive) {
        val current = controller
        val observed = mutablePlayback.subscriptionCount.value > 0
        if (current?.isPlaying == true && observed) refreshPlayback(current)
        delay(if (current?.isPlaying == true && observed) 500L else 2_000L)
      }
    }
  }

  private fun trackFromItem(item: MediaItem): LibraryTrack =
    indexedTracks[item.mediaId] ?: sessionTracks[item.mediaId] ?: LibraryTrack(
      id = item.mediaId, uri = item.localConfiguration?.uri?.toString().orEmpty(),
      title = item.mediaMetadata.title?.toString().orEmpty(), artist = item.mediaMetadata.artist?.toString().orEmpty(),
      album = item.mediaMetadata.albumTitle?.toString().orEmpty(),
      source = TrackSource.DIRECT, artworkUri = item.mediaMetadata.artworkUri?.toString(),
    )

  private fun refreshQueue(player: Player) {
    val list = (0 until player.mediaItemCount).map { trackFromItem(player.getMediaItemAt(it)) }
    if (mutableQueue.value != list) mutableQueue.value = list
  }

  private fun refreshPlayback(player: Player) {
    val found = player.currentMediaItem?.let(::trackFromItem)
    val duration = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: 0L
    mutablePlayback.value = PlaybackUiState(
      track = found, isPlaying = player.isPlaying,
      isBuffering = player.playbackState == Player.STATE_BUFFERING,
      positionMs = player.currentPosition.coerceAtLeast(0), durationMs = duration,
      canSeek = player.isCurrentMediaItemSeekable && duration > 0,
      shuffle = player.shuffleModeEnabled, repeatMode = player.repeatMode,
      queueIndex = player.currentMediaItemIndex.takeIf { player.currentMediaItem != null } ?: -1,
    )
  }

  private fun toMediaItem(item: LibraryTrack): MediaItem {
    val localCopy = item.downloadedPath?.let(::File)?.takeIf { it.isFile }
    val playbackUri = localCopy?.let(Uri::fromFile) ?: Uri.parse(item.uri)
    return MediaItem.Builder().setMediaId(item.id).setUri(playbackUri).setMediaMetadata(
      MediaMetadata.Builder().setTitle(item.title).setArtist(item.artist).setAlbumTitle(item.album)
        .setArtworkUri(item.artworkUri?.let(Uri::parse)).build()
    ).build()
  }

  fun play(track: LibraryTrack, list: List<LibraryTrack> = listOf(track), startPositionMs: Long = 0L) {
    val player = controller ?: run { notify(R.string.player_connecting); return }
    val playable = list.distinctBy { it.id }.filter { it.uri.isNotBlank() }
    val startIndex = playable.indexOfFirst { it.id == track.id }
    if (startIndex < 0) return
    playable.forEach { sessionTracks[it.id] = it }
    try {
      player.setMediaItems(playable.map(::toMediaItem), startIndex, startPositionMs.coerceAtLeast(0))
      player.prepare()
      player.play()
      refreshQueue(player)
      refreshPlayback(player)
      mutableOpenPlayerRequests.trySend(Unit)
    } catch (e: Exception) { notify(R.string.playback_error, safeError(e)) }
  }

  fun resumeLast() {
    val point = resume.value ?: return
    val track = indexedTracks[point.trackId] ?: return
    play(track, tracks.value, point.positionMs)
  }

  fun toggleFavorite(track: LibraryTrack) = viewModelScope.launch {
    try {
      if (track.id !in favorites.value && track.id !in indexedTracks) {
        if (track.uri.isBlank()) { notify(R.string.track_unavailable); return@launch }
        library.addTrack(track)
      }
      listening.toggleFavorite(track.id)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }

  fun clearHistory() = listening.clearHistory()

  fun enqueue(track: LibraryTrack, playNext: Boolean) {
    val player = controller ?: run { notify(R.string.player_connecting); return }
    if (track.uri.isBlank()) { notify(R.string.track_unavailable); return }
    try {
      sessionTracks[track.id] = track
      val index = if (playNext && player.currentMediaItem != null) (player.currentMediaItemIndex + 1).coerceAtMost(player.mediaItemCount) else player.mediaItemCount
      player.addMediaItem(index, toMediaItem(track))
      if (player.playbackState == Player.STATE_IDLE) player.prepare()
      refreshQueue(player)
      notify(if (playNext) R.string.queued_next else R.string.added_to_queue)
    } catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }

  fun playQueueItem(index: Int) {
    controller?.let { player ->
      if (index in 0 until player.mediaItemCount) { player.seekTo(index, 0); player.prepare(); player.play() }
    }
  }
  fun moveQueueItem(from: Int, to: Int) {
    controller?.let { player ->
      if (from in 0 until player.mediaItemCount && to in 0 until player.mediaItemCount) player.moveMediaItem(from, to)
    }
  }
  fun removeQueueItem(index: Int) {
    controller?.let { player -> if (index in 0 until player.mediaItemCount) player.removeMediaItem(index) }
  }
  fun jumpBy(deltaMs: Long) {
    controller?.let { player -> if (player.isCurrentMediaItemSeekable) {
      val end = player.duration.takeIf { it != C.TIME_UNSET && it > 0 } ?: Long.MAX_VALUE
      player.seekTo((player.currentPosition + deltaMs).coerceIn(0L, end))
    } }
  }
  fun setLoopStart() { if (!PlaybackEnhancements.setLoopStart()) notify(R.string.loop_unavailable) }
  fun setLoopEnd() { if (!PlaybackEnhancements.setLoopEnd()) notify(R.string.loop_invalid_end) }

  fun togglePlay() {
    val player = controller ?: return
    if (player.playbackState == Player.STATE_ENDED) {
      player.seekTo(0)
      player.play()
    } else if (player.isPlaying || player.playWhenReady) player.pause()
    else player.play()
  }
  fun next() { controller?.seekToNextMediaItem() }
  fun previous() { controller?.let { if (it.isCurrentMediaItemSeekable && it.currentPosition > 3000) it.seekTo(0) else it.seekToPreviousMediaItem() } }
  fun seek(positionMs: Long) { controller?.seekTo(positionMs.coerceAtLeast(0)) }
  fun toggleShuffle() { controller?.let { it.shuffleModeEnabled = !it.shuffleModeEnabled } }
  fun cycleRepeat() { controller?.let { it.repeatMode = when (it.repeatMode) {
    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
    else -> Player.REPEAT_MODE_OFF
  } } }

  fun importFiles(uris: List<Uri>) {
    if (uris.isEmpty()) return
    viewModelScope.launch {
      mutableImporting.value = true
      try {
        val result = library.importDocuments(uris)
        notify(R.string.import_result, result.added, result.skipped, result.failed)
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { notify(R.string.import_failed, safeError(e)) }
      finally { mutableImporting.value = false }
    }
  }
  fun importFolder(uri: Uri) {
    viewModelScope.launch {
      mutableImporting.value = true
      try {
        val result = library.importFolder(uri)
        notify(if (result.limited) R.string.import_result_limited else R.string.import_result, result.added, result.skipped, result.failed)
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { notify(R.string.import_failed, safeError(e)) }
      finally { mutableImporting.value = false }
    }
  }

  fun addTrack(track: LibraryTrack) = viewModelScope.launch {
    try { notify(if (library.addTrack(track)) R.string.added_to_library else R.string.already_in_library) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun removeTrack(track: LibraryTrack) = viewModelScope.launch {
    try { downloadJobs[track.id]?.cancel(); downloads.cancel(track.id); library.deleteTrack(track); listening.removeTrack(track.id); notify(R.string.removed_from_library) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun addDirectUrl(title: String, value: String): Boolean {
    val url = value.trim().toHttpUrlOrNull()
    if (title.isBlank() || url == null || url.username.isNotEmpty() || url.password.isNotEmpty()) {
      notify(R.string.invalid_stream); return false
    }
    val normalized = url.toString()
    addTrack(LibraryTrack(id = stableTrackId(normalized), uri = normalized, title = title.trim().take(200), source = TrackSource.DIRECT))
    return true
  }

  fun download(track: LibraryTrack) {
    if (downloadJobs.containsKey(track.id)) return
    val job = viewModelScope.launch {
      try {
        mutableDownloads.update { it + (track.id to DownloadProgress(0, null)) }
        var lastUpdate = 0L
        downloads.download(track) { bytes, total ->
          val now = System.nanoTime() / 1_000_000
          if (now - lastUpdate >= 250L || (total != null && bytes >= total)) {
            lastUpdate = now
            mutableDownloads.update { it + (track.id to DownloadProgress(bytes, total)) }
          }
        }
        notify(R.string.download_complete)
      } catch (e: CancellationException) { notify(R.string.download_cancelled); throw e }
      catch (_: UnsupportedStreamingPlaylistException) { notify(R.string.download_stream_not_supported) }
      catch (e: Exception) { notify(R.string.download_failed, safeError(e)) }
      finally { mutableDownloads.update { it - track.id }; downloadJobs.remove(track.id) }
    }
    downloadJobs[track.id] = job
  }
  fun cancelDownload(id: String) { downloadJobs[id]?.cancel(); downloads.cancel(id) }
  fun removeDownload(track: LibraryTrack) = viewModelScope.launch {
    try { downloads.removeDownload(track); notify(R.string.download_removed) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }

  fun createPlaylist(name: String) = viewModelScope.launch {
    try { library.createPlaylist(name); notify(R.string.playlist_created) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun renamePlaylist(playlist: LibraryPlaylist, name: String) = viewModelScope.launch {
    try { library.renamePlaylist(playlist.id, name); notify(R.string.playlist_updated) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun setPlaylistTracks(playlist: LibraryPlaylist, ids: List<String>) = viewModelScope.launch {
    try { library.setPlaylistTracks(playlist.id, ids); notify(R.string.playlist_updated) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun addTrackToPlaylist(playlist: LibraryPlaylist, track: LibraryTrack) = viewModelScope.launch {
    try {
      val latest = database.libraryDao().getPlaylist(playlist.id) ?: return@launch
      library.setPlaylistTracks(latest.id, latest.trackIds() + track.id)
      notify(R.string.playlist_updated)
    } catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun deletePlaylist(playlist: LibraryPlaylist) = viewModelScope.launch {
    try { library.deletePlaylist(playlist.id); notify(R.string.playlist_deleted) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
  }
  fun playPlaylist(playlist: LibraryPlaylist) {
    val available = tracks.value.associateBy { it.id }
    val list = playlist.trackIds().mapNotNull(available::get)
    if (list.isEmpty()) notify(R.string.playlist_no_available_tracks) else play(list.first(), list)
  }

  fun setDiscoverySource(source: DiscoverySource) {
    searchJob?.cancel()
    mutableDiscovery.update { it.copy(source = source, loading = false, hasSearched = false, results = emptyList(), error = null) }
  }
  fun setDiscoveryQuery(query: String) { mutableDiscovery.update { it.copy(query = query) } }
  fun setCountry(value: String) { mutableDiscovery.update { it.copy(country = value.uppercase().take(2)) } }
  fun saveCustomSource(name: String, template: String): Boolean {
    val url = template.trim().replace("{query}", "test").replace("{country}", "US").toHttpUrlOrNull()
    if (name.isBlank() || !template.contains("{query}") || url == null || !url.isHttps || url.username.isNotEmpty() || url.password.isNotEmpty()) {
      notify(R.string.invalid_custom_source); return false
    }
    prefs.edit().putString("custom_name", name.trim().take(80)).putString("custom_template", template.trim()).apply()
    mutableDiscovery.update { it.copy(customName = name.trim().take(80), customTemplate = template.trim(), results = emptyList(), hasSearched = false) }
    return true
  }
  fun search() {
    val s = mutableDiscovery.value
    if (s.query.isBlank()) { notify(R.string.enter_search); return }
    if (s.source != DiscoverySource.RADIO && !s.country.matches(Regex("[A-Z]{2}"))) { notify(R.string.invalid_country); return }
    if (s.source == DiscoverySource.CUSTOM && s.customTemplate.isBlank()) { notify(R.string.configure_custom_first); return }
    prefs.edit().putString("country", s.country).apply()
    searchJob?.cancel()
    searchJob = viewModelScope.launch {
      mutableDiscovery.update { it.copy(loading = true, hasSearched = true, error = null, results = emptyList()) }
      try {
        val results = discovery.search(s.source, s.query, s.country, s.customTemplate)
        mutableDiscovery.update { it.copy(loading = false, results = results) }
      } catch (e: CancellationException) { throw e }
      catch (e: Exception) { mutableDiscovery.update { it.copy(loading = false, error = safeError(e)) } }
    }
  }

  fun signIn(activity: Activity, useAccountSelector: Boolean = false) = viewModelScope.launch {
    if (mutableBusy.value) return@launch
    mutableBusy.value = true
    mutableSignInIssue.value = null
    try { GoogleAccount.signIn(activity, useAccountSelector); notify(R.string.signed_in) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { reportSignInFailure(activity, e) }
    finally { mutableBusy.value = false }
  }

  /** Explicit fallback for Credential Manager incompatibilities, not a bypass of Firebase. */
  fun startCompatibilitySignIn(activity: Activity, launch: (Intent) -> Unit) {
    if (mutableBusy.value) return
    mutableBusy.value = true
    mutableSignInIssue.value = null
    try { launch(GoogleAccount.compatibilitySignInIntent(activity)) }
    catch (e: Exception) { reportSignInFailure(activity, e); mutableBusy.value = false }
  }

  fun finishCompatibilitySignIn(activity: Activity, data: Intent?) = viewModelScope.launch {
    try { GoogleAccount.finishCompatibilitySignIn(data); notify(R.string.signed_in) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { reportSignInFailure(activity, e) }
    finally { mutableBusy.value = false }
  }

  private fun reportSignInFailure(activity: Activity, error: Exception) {
    if (error is MissingFirebaseConfiguration) {
      mutableSignInIssue.value = activity.getString(R.string.firebase_not_configured)
      notify(R.string.firebase_not_configured)
      return
    }
    mutableSignInIssue.value = GoogleAccount.describeFailure(error)
    // Google and Play services sometimes report OAuth/configuration failures as a
    // cancellation even when the person never dismissed the chooser.
    if (error is CredentialFallbackCancelled || error is GetCredentialCancellationException ||
      (error is ApiException && error.statusCode == 12501)) {
      notify(R.string.sign_in_interrupted)
    } else {
      notify(R.string.sign_in_failed, error.javaClass.simpleName)
    }
  }
  fun signOut(activity: Activity) = viewModelScope.launch {
    mutableBusy.value = true
    try { GoogleAccount.signOut(activity); notify(R.string.signed_out) }
    catch (e: CancellationException) { throw e }
    catch (e: Exception) { notify(R.string.action_failed, safeError(e)) }
    finally { mutableBusy.value = false }
  }
  private fun cloud(): CloudBackup = CloudBackup(database, FirebaseFirestore.getInstance(getApplication<Application>().getString(R.string.firestore_database_id)))
  fun uploadBackup() = viewModelScope.launch {
    val uid = auth?.currentUser?.uid ?: run { notify(R.string.sign_in_first); return@launch }
    mutableBusy.value = true
    try { cloud().upload(uid); notify(R.string.backup_complete) }
    catch (e: CancellationException) { if (e is kotlinx.coroutines.TimeoutCancellationException) notify(R.string.backup_timeout) else throw e }
    catch (e: Exception) { notify(R.string.backup_failed, safeError(e)) }
    finally { mutableBusy.value = false }
  }
  fun restoreBackup() = viewModelScope.launch {
    val uid = auth?.currentUser?.uid ?: run { notify(R.string.sign_in_first); return@launch }
    mutableBusy.value = true
    try {
      val result = cloud().restore(uid)
      notify(R.string.restore_complete, result.tracksAdded, result.playlistsMerged, result.missingLocalTracks)
    } catch (e: CancellationException) { if (e is kotlinx.coroutines.TimeoutCancellationException) notify(R.string.restore_timeout) else throw e }
    catch (e: Exception) { notify(R.string.restore_failed, safeError(e)) }
    finally { mutableBusy.value = false }
  }

  private fun notify(@StringRes resource: Int, vararg args: Any) { mutableNotices.trySend(Notice(resource, args.toList())) }
  private fun safeError(error: Throwable): String = (error.cause?.localizedMessage ?: error.localizedMessage ?: error.javaClass.simpleName)
    .replace(Regex("[\\r\\n]+"), " ").take(240)

  override fun onCleared() {
    downloadJobs.keys.toList().forEach { downloads.cancel(it) }
    auth?.removeAuthStateListener(authListener)
    controller?.removeListener(playerListener)
    MediaController.releaseFuture(controllerFuture)
    mutableNotices.close()
    mutableOpenPlayerRequests.close()
    super.onCleared()
  }
}
