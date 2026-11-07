package com.hotsteel.aurapulse.ui.screens

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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.data.library.PlaylistEntity
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.PlaylistCard
import com.hotsteel.aurapulse.ui.components.TrackRow

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistsScreen(vm: AuraViewModel, onBack: () -> Unit, onOpen: (String) -> Unit) {
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  var creating by remember { mutableStateOf(false) }
  var name by remember { mutableStateOf("") }

  Box(Modifier.fillMaxSize()) {
    Column(Modifier.fillMaxSize()) {
      DetailTopBar(stringResource(R.string.playlists_title), onBack)
      if (playlists.isEmpty()) {
        EmptyState(
          title = stringResource(R.string.playlists_title),
          body = stringResource(R.string.no_playlists_yet),
          action = {
            ExtendedFloatingActionButton(
              onClick = { creating = true },
              icon = { Icon(Icons.Default.Add, null) },
              text = { Text(stringResource(R.string.playlist_new)) },
            )
          },
        )
      } else {
        LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
          items(playlists.chunked(2), key = { it.first().id }) { row ->
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
              horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
              row.forEach { playlist ->
                PlaylistCard(
                  playlist = playlist,
                  trackCount = playlist.trackIds().size,
                  onClick = { onOpen(playlist.id) },
                  modifier = Modifier.weight(1f),
                )
              }
              if (row.size == 1) Spacer(Modifier.weight(1f))
            }
          }
        }
      }
    }
    ExtendedFloatingActionButton(
      onClick = { creating = true },
      icon = { Icon(Icons.Default.Add, null) },
      text = { Text(stringResource(R.string.playlist_new)) },
      modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
    )
  }

  if (creating) {
    AlertDialog(
      onDismissRequest = { creating = false },
      title = { Text(stringResource(R.string.playlist_new)) },
      text = {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it },
          label = { Text(stringResource(R.string.playlist_name)) },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(onClick = { vm.createPlaylist(name); name = ""; creating = false }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = { creating = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun PlaylistDetailScreen(
  vm: AuraViewModel,
  playlistId: String,
  onBack: () -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenArtist: (String) -> Unit,
) {
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  val playlist = remember(playlists, playlistId) { playlists.firstOrNull { it.id == playlistId } }
  val byId = remember(tracks) { tracks.associateBy { it.id } }
  val items = remember(playlist, tracks) { playlist?.trackIds()?.mapNotNull(byId::get).orEmpty() }

  var menu by remember { mutableStateOf(false) }
  var renaming by remember { mutableStateOf(false) }
  var deleting by remember { mutableStateOf(false) }
  var newName by remember { mutableStateOf("") }
  var publishing by remember { mutableStateOf(false) }

  if (playlist == null) {
    Column(Modifier.fillMaxSize()) {
      DetailTopBar(stringResource(R.string.playlists_title), onBack)
      EmptyState(title = stringResource(R.string.playlists_title), body = stringResource(R.string.no_playlists_yet))
    }
    return
  }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(
      title = playlist.name,
      onBack = onBack,
      trailing = {
        IconButton(onClick = { menu = true }) {
          Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
        }
        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
          DropdownMenuItem(text = { Text(stringResource(R.string.rename)) }, onClick = {
            newName = playlist.name; renaming = true; menu = false
          })
          DropdownMenuItem(text = { Text(stringResource(R.string.playlist_duplicate)) }, onClick = {
            vm.duplicatePlaylist(playlist.id); menu = false
          })
          DropdownMenuItem(text = { Text(stringResource(R.string.playlist_publish)) }, onClick = {
            publishing = true; menu = false
          })
          DropdownMenuItem(text = { Text(stringResource(R.string.delete)) }, onClick = {
            deleting = true; menu = false
          })
        }
      },
    )
    if (items.isEmpty()) {
      EmptyState(
        title = playlist.name,
        body = stringResource(R.string.playlist_empty),
        action = {
          TextButton(onClick = onBack) { Text(stringResource(R.string.add_to_playlist)) }
        },
      )
    } else {
      LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
        item("header") {
          CollectionHeader(
            title = playlist.name,
            subtitle = stringResource(R.string.playlist_track_count, items.size),
            tracks = items,
            onPlay = { vm.play(items.first(), items) },
            onShuffle = { vm.playShuffled(items) },
          )
        }
        items(items, key = { it.id }) { track ->
          TrackRow(
            track = track,
            vm = vm,
            playing = playback.track?.id == track.id,
            progress = downloads[track.id],
            onClick = { vm.play(track, items) },
            trailing = {
              IconButton(onClick = {
                vm.removeFromPlaylist(playlist.id, track.id)
              }) { Icon(Icons.Default.RemoveCircleOutline, contentDescription = stringResource(R.string.remove_from_playlist), modifier = Modifier.size(20.dp)) }
            },
          )
        }
      }
    }
  }

  if (renaming) {
    AlertDialog(
      onDismissRequest = { renaming = false },
      title = { Text(stringResource(R.string.rename)) },
      text = {
        OutlinedTextField(
          value = newName,
          onValueChange = { newName = it },
          label = { Text(stringResource(R.string.playlist_name)) },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(onClick = { vm.renamePlaylist(playlist.id, newName); renaming = false }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = { renaming = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
  if (deleting) {
    AlertDialog(
      onDismissRequest = { deleting = false },
      title = { Text(stringResource(R.string.playlist_delete_title, playlist.name)) },
      text = { Text(stringResource(R.string.playlist_delete_body)) },
      confirmButton = {
        TextButton(onClick = { vm.deletePlaylist(playlist.id); deleting = false; onBack() }) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = { TextButton(onClick = { deleting = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
  if (publishing) {
    AlertDialog(
      onDismissRequest = { publishing = false },
      title = { Text(stringResource(R.string.playlist_publish)) },
      text = { Text("Creates a secret gist containing this playlist as .m3u. Sign-in required.") },
      confirmButton = {
        TextButton(onClick = { vm.publishPlaylist(playlist, false); publishing = false }) { Text(stringResource(R.string.sync_push)) }
      },
      dismissButton = { TextButton(onClick = { publishing = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}
