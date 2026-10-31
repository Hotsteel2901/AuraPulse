package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.data.model.OfflineTrackEntity
import com.example.data.model.Playlist
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.UiState

@Composable
fun PlaylistsScreen(
  playlistsState: UiState<List<Playlist>>,
  allTracks: List<OfflineTrackEntity>,
  selectedPlaylistId: String?,
  onSelectPlaylist: (String?) -> Unit,
  onCreatePlaylist: (String, String, String, String, Boolean, List<String>) -> Unit,
  onToggleTrackInPlaylist: (Playlist, String) -> Unit,
  onTogglePlaylistOfflinePin: (Playlist) -> Unit,
  onDeletePlaylist: (String) -> Unit,
  onPlayTrack: (OfflineTrackEntity) -> Unit,
  onBackToDeck: () -> Unit,
) {
  val strings = LocalAuraStrings.current
  var showCreateDialog by remember { mutableStateOf(false) }

  val playlists = (playlistsState as? UiState.Success)?.data ?: emptyList()
  val activePlaylist = playlists.find { it.id == selectedPlaylistId }

  if (activePlaylist != null) {
    BackHandler { onSelectPlaylist(null) }
    PlaylistDetailView(
      playlist = activePlaylist,
      allTracks = allTracks,
      onBack = { onSelectPlaylist(null) },
      onToggleTrack = { trackId -> onToggleTrackInPlaylist(activePlaylist, trackId) },
      onToggleOfflinePin = { onTogglePlaylistOfflinePin(activePlaylist) },
      onDelete = { onDeletePlaylist(activePlaylist.id) },
      onPlayTrack = onPlayTrack,
    )
    return
  } else {
    BackHandler { onBackToDeck() }
  }

  Column(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Column(modifier = Modifier.weight(1f)) {
        Text(
          text = strings.playlistsTitle,
          style = MaterialTheme.typography.headlineMedium,
        )
        Text(
          text = strings.playlistsSubtitle,
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }

      Button(
        onClick = { showCreateDialog = true },
        modifier = Modifier.testTag("create_playlist_button"),
        shape = MaterialTheme.shapes.medium,
      ) {
        Icon(Icons.Filled.Add, contentDescription = null)
        Spacer(modifier = Modifier.width(6.dp))
        Text(strings.newMixButton)
      }
    }

    when (playlistsState) {
      is UiState.Loading -> {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
          CircularProgressIndicator()
        }
      }
      is UiState.Error -> {
        Card(
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
          modifier = Modifier.fillMaxWidth(),
        ) {
          Text(
            text = playlistsState.message,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(16.dp),
          )
        }
      }
      is UiState.Success -> {
        if (playlists.isEmpty()) {
          Card(
            modifier = Modifier.fillMaxWidth(),
            colors =
              CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
          ) {
            Column(
              modifier = Modifier.fillMaxWidth().padding(28.dp),
              horizontalAlignment = Alignment.CenterHorizontally,
              verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Icon(
                Icons.AutoMirrored.Filled.QueueMusic,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(42.dp),
              )
              Text(
                strings.emptyPlaylistsTitle,
                style = MaterialTheme.typography.titleLarge,
              )
              Text(
                strings.emptyPlaylistsSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        } else {
          LazyColumn(
            modifier = Modifier.fillMaxSize().testTag("playlists_lazy_column"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
          ) {
            items(playlists, key = { it.id }) { playlist ->
              val accent = parseHexColor(playlist.accentHex, MaterialTheme.colorScheme.primary)
              Card(
                modifier =
                  Modifier.fillMaxWidth()
                    .clickable { onSelectPlaylist(playlist.id) }
                    .testTag("playlist_card_${playlist.id}"),
                shape = MaterialTheme.shapes.large,
                colors =
                  CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
              ) {
                Box(
                  modifier =
                    Modifier.fillMaxWidth()
                      .background(
                        Brush.horizontalGradient(
                          listOf(accent.copy(alpha = 0.25f), Color.Transparent)
                        )
                      )
                      .padding(18.dp)
                ) {
                  Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                  ) {
                    Column(
                      modifier = Modifier.weight(1f),
                      verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                      Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                      ) {
                        ProviderBadge(playlist.sourceService)
                        if (playlist.isOfflinePinned) {
                          Surface(
                            color = accent.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(6.dp),
                          ) {
                            Text(
                              text = "OFFLINE PINNED",
                              style = MaterialTheme.typography.labelSmall,
                              color = accent,
                              modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                          }
                        }
                      }
                      Text(
                        text = playlist.title,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface,
                      )
                      if (playlist.description.isNotBlank()) {
                        Text(
                          text = playlist.description,
                          style = MaterialTheme.typography.bodyMedium,
                          color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      Text(
                        text = "${playlist.trackIds.size} tracks synced across devices",
                        style = MaterialTheme.typography.labelLarge,
                        color = accent,
                      )
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                      IconButton(
                        onClick = { onTogglePlaylistOfflinePin(playlist) },
                        modifier = Modifier.testTag("pin_playlist_${playlist.id}"),
                      ) {
                        Icon(
                          imageVector =
                            if (playlist.isOfflinePinned) Icons.Filled.DownloadForOffline
                            else Icons.Outlined.Download,
                          contentDescription = "Pin playlist offline",
                          tint =
                            if (playlist.isOfflinePinned) accent
                            else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                      }
                      IconButton(
                        onClick = { onDeletePlaylist(playlist.id) },
                        modifier = Modifier.testTag("delete_playlist_${playlist.id}"),
                      ) {
                        Icon(
                          imageVector = Icons.Filled.DeleteOutline,
                          contentDescription = "Delete playlist",
                          tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
  }

  if (showCreateDialog) {
    CreatePlaylistDialog(
      allTracks = allTracks,
      onDismiss = { showCreateDialog = false },
      onCreate = { title, desc, hex, service, pinned, trackIds ->
        onCreatePlaylist(title, desc, hex, service, pinned, trackIds)
        showCreateDialog = false
      },
    )
  }
}

@Composable
private fun PlaylistDetailView(
  playlist: Playlist,
  allTracks: List<OfflineTrackEntity>,
  onBack: () -> Unit,
  onToggleTrack: (String) -> Unit,
  onToggleOfflinePin: () -> Unit,
  onDelete: () -> Unit,
  onPlayTrack: (OfflineTrackEntity) -> Unit,
) {
  val accent = parseHexColor(playlist.accentHex, MaterialTheme.colorScheme.primary)
  val inPlaylistTracks = allTracks.filter { playlist.trackIds.contains(it.trackId) }

  LazyColumn(
    modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = accent.copy(alpha = 0.18f)),
      ) {
        Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            IconButton(
              onClick = onBack,
              modifier = Modifier.testTag("playlist_detail_back_button"),
            ) {
              Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Playlists")
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
              FilledTonalButton(
                onClick = onToggleOfflinePin,
                modifier = Modifier.testTag("detail_offline_pin_button"),
              ) {
                Icon(
                  if (playlist.isOfflinePinned) Icons.Filled.DownloadForOffline
                  else Icons.Outlined.Download,
                  contentDescription = null,
                  modifier = Modifier.size(18.dp),
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (playlist.isOfflinePinned) "Pinned Offline" else "Pin Offline")
              }
              IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Playlist")
              }
            }
          }

          Text(text = playlist.title, style = MaterialTheme.typography.headlineLarge)
          if (playlist.description.isNotBlank()) {
            Text(
              text = playlist.description,
              style = MaterialTheme.typography.bodyLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          if (inPlaylistTracks.isNotEmpty()) {
            Button(
              onClick = { onPlayTrack(inPlaylistTracks.first()) },
              modifier = Modifier.testTag("play_playlist_first_button"),
            ) {
              Icon(Icons.Filled.PlayArrow, contentDescription = null)
              Spacer(modifier = Modifier.width(6.dp))
              Text("Play Lossless Queue (${inPlaylistTracks.size} Tracks)")
            }
          }
        }
      }
    }

    item {
      Text(
        text = "Curate Tracks in '${playlist.title}'",
        style = MaterialTheme.typography.titleMedium,
        color = accent,
      )
    }

    items(allTracks, key = { it.trackId }) { track ->
      val included = playlist.trackIds.contains(track.trackId)
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .clickable {
              if (included) onPlayTrack(track) else onToggleTrack(track.trackId)
            }
            .testTag("playlist_track_toggle_${track.trackId}"),
        colors =
          CardDefaults.cardColors(
            containerColor =
              if (included) accent.copy(alpha = 0.14f)
              else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(12.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = track.title, style = MaterialTheme.typography.titleMedium)
            Text(
              text = "${track.artist} • ${track.codec}",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          Row(verticalAlignment = Alignment.CenterVertically) {
            if (included) {
              IconButton(onClick = { onPlayTrack(track) }) {
                Icon(Icons.Filled.PlayArrow, contentDescription = "Play track", tint = accent)
              }
            }
            Checkbox(
              checked = included,
              onCheckedChange = { onToggleTrack(track.trackId) },
            )
          }
        }
      }
    }
  }
}

@Composable
private fun CreatePlaylistDialog(
  allTracks: List<OfflineTrackEntity>,
  onDismiss: () -> Unit,
  onCreate: (String, String, String, String, Boolean, List<String>) -> Unit,
) {
  BackHandler { onDismiss() }
  var title by remember { mutableStateOf("") }
  var description by remember { mutableStateOf("") }
  var selectedHex by remember { mutableStateOf("#00F5D4") }
  var selectedService by remember { mutableStateOf("HYBRID") }
  var offlinePinned by remember { mutableStateOf(true) }
  val selectedTrackIds = remember {
    mutableStateListOf<String>().apply {
      allTracks.take(2).forEach { add(it.trackId) }
    }
  }

  val paletteOptions =
    listOf("#00F5D4", "#FF9F1C", "#F72585", "#4CC9F0", "#9B5DE5", "#38B000")
  val serviceOptions = listOf("AURA_HIFI", "CUSTOM_API", "SUBSONIC", "OPEN_GLOBAL", "HYBRID")

  AlertDialog(
    onDismissRequest = onDismiss,
    title = { Text("Create Expressive Cloud Playlist") },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = title,
          onValueChange = { title = it },
          label = { Text("Playlist Title") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_playlist_title_input"),
        )
        OutlinedTextField(
          value = description,
          onValueChange = { description = it },
          label = { Text("Sonic Mood / Description") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("new_playlist_desc_input"),
        )

        Text("Dynamic Theme Accent", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
          items(paletteOptions) { hex ->
            val color = parseHexColor(hex)
            Box(
              modifier =
                Modifier.size(36.dp)
                  .clip(CircleShape)
                  .background(color)
                  .clickable { selectedHex = hex },
              contentAlignment = Alignment.Center,
            ) {
              if (selectedHex == hex) {
                Icon(
                  Icons.Filled.Check,
                  contentDescription = "Selected Accent",
                  tint = Color.Black,
                  modifier = Modifier.size(18.dp),
                )
              }
            }
          }
        }

        Text("Streaming Bridge Source", style = MaterialTheme.typography.labelLarge)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
          items(serviceOptions) { srv ->
            FilterChip(
              selected = selectedService == srv,
              onClick = { selectedService = srv },
              label = { Text(srv) },
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text("Pin Lossless Copy Offline", style = MaterialTheme.typography.bodyMedium)
          Switch(checked = offlinePinned, onCheckedChange = { offlinePinned = it })
        }
      }
    },
    confirmButton = {
      Button(
        onClick = {
          onCreate(
            title.ifBlank { "Audiophile Session" },
            description.ifBlank { "24-Bit Lossless Custom Mix" },
            selectedHex,
            selectedService,
            offlinePinned,
            selectedTrackIds.toList(),
          )
        },
        modifier = Modifier.testTag("confirm_create_playlist_button"),
      ) {
        Text("Create & Sync")
      }
    },
    dismissButton = {
      TextButton(onClick = onDismiss) { Text("Cancel") }
    },
  )
}
