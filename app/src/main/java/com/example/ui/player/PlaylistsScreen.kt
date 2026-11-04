package com.example.ui.player

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryTrack

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistsScreen(vm: MusicViewModel) {
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  var selectedId by rememberSaveable { mutableStateOf<String?>(null) }
  var createDialog by rememberSaveable { mutableStateOf(false) }
  val selected = playlists.find { it.id == selectedId }
  BackHandler(enabled = selected != null) { selectedId = null }

  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    if (selected == null) {
      LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth(),
        contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 30.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)) {
        item { PageHeader(stringResource(R.string.playlists_eyebrow), stringResource(R.string.playlists_title), stringResource(R.string.playlists_subtitle)) }
        item {
          Button(onClick = { createDialog = true }, shapes = ButtonDefaults.shapes()) {
            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(R.string.new_playlist), modifier = Modifier.padding(start = 6.dp))
          }
        }
        if (playlists.isEmpty()) {
          item {
            Column(Modifier.fillMaxWidth().padding(vertical = 42.dp), horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp)) {
              Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(42.dp), tint = MaterialTheme.colorScheme.primary)
              Text(stringResource(R.string.empty_playlists_title), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
              Text(stringResource(R.string.empty_playlists_body), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
        items(playlists, key = { it.id }) { playlist ->
          Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.fillMaxWidth().clickable { selectedId = playlist.id }) {
            Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
              Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(14.dp), modifier = Modifier.size(58.dp)) {
                Box(contentAlignment = Alignment.Center) { Icon(Icons.AutoMirrored.Filled.QueueMusic, null, tint = MaterialTheme.colorScheme.primary) }
              }
              Column(Modifier.weight(1f)) {
                Text(playlist.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(stringResource(R.string.playlist_track_count, playlist.trackIds().size), color = MaterialTheme.colorScheme.onSurfaceVariant,
                  style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 4.dp))
              }
              Icon(Icons.Default.ChevronRight, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    } else {
      PlaylistDetail(selected, tracks, vm, onBack = { selectedId = null }, onDeleted = { selectedId = null })
    }
  }
  if (createDialog) NamePlaylistDialog(
    title = stringResource(R.string.new_playlist), initial = "", onDismiss = { createDialog = false },
    onConfirm = { vm.createPlaylist(it); createDialog = false },
  )
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlaylistDetail(playlist: LibraryPlaylist, allTracks: List<LibraryTrack>, vm: MusicViewModel, onBack: () -> Unit, onDeleted: () -> Unit) {
  var edit by remember(playlist.id) { mutableStateOf(false) }
  var rename by remember(playlist.id) { mutableStateOf(false) }
  var delete by remember(playlist.id) { mutableStateOf(false) }
  val byId = remember(allTracks) { allTracks.associateBy { it.id } }
  val available = playlist.trackIds().mapNotNull(byId::get)
  val missing = playlist.trackIds().size - available.size
  LazyColumn(Modifier.widthIn(max = 820.dp).fillMaxWidth(),
    contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 30.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp)) {
    item {
      Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
        Text(stringResource(R.string.playlists_title), color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        IconButton(onClick = { rename = true }) { Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.rename_playlist)) }
        IconButton(onClick = { delete = true }) { Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_playlist)) }
      }
    }
    item {
      PageHeader(stringResource(R.string.playlist_detail_eyebrow), playlist.name, stringResource(R.string.playlist_track_count, playlist.trackIds().size))
    }
    item {
      Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = { vm.playPlaylist(playlist) }, shapes = ButtonDefaults.shapes(), enabled = available.isNotEmpty()) {
          Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(20.dp))
          Text(stringResource(R.string.play_all), modifier = Modifier.padding(start = 5.dp))
        }
        OutlinedButton(onClick = { edit = true }) { Text(stringResource(R.string.edit_tracks)) }
      }
    }
    if (missing > 0) item {
      Column {
        Text(stringResource(R.string.missing_playlist_tracks, missing), color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.bodySmall)
        TextButton(onClick = { vm.setPlaylistTracks(playlist, playlist.trackIds().filter { it in byId }) }) {
          Text(stringResource(R.string.remove_unavailable_tracks))
        }
      }
    }
    if (available.isEmpty()) item {
      Text(stringResource(R.string.empty_playlist), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 24.dp))
    }
    items(available, key = { it.id }) { track ->
      TrackRow(track, onPlay = { vm.play(track, available) }) {
        IconButton(onClick = { vm.setPlaylistTracks(playlist, playlist.trackIds() - track.id) }) {
          Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.remove_from_playlist))
        }
      }
    }
  }
  if (edit) EditPlaylistDialog(playlist, allTracks, onDismiss = { edit = false }, onSave = { ids -> vm.setPlaylistTracks(playlist, ids); edit = false })
  if (rename) NamePlaylistDialog(stringResource(R.string.rename_playlist), playlist.name,
    onDismiss = { rename = false }, onConfirm = { vm.renamePlaylist(playlist, it); rename = false })
  if (delete) AlertDialog(
    onDismissRequest = { delete = false }, title = { Text(stringResource(R.string.delete_playlist)) },
    text = { Text(stringResource(R.string.delete_playlist_body, playlist.name)) },
    confirmButton = { TextButton(onClick = { vm.deletePlaylist(playlist); delete = false; onDeleted() }) { Text(stringResource(R.string.delete)) } },
    dismissButton = { TextButton(onClick = { delete = false }) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun NamePlaylistDialog(title: String, initial: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
  var name by remember { mutableStateOf(initial) }
  AlertDialog(
    onDismissRequest = onDismiss, title = { Text(title) },
    text = { OutlinedTextField(value = name, onValueChange = { name = it.take(80) }, label = { Text(stringResource(R.string.playlist_name)) }, singleLine = true) },
    confirmButton = { TextButton(onClick = { onConfirm(name.trim()) }, enabled = name.isNotBlank()) { Text(stringResource(R.string.save)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}

@Composable
private fun EditPlaylistDialog(playlist: LibraryPlaylist, tracks: List<LibraryTrack>, onDismiss: () -> Unit, onSave: (List<String>) -> Unit) {
  var selected by remember(playlist.id) { mutableStateOf(playlist.trackIds()) }
  AlertDialog(
    onDismissRequest = onDismiss, title = { Text(stringResource(R.string.edit_tracks)) },
    text = {
      if (tracks.isEmpty()) Text(stringResource(R.string.no_library_tracks)) else LazyColumn(Modifier.fillMaxWidth().heightIn(max = 380.dp)) {
        items(tracks, key = { it.id }) { track ->
          Row(Modifier.fillMaxWidth().clickable {
            selected = if (track.id in selected) selected - track.id else selected + track.id
          }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = track.id in selected, onCheckedChange = { checked -> selected = if (checked) (selected + track.id).distinct() else selected - track.id })
            Column(Modifier.weight(1f).padding(start = 8.dp)) {
              Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, maxLines = 1, overflow = TextOverflow.Ellipsis)
              Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = { onSave(selected) }) { Text(stringResource(R.string.save)) } },
    dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) } },
  )
}
