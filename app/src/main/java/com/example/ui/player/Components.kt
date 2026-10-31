package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.R
import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource

@Composable
fun PageHeader(eyebrow: String, title: String, subtitle: String) {
  Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
    Text(eyebrow.uppercase(), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold,
      color = MaterialTheme.colorScheme.primary, letterSpacing = androidx.compose.ui.unit.TextUnit.Unspecified)
    Text(title, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground)
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun SectionTitle(title: String, subtitle: String? = null) {
  Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
    Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
    if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
  }
}

@Composable
fun TrackArtwork(track: LibraryTrack?, size: Dp, modifier: Modifier = Modifier) {
  val colors = when (track?.source) {
    TrackSource.RADIO -> listOf(Color(0xFF5D6E55), Color(0xFF273E38))
    TrackSource.PREVIEW -> listOf(Color(0xFF8D7055), Color(0xFF3B4F43))
    TrackSource.DIRECT, TrackSource.CUSTOM -> listOf(Color(0xFF557B72), Color(0xFF293D3E))
    else -> listOf(Color(0xFF607C66), Color(0xFF293A31))
  }
  Box(modifier.size(size).clip(RoundedCornerShape(if (size > 100.dp) 24.dp else 12.dp))
    .background(Brush.linearGradient(colors)), contentAlignment = Alignment.Center) {
    Icon(Icons.Default.MusicNote, contentDescription = null, tint = Color(0xFFD7EAD1).copy(alpha = .8f), modifier = Modifier.size(size * .38f))
    track?.artworkUri?.takeIf { it.isNotBlank() }?.let { artwork ->
      AsyncImage(model = artwork, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
  }
}

@Composable
fun sourceLabel(track: LibraryTrack): String = stringResource(when (track.source) {
  TrackSource.LOCAL -> R.string.source_file
  TrackSource.RADIO -> R.string.source_radio
  TrackSource.PREVIEW -> R.string.source_preview
  TrackSource.CUSTOM -> R.string.source_custom
  else -> R.string.source_direct
})

@Composable
fun TrackRow(
  track: LibraryTrack,
  onPlay: () -> Unit,
  modifier: Modifier = Modifier,
  progress: DownloadProgress? = null,
  trailing: @Composable () -> Unit,
) {
  Row(
    modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).clickable(onClick = onPlay).padding(vertical = 9.dp),
    verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(13.dp),
  ) {
    TrackArtwork(track, 56.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
      val artist = track.artist.ifBlank { stringResource(R.string.unknown_artist) }
      val total = progress?.total
      val status = when {
        progress != null && total != null && total > 0 -> stringResource(R.string.downloading_percent, ((progress.bytes * 100 / total).coerceIn(0, 100)).toInt())
        progress != null -> stringResource(R.string.downloading_megabytes, (progress.bytes / (1024 * 1024)).toInt())
        track.downloadedPath != null -> stringResource(R.string.downloaded)
        else -> ""
      }
      val extra = if (status.isNotBlank()) " · $status" else ""
      Text("$artist · ${sourceLabel(track)}$extra", style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
    trailing()
  }
}

@Composable
fun LibraryTrackActions(
  track: LibraryTrack,
  playlists: List<LibraryPlaylist>,
  progress: DownloadProgress?,
  vm: MusicViewModel,
) {
  var menu by remember(track.id) { mutableStateOf(false) }
  var choosingPlaylist by remember(track.id) { mutableStateOf(false) }
  var confirmingRemoval by remember(track.id) { mutableStateOf(false) }
  Box {
    IconButton(onClick = { menu = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions)) }
    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
      DropdownMenuItem(
        text = { Text(stringResource(R.string.add_to_playlist)) },
        leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) },
        onClick = { menu = false; choosingPlaylist = true },
      )
      if (track.source == TrackSource.DIRECT || track.source == TrackSource.CUSTOM) {
        when {
          progress != null -> DropdownMenuItem(
            text = { Text(stringResource(R.string.cancel_download)) },
            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
            onClick = { menu = false; vm.cancelDownload(track.id) },
          )
          track.downloadedPath != null -> DropdownMenuItem(
            text = { Text(stringResource(R.string.remove_download)) },
            leadingIcon = { Icon(Icons.Default.RemoveCircleOutline, null) },
            onClick = { menu = false; vm.removeDownload(track) },
          )
          else -> DropdownMenuItem(
            text = { Text(stringResource(R.string.download_audio)) },
            leadingIcon = { Icon(Icons.Default.CloudDownload, null) },
            onClick = { menu = false; vm.download(track) },
          )
        }
      }
      DropdownMenuItem(
        text = { Text(stringResource(R.string.remove_from_library)) },
        leadingIcon = { Icon(Icons.Default.DeleteOutline, null) },
        onClick = { menu = false; confirmingRemoval = true },
      )
    }
  }
  if (choosingPlaylist) {
    AlertDialog(
      onDismissRequest = { choosingPlaylist = false },
      title = { Text(stringResource(R.string.choose_playlist)) },
      text = {
        Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
          if (playlists.isEmpty()) Text(stringResource(R.string.no_playlists_yet))
          playlists.forEach { playlist ->
            TextButton(onClick = { vm.addTrackToPlaylist(playlist, track); choosingPlaylist = false }, modifier = Modifier.fillMaxWidth()) {
              Text(playlist.name, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { choosingPlaylist = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
  if (confirmingRemoval) {
    AlertDialog(
      onDismissRequest = { confirmingRemoval = false }, title = { Text(stringResource(R.string.remove_track_title)) },
      text = { Text(stringResource(R.string.remove_track_body, track.title)) },
      confirmButton = { TextButton(onClick = { confirmingRemoval = false; vm.removeTrack(track) }) { Text(stringResource(R.string.remove)) } },
      dismissButton = { TextButton(onClick = { confirmingRemoval = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
fun AddTrackButton(onClick: () -> Unit, alreadyAdded: Boolean = false) {
  IconButton(onClick = onClick, enabled = !alreadyAdded) {
    Icon(if (alreadyAdded) Icons.Default.Check else Icons.Default.AddCircleOutline,
      contentDescription = stringResource(if (alreadyAdded) R.string.already_in_library else R.string.add_to_library),
      tint = if (alreadyAdded) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary)
  }
}

fun formatTime(milliseconds: Long): String {
  val seconds = (milliseconds.coerceAtLeast(0) / 1000)
  val hours = seconds / 3600
  return if (hours > 0) "%d:%02d:%02d".format(hours, (seconds % 3600) / 60, seconds % 60)
  else "%d:%02d".format(seconds / 60, seconds % 60)
}
