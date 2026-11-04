package com.hotsteel.aurapulse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Album
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.Sort
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlin.math.absoluteValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.LibraryFilter
import com.hotsteel.aurapulse.data.TrackSort
import com.hotsteel.aurapulse.data.library.PlaylistEntity
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.Route
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.AuraBackdrop
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.MixCard
import com.hotsteel.aurapulse.ui.components.PageHeader
import com.hotsteel.aurapulse.ui.components.SectionHeader
import com.hotsteel.aurapulse.ui.components.TrackRow
import com.hotsteel.aurapulse.ui.theme.hueGradient
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts

private enum class LibraryTab { Albums, Artists, Tracks }

data class AlbumGroup(val key: String, val title: String, val artist: String, val tracks: List<TrackEntity>)
data class ArtistGroup(val name: String, val tracks: List<TrackEntity>)

fun albumGroups(tracks: List<TrackEntity>): List<AlbumGroup> =
  tracks.filter { it.album.isNotBlank() || it.artist.isNotBlank() }
    .groupBy { (it.albumArtist.ifBlank { it.artist }).ifBlank { "—" } + "\u0001" + it.album.ifBlank { "Singles" } }
    .map { (key, list) ->
      AlbumGroup(
        key = key,
        title = list.first().album.ifBlank { "Singles" },
        artist = (list.first().albumArtist.ifBlank { list.first().artist }).ifBlank { "Unknown artist" },
        tracks = list.sortedBy { it.trackNumber },
      )
    }
    .sortedBy { it.title.lowercase() }

fun artistGroups(tracks: List<TrackEntity>): List<ArtistGroup> =
  tracks.groupBy { it.artist.ifBlank { "Unknown artist" } }
    .map { (name, list) -> ArtistGroup(name, list) }
    .sortedBy { it.name.lowercase() }

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(
  vm: AuraViewModel,
  onOpenSearch: () -> Unit,
  onOpenAllTracks: () -> Unit,
  onOpenFavorites: () -> Unit,
  onOpenPlaylists: () -> Unit,
  onOpenPlaylist: (String) -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenArtist: (String) -> Unit,
  onOpenDownloads: () -> Unit,
) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val stats by vm.stats.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  val importing by vm.importing.collectAsStateWithLifecycle()
  val selection by vm.selection.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()

  var tab by remember { mutableStateOf(LibraryTab.Tracks) }
  var importMenu by remember { mutableStateOf(false) }
  var sortMenu by remember { mutableStateOf(false) }
  var filterMenu by remember { mutableStateOf(false) }
  var confirmDelete by remember { mutableStateOf(false) }

  val context = LocalContext.current
  val filePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
    vm.importUris(uris)
  }
  val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
    uri?.let(vm::importTree)
  }
  val listState = rememberLazyListState()
  val selectionMode = selection.isNotEmpty()
  val visible = vm.sortedTracks(tracks, settings.librarySort, favorites, stats, settings.libraryFilter)
  val albums = remember(tracks) { albumGroups(tracks) }
  val artists = remember(tracks) { artistGroups(tracks) }
  val totalMs = remember(tracks) { tracks.sumOf { it.durationMs } }
  val recent = remember(tracks) { tracks.sortedByDescending { it.addedAt }.take(12) }

  Scaffold(containerColor = Color.Transparent, floatingActionButton = {
    if (!selectionMode) {
      Box {
        ExtendedFloatingActionButton(
          onClick = { importMenu = true },
          icon = { Icon(Icons.Default.Add, contentDescription = null) },
          text = { Text(stringResource(R.string.library_import)) },
        )
        DropdownMenu(expanded = importMenu, onDismissRequest = { importMenu = false }) {
          DropdownMenuItem(
            text = { Text(stringResource(R.string.library_import_files)) },
            leadingIcon = { Icon(Icons.Default.UploadFile, null) },
            onClick = { importMenu = false; filePicker.launch(arrayOf("audio/*", "application/ogg")) },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.library_import_folder)) },
            leadingIcon = { Icon(Icons.Default.Folder, null) },
            onClick = { importMenu = false; folderPicker.launch(null) },
          )
          DropdownMenuItem(
            text = { Text(stringResource(R.string.library_import_url)) },
            leadingIcon = { Icon(Icons.Default.Link, null) },
            onClick = { importMenu = false; onOpenSearch() },
          )
        }
      }
    }
  }) { padding ->
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxSize(),
      contentPadding = PaddingValues(
        top = padding.calculateTopPadding() + 8.dp,
        bottom = padding.calculateBottomPadding() + 96.dp,
      ),
    ) {
      item("header") {
        PageHeader(
          eyebrow = stringResource(R.string.app_name),
          title = stringResource(R.string.nav_library),
          subtitle = stringResource(R.string.library_subtitle),
          trailing = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
              FilledTonalIconButton(onClick = onOpenSearch, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Default.Search, contentDescription = stringResource(R.string.search_title))
              }
              FilledTonalIconButton(onClick = onOpenDownloads, modifier = Modifier.size(46.dp)) {
                Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.downloads_title))
              }
            }
          },
        )
      }

      item("stats") {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
          StatChip(stringResource(R.string.library_tracks_count, tracks.size))
          StatChip(stringResource(R.string.library_albums_count, albums.size))
          StatChip(stringResource(R.string.library_hours, fmt.hours(totalMs)))
        }
      }

      if (tracks.isEmpty()) {
        item("empty") {
          EmptyState(
            title = stringResource(R.string.empty_library_title),
            body = stringResource(R.string.empty_library_body),
            action = {
              Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                ExtendedFloatingActionButton(
                  onClick = { filePicker.launch(arrayOf("audio/*", "application/ogg")) },
                  icon = { Icon(Icons.Default.Add, null) },
                  text = { Text(stringResource(R.string.empty_library_cta)) },
                )
                FilledTonalIconButton(onClick = { folderPicker.launch(null) }, modifier = Modifier.size(56.dp)) {
                  Icon(Icons.Default.Folder, contentDescription = stringResource(R.string.library_import_folder))
                }
              }
            },
          )
        }
      } else {
        item("mixes") {
          SectionHeader(stringResource(R.string.section_mixes))
          val mixes = listOf(
            Triple(stringResource(R.string.mix_recent), stringResource(R.string.mix_recent_desc), hueGradient(142f)),
            Triple(stringResource(R.string.mix_heavy), stringResource(R.string.mix_heavy_desc), hueGradient(28f)),
            Triple(stringResource(R.string.mix_forgotten), stringResource(R.string.mix_forgotten_desc), hueGradient(196f)),
            Triple(stringResource(R.string.mix_long), stringResource(R.string.mix_long_desc), hueGradient(300f)),
          )
          LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
          ) {
            items(mixes.size) { index ->
              val (title, subtitle, colors) = mixes[index]
              MixCard(title, subtitle, colors, onClick = {
                val list = when (index) {
                  0 -> tracks.sortedByDescending { it.addedAt }.take(30)
                  1 -> tracks.sortedByDescending { stats[it.id]?.plays ?: 0 }.filter { (stats[it.id]?.plays ?: 0) > 0 }.take(30)
                     .ifEmpty { tracks.sortedByDescending { stats[it.id]?.plays ?: 0 }.take(30) }
                  2 -> tracks.filter { track ->
                    val last = stats[track.id]?.lastPlayedAt ?: 0
                    last == 0L || last < System.currentTimeMillis() - 21L * 24 * 3600 * 1000
                  }.take(40)
                  else -> tracks.filter { it.durationMs > 6 * 60_000 }.take(40)
                }
                if (list.isNotEmpty()) vm.play(list.first(), list) else vm.play(tracks.first(), tracks)
              })
            }
          }
        }

        if (recent.isNotEmpty()) {
          item("recent") {
            SectionHeader(
              stringResource(R.string.section_recent),
              action = { TextButton(onClick = onOpenAllTracks) { Text(stringResource(R.string.see_all)) } },
            )
            LazyRow(
              contentPadding = PaddingValues(horizontal = 20.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              items(recent, key = { it.id }) { track ->
                Column(Modifier.width(132.dp).clickable { vm.play(track, recent) }) {
                  Artwork(track, 132.dp, corner = 20.dp)
                  Spacer(Modifier.height(8.dp))
                  Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    track.artist.ifBlank { "Unknown artist" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                  )
                }
              }
            }
          }
        }

        item("tabs") {
          Spacer(Modifier.height(14.dp))
          LibrarySegments(tab) { tab = it }
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Box {
              IconButton(onClick = { sortMenu = true }) {
                Icon(Icons.Default.Sort, contentDescription = stringResource(R.string.sort))
              }
              DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                TrackSort.entries.forEach { sort ->
                  DropdownMenuItem(
                    text = { Text(sortLabel(sort)) },
                    onClick = { sortMenu = false; vm.updateSettings { s -> s.copy(librarySort = sort) } },
                  )
                }
              }
            }
            Box {
              IconButton(onClick = { filterMenu = true }) {
                Icon(Icons.Default.List, contentDescription = stringResource(R.string.filter_all))
              }
              DropdownMenu(expanded = filterMenu, onDismissRequest = { filterMenu = false }) {
                LibraryFilter.entries.forEach { filter ->
                  DropdownMenuItem(
                    text = { Text(filterLabel(filter)) },
                    onClick = { filterMenu = false; vm.updateSettings { s -> s.copy(libraryFilter = filter) } },
                  )
                }
              }
            }
            Spacer(Modifier.weight(1f))
            Text(
              when (tab) {
                LibraryTab.Albums -> stringResource(R.string.library_albums_count, albums.size)
                LibraryTab.Artists -> stringResource(R.string.section_artists) + " · " + artists.size
                LibraryTab.Tracks -> stringResource(R.string.library_tracks_count, visible.size)
              },
              style = MaterialTheme.typography.labelMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        when (tab) {
          LibraryTab.Albums -> {
            items(albums.chunked(2), key = { row -> "albumrow_" + row.first().key }) { row ->
              Row(
                Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                row.forEach { album ->
                  Column(Modifier.weight(1f).clickable { onOpenAlbum(album.key) }) {
                    Box {
                      Artwork(album.tracks.firstOrNull(), 158.dp, Modifier.fillMaxWidth().height(158.dp), corner = 22.dp, fillWidth = true)
                      Box(
                        Modifier.fillMaxWidth().height(158.dp).clip(MaterialTheme.shapes.large)
                          .background(hueGradient((album.key.hashCode().absoluteValue % 360).toFloat()).first().copy(alpha = 0.18f)),
                      )
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                      "${album.artist} · ${album.tracks.size}",
                      style = MaterialTheme.typography.bodySmall,
                      color = MaterialTheme.colorScheme.onSurfaceVariant,
                      maxLines = 1,
                      overflow = TextOverflow.Ellipsis,
                    )
                  }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
              }
            }
          }

          LibraryTab.Artists -> {
            items(artists, key = { it.name }) { artist ->
              Row(
                Modifier.fillMaxWidth().clickable { onOpenArtist(artist.name) }
                  .padding(horizontal = 20.dp, vertical = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
              ) {
                Box(Modifier.size(54.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                  contentAlignment = Alignment.Center) {
                  Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                }
                Column(Modifier.weight(1f)) {
                  Text(artist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                  Text(
                    stringResource(R.string.library_tracks_count, artist.tracks.size) + " · " +
                      stringResource(R.string.library_albums_count, artist.tracks.map { it.album }.distinct().size),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
              }
            }
          }

          LibraryTab.Tracks -> {
            if (visible.isEmpty()) {
              item("nofilter") {
                EmptyState(
                  title = stringResource(R.string.search_no_results, filterLabel(settings.libraryFilter)),
                  body = stringResource(R.string.empty_library_body),
                )
              }
            }
            items(visible, key = { it.id }) { track ->
              TrackRow(
                track = track,
                vm = vm,
                playing = playback.track?.id == track.id,
                progress = downloads[track.id],
                selected = track.id in selection,
                selectionMode = selectionMode,
                onClick = {
                  if (selectionMode) vm.toggleSelection(track.id) else vm.play(track, visible)
                },
                onLongClick = { vm.startSelection(track.id) },
              )
            }
          }
        }
      }
    }
  }

  AnimatedVisibility(
    visible = selectionMode,
    enter = slideInVertically { -it } + fadeIn(),
    exit = slideOutVertically { -it } + fadeOut(),
  ) {
    Surface(
      color = MaterialTheme.colorScheme.surfaceContainerHigh,
      shape = MaterialTheme.shapes.large,
      modifier = Modifier.statusBarsPadding().padding(12.dp).fillMaxWidth(),
    ) {
      Row(Modifier.padding(horizontal = 14.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
          stringResource(R.string.selected_count, selection.size),
          style = MaterialTheme.typography.titleSmall,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = { vm.selectAll(visible.map { it.id }) }) {
          Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.select_all))
        }
        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.delete)) }
        TextButton(onClick = { vm.clearSelection() }) { Text(stringResource(R.string.clear_selection)) }
      }
    }
  }

  if (confirmDelete) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { confirmDelete = false },
      title = { Text(stringResource(R.string.delete_tracks_title, selection.size)) },
      text = { Text(stringResource(R.string.delete_tracks_body)) },
      confirmButton = {
        TextButton(onClick = { confirmDelete = false; vm.deleteTracks(selection) }) { Text(stringResource(R.string.remove)) }
      },
      dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun StatChip(text: String) {
  Surface(color = MaterialTheme.colorScheme.surfaceContainer, shape = CircleShape) {
    Text(
      text,
      modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun LibrarySegments(selected: LibraryTab, onSelect: (LibraryTab) -> Unit) {
  val options = listOf(LibraryTab.Albums, LibraryTab.Artists, LibraryTab.Tracks)
  ButtonGroup(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menu) },
  ) {
    options.forEach { option ->
      customItem(
        buttonGroupContent = {
          ToggleButton(
            checked = selected == option,
            onCheckedChange = { onSelect(option) },
            shapes = ToggleButtonDefaults.shapes(),
            modifier = Modifier.height(42.dp),
          ) {
            Text(
              when (option) {
                LibraryTab.Albums -> stringResource(R.string.section_albums)
                LibraryTab.Artists -> stringResource(R.string.section_artists)
                LibraryTab.Tracks -> stringResource(R.string.section_tracks)
              },
              style = MaterialTheme.typography.labelLarge,
            )
          }
        },
        menuContent = { menu ->
          DropdownMenuItem(
            text = {
              Text(
                when (option) {
                  LibraryTab.Albums -> stringResource(R.string.section_albums)
                  LibraryTab.Artists -> stringResource(R.string.section_artists)
                  LibraryTab.Tracks -> stringResource(R.string.section_tracks)
                },
              )
            },
            onClick = { menu.dismiss(); onSelect(option) },
          )
        },
      )
    }
  }
}

@Composable
private fun sortLabel(sort: TrackSort): String = stringResource(
  when (sort) {
    TrackSort.ADDED -> R.string.sort_added
    TrackSort.TITLE -> R.string.sort_title
    TrackSort.ARTIST -> R.string.sort_artist
    TrackSort.DURATION -> R.string.sort_duration
    TrackSort.PLAYS -> R.string.sort_plays
  },
)

@Composable
private fun filterLabel(filter: LibraryFilter): String = stringResource(
  when (filter) {
    LibraryFilter.ALL -> R.string.filter_all
    LibraryFilter.FAVORITES -> R.string.filter_favorites
    LibraryFilter.DOWNLOADED -> R.string.filter_downloaded
  },
)
