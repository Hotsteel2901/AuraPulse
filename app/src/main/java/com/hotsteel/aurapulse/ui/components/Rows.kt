package com.hotsteel.aurapulse.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RemoveCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.SafeHaptics
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.library.PlaylistEntity
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.DownloadProgress

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalFoundationApi::class)
@Composable
fun TrackRow(
  track: TrackEntity,
  vm: AuraViewModel,
  playing: Boolean,
  modifier: Modifier = Modifier,
  showArtwork: Boolean = true,
  progress: DownloadProgress? = null,
  selected: Boolean = false,
  selectionMode: Boolean = false,
  onClick: () -> Unit,
  onLongClick: (() -> Unit)? = null,
  trailing: (@Composable () -> Unit)? = null,
) {
  val colors = track.artworkColors()
  val container by animateColorAsState(
    targetValue = when {
      selected -> MaterialTheme.colorScheme.secondaryContainer
      playing -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
      else -> Color.Transparent
    },
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "rowContainer",
  )
  Row(
    modifier.fillMaxWidth()
      .clip(MaterialTheme.shapes.medium)
      .background(container)
      .combinedClickable(
        onClick = onClick,
        onLongClick = onLongClick,
      )
      .padding(horizontal = 8.dp, vertical = 8.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(14.dp),
  ) {
    if (showArtwork) {
      Box(contentAlignment = Alignment.Center) {
        Artwork(track, 54.dp, Modifier.padding(1.dp), corner = 14.dp)
        if (playing) {
          Box(
            Modifier.size(54.dp).clip(RoundedCornerShape(14.dp))
              .background(Color.Black.copy(alpha = 0.42f)),
            contentAlignment = Alignment.Center,
          ) { PlayingBars(Color.White) }
        }
      }
    }
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
      Text(
        track.title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.titleSmall,
        color = if (playing) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
        fontWeight = if (playing) FontWeight.Bold else FontWeight.Medium,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
          track.artist.ifBlank { "Unknown artist" },
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
          modifier = Modifier.weight(1f, fill = false),
        )
        if (track.missing) {
          Text("· missing", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error)
        } else if (progress != null) {
          val percent = progress.total?.takeIf { it > 0 }?.let { (progress.bytes * 100 / it).toInt() }
          Text(
            if (percent != null) "· $percent%" else "· ${fmt.bytes(progress.bytes)}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.tertiary,
          )
        }
      }
    }
    if (track.durationMs > 0 && progress == null) {
      Text(
        fmt.duration(track.durationMs),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    if (trailing != null) {
      trailing()
    } else if (!selectionMode) {
      var open by remember(track.id) { mutableStateOf(false) }
      IconButton(onClick = { open = true }, modifier = Modifier.size(40.dp)) {
        Icon(Icons.Default.MoreVert, contentDescription = "More actions", modifier = Modifier.size(20.dp))
      }
      if (open) {
        TrackActionsSheet(track, vm, onDismiss = { open = false })
      }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun TrackActionsSheet(track: TrackEntity, vm: AuraViewModel, onDismiss: () -> Unit) {
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  var pickingPlaylist by remember { mutableStateOf(false) }
  var confirmingDelete by remember { mutableStateOf(false) }
  val context = LocalContext.current
  val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
    Column(Modifier.padding(bottom = 24.dp)) {
      Row(Modifier.padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Artwork(track, 58.dp, corner = 16.dp)
        Column(Modifier.weight(1f).padding(start = 14.dp)) {
          Text(track.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            track.artist.ifBlank { "Unknown artist" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
          )
        }
      }
      ActionRow(
        icon = { Icon(if (track.id in favorites) Icons.Default.Favorite else Icons.Default.FavoriteBorder, null) },
        title = if (track.id in favorites) "Remove favourite" else "Add to favourites",
      ) { vm.toggleFavorite(track); onDismiss() }
      ActionRow(icon = { Icon(Icons.Default.SkipNext, null) }, title = "Play next") { vm.enqueue(track, true); onDismiss() }
      ActionRow(icon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, null) }, title = "Add to queue") { vm.enqueue(track, false); onDismiss() }
      ActionRow(icon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, null) }, title = "Add to playlist") { pickingPlaylist = true }
      if (track.source == Source.DIRECT || track.source == Source.CUSTOM || track.source == Source.PREVIEW || track.source == Source.RADIO) {
        val progress = downloads[track.id]
        when {
          progress != null -> ActionRow(icon = { Icon(Icons.Default.RemoveCircleOutline, null) }, title = "Cancel download") {
            vm.cancelDownload(track.id); onDismiss()
          }
          track.downloadedPath != null -> ActionRow(icon = { Icon(Icons.Default.RemoveCircleOutline, null) }, title = "Remove download") {
            vm.removeDownload(track); onDismiss()
          }
          else -> ActionRow(icon = { Icon(Icons.Default.CloudDownload, null) }, title = "Download") { vm.download(track); onDismiss() }
        }
      }
      ActionRow(icon = { Icon(Icons.Default.ContentCopy, null) }, title = "Copy info") {
        val clipboard = context.getSystemService(android.content.ClipboardManager::class.java)
        clipboard?.setPrimaryClip(
          android.content.ClipData.newPlainText("track", "${track.title} — ${track.artist}".trim(' ', '—')),
        )
        onDismiss()
      }
      ActionRow(icon = { Icon(Icons.Default.Share, null) }, title = "Share title") {
        val text = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" — ")
        val intent = android.content.Intent(android.content.Intent.ACTION_SEND).setType("text/plain")
          .putExtra(android.content.Intent.EXTRA_TEXT, text)
        runCatching { context.startActivity(android.content.Intent.createChooser(intent, "Share")) }
        onDismiss()
      }
      if (track.uri.startsWith("http")) {
        ActionRow(icon = { Icon(Icons.Default.Info, null) }, title = "Open link") {
          runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(track.uri))) }
          onDismiss()
        }
      }
      ActionRow(icon = { Icon(Icons.Default.DeleteOutline, null) }, title = "Remove from library", destructive = true) {
        confirmingDelete = true
      }
    }
  }
  if (pickingPlaylist) {
    AlertDialog(
      onDismissRequest = { pickingPlaylist = false },
      title = { Text("Add to playlist") },
      text = {
        if (playlists.isEmpty()) {
          Text("No playlists yet — create one first.")
        } else {
          LazyColumn(Modifier.heightIn(max = 320.dp)) {
            items(playlists, key = { it.id }) { playlist ->
              Row(
                Modifier.fillMaxWidth().clip(MaterialTheme.shapes.small)
                  .clickable { vm.addToPlaylist(playlist.id, track.id); pickingPlaylist = false; onDismiss() }
                  .padding(vertical = 14.dp, horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
              ) {
                Text(playlist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
            }
          }
        }
      },
      confirmButton = { TextButton(onClick = { pickingPlaylist = false }) { Text("Cancel") } },
    )
  }
  if (confirmingDelete) {
    AlertDialog(
      onDismissRequest = { confirmingDelete = false },
      title = { Text("Remove track?") },
      text = { Text("It is removed from AuraPulse. The original file is never deleted.") },
      confirmButton = {
        TextButton(onClick = { confirmingDelete = false; vm.removeTrack(track); onDismiss() }) { Text("Remove") }
      },
      dismissButton = { TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") } },
    )
  }
}

@Composable
private fun ActionRow(
  icon: @Composable () -> Unit,
  title: String,
  destructive: Boolean = false,
  onClick: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth()
      .clickable(onClick = onClick)
      .padding(horizontal = 24.dp, vertical = 14.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(18.dp),
  ) {
    CompositionLocalIcon(icon, destructive)
    Text(
      title,
      style = MaterialTheme.typography.bodyLarge,
      color = if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
    )
  }
}

@Composable
private fun CompositionLocalIcon(icon: @Composable () -> Unit, destructive: Boolean) {
  Box(
    Modifier.size(24.dp),
    contentAlignment = Alignment.Center,
  ) {
    androidx.compose.runtime.CompositionLocalProvider(
      androidx.compose.material3.LocalContentColor provides if (destructive) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
    ) { icon() }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(
  vm: AuraViewModel,
  onOpen: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val state by vm.playback.collectAsStateWithLifecycle()
  val track = state.track ?: return
  val context = LocalContext.current
  val settings by vm.settings.collectAsStateWithLifecycle()
  val hapticsOn = settings.haptics
  val interaction = remember { MutableInteractionSource() }
  val scale = rememberPressScale(interaction, 0.98f)
  Surface(
    modifier = modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 6.dp)
      .androidxScale(scale)
      .clip(MaterialTheme.shapes.extraLarge)
      .clickable(interactionSource = interaction, indication = null, onClick = onOpen),
    color = MaterialTheme.colorScheme.surfaceContainerHigh,
    tonalElevation = 3.dp,
    shape = MaterialTheme.shapes.extraLarge,
  ) {
    Column {
      Row(
        Modifier.padding(start = 8.dp, end = 6.dp, top = 8.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Artwork(track, 44.dp, corner = 12.dp)
        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
          Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(
            track.artist.ifBlank { "Unknown artist" },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        IconButton(onClick = { if (hapticsOn) SafeHaptics.tick(context); vm.toggle() }) {
          Icon(
            if (state.playing) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = if (state.playing) "Pause" else "Play",
          )
        }
        IconButton(onClick = vm::next) { Icon(Icons.Default.SkipNext, contentDescription = "Next") }
      }
      val fraction = if (state.durationMs > 0) (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) else 0f
      WaveProgressBar(
        progress = fraction,
        modifier = Modifier.fillMaxWidth().height(12.dp).padding(horizontal = 12.dp),
        barHeight = 4.dp,
        amplitude = if (state.playing) 2.dp else 0.dp,
        color = MaterialTheme.colorScheme.primary,
        trackColor = MaterialTheme.colorScheme.surfaceVariant,
      )
      Spacer(Modifier.height(6.dp))
    }
  }
}

private fun Modifier.androidxScale(value: Float): Modifier = this.scale(value)

@Composable
fun MixCard(
  title: String,
  subtitle: String,
  colors: List<Color>,
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
) {
  val interaction = remember { MutableInteractionSource() }
  val scale = rememberPressScale(interaction, 0.96f)
  Box(
    modifier.width(186.dp).height(122.dp)
      .androidxScale(scale)
      .clip(MaterialTheme.shapes.large)
      .background(androidx.compose.ui.graphics.Brush.linearGradient(colors))
      .clickable(interactionSource = interaction, indication = null, onClick = onClick)
      .padding(16.dp),
  ) {
    Column(Modifier.align(Alignment.BottomStart)) {
      Text(title, style = MaterialTheme.typography.titleMedium, color = Color.White, fontWeight = FontWeight.Bold)
      Text(subtitle, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.78f))
    }
    Box(
      Modifier.align(Alignment.TopEnd).size(34.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.22f)),
      contentAlignment = Alignment.Center,
    ) { Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp)) }
  }
}

@Composable
fun PlaylistCard(playlist: PlaylistEntity, trackCount: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
  val colors = com.hotsteel.aurapulse.ui.theme.hueGradient(playlist.accent * 57f + 20f)
  Column(modifier.width(150.dp).clickable(onClick = onClick)) {
    Box(
      Modifier.size(150.dp).clip(MaterialTheme.shapes.large)
        .background(androidx.compose.ui.graphics.Brush.linearGradient(colors)),
      contentAlignment = Alignment.Center,
    ) {
      Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(44.dp))
    }
    Spacer(Modifier.height(8.dp))
    Text(playlist.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
    Text(
      stringResource(R.string.library_tracks_count, trackCount),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
  }
}
