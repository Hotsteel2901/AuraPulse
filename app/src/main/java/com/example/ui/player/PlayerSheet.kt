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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import com.example.R

@Composable
fun MiniPlayer(state: PlaybackUiState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
  val track = state.track ?: return
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = onOpen),
  ) {
    Row(Modifier.padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
      TrackArtwork(track, 46.dp)
      Column(Modifier.weight(1f).padding(start = 12.dp)) {
        Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.titleSmall,
          fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
      }
      IconButton(onClick = onToggle) {
        if (state.isBuffering) CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
        else Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play))
      }
      IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_track)) }
    }
  }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlayingSheet(state: PlaybackUiState, vm: MusicViewModel, onDismiss: () -> Unit) {
  val track = state.track ?: return
  var seeking by remember(track.id) { mutableStateOf(false) }
  var seekPosition by remember(track.id) { mutableFloatStateOf(0f) }
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
    Column(
      Modifier.fillMaxWidth().widthIn(max = 520.dp).align(Alignment.CenterHorizontally).verticalScroll(rememberScrollState()).padding(horizontal = 28.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Text(stringResource(R.string.now_playing).uppercase(), color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
      Spacer(Modifier.height(24.dp))
      BoxWithArtwork(track)
      Spacer(Modifier.height(24.dp))
      Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
      Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
      Text(sourceLabel(track), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 9.dp))
      Spacer(Modifier.height(22.dp))
      Slider(
        value = if (seeking) seekPosition else state.positionMs.toFloat().coerceIn(0f, state.durationMs.coerceAtLeast(1).toFloat()),
        onValueChange = { seeking = true; seekPosition = it },
        onValueChangeFinished = { vm.seek(seekPosition.toLong()); seeking = false },
        valueRange = 0f..state.durationMs.coerceAtLeast(1).toFloat(), enabled = state.canSeek,
      )
      Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatTime(if (seeking) seekPosition.toLong() else state.positionMs), style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(if (state.durationMs > 0) formatTime(state.durationMs) else stringResource(R.string.live_or_unknown),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
      Spacer(Modifier.height(12.dp))
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = vm::toggleShuffle) {
          Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle),
            tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = vm::previous, modifier = Modifier.size(52.dp)) {
          Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous_track), modifier = Modifier.size(32.dp))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp).clickable(onClick = vm::togglePlay)) {
          Box(contentAlignment = Alignment.Center) {
            if (state.isBuffering) CircularProgressIndicator(modifier = Modifier.size(28.dp), color = MaterialTheme.colorScheme.onPrimary, strokeWidth = 2.dp)
            else Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
              contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play),
              tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(38.dp))
          }
        }
        IconButton(onClick = vm::next, modifier = Modifier.size(52.dp)) {
          Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_track), modifier = Modifier.size(32.dp))
        }
        IconButton(onClick = vm::cycleRepeat) {
          val label = when (state.repeatMode) { Player.REPEAT_MODE_ONE -> R.string.repeat_one; Player.REPEAT_MODE_ALL -> R.string.repeat_all; else -> R.string.repeat_off }
          Icon(if (state.repeatMode == Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
            contentDescription = stringResource(label), tint = if (state.repeatMode != Player.REPEAT_MODE_OFF) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      Spacer(Modifier.height(32.dp))
    }
  }
}

@Composable
private fun BoxWithArtwork(track: com.example.data.library.LibraryTrack) {
  androidx.compose.foundation.layout.BoxWithConstraints(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
    TrackArtwork(track, minOf(maxWidth, 300.dp))
  }
}
