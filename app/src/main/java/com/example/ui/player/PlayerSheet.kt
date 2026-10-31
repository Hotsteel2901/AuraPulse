package com.example.ui.player

import android.animation.ValueAnimator
import android.content.Intent
import android.os.Build
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import com.example.R
import com.example.audio.PlaybackEnhancements
import com.example.audio.PlaybackEnhancementState
import com.example.data.library.LibraryTrack
import java.util.Locale

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun MiniPlayer(state: PlaybackUiState, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
  val track = state.track ?: return
  Surface(
    color = MaterialTheme.colorScheme.surfaceVariant,
    shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
    modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp).clickable(onClick = onOpen),
  ) {
    Column {
      Row(Modifier.padding(start = 10.dp, end = 5.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        TrackArtwork(track, 46.dp)
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
          Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
          Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        IconButton(onClick = onToggle) {
          if (state.isBuffering) LoadingIndicator(modifier = Modifier.size(24.dp))
          else Icon(if (state.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            contentDescription = stringResource(if (state.isPlaying) R.string.pause else R.string.play))
        }
        IconButton(onClick = onNext) { Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_track)) }
      }
      if (state.durationMs > 0) {
        LinearWavyProgressIndicator(
          progress = { (state.positionMs.toFloat() / state.durationMs).coerceIn(0f, 1f) },
          modifier = Modifier.fillMaxWidth().height(6.dp).padding(horizontal = 12.dp),
          waveSpeed = if (state.isPlaying && animationsEnabled()) 16.dp else 0.dp,
        )
      } else Spacer(Modifier.height(6.dp))
    }
  }
}

@Composable
private fun animationsEnabled(): Boolean = remember {
  Build.VERSION.SDK_INT < 26 || ValueAnimator.areAnimatorsEnabled()
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class, ExperimentalLayoutApi::class)
@Composable
fun NowPlayingSheet(state: PlaybackUiState, vm: MusicViewModel, onDismiss: () -> Unit, onEqualizer: () -> Unit) {
  val track = state.track ?: return
  val enhanced by vm.enhancements.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val queue by vm.queue.collectAsStateWithLifecycle()
  var seeking by remember(track.id) { mutableStateOf(false) }
  var seekPosition by remember(track.id) { mutableFloatStateOf(0f) }
  var dialog by rememberSaveable { mutableStateOf("") }
  val context = LocalContext.current
  val motion = MaterialTheme.motionScheme
  ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surface) {
    Column(
      Modifier.fillMaxWidth().widthIn(max = 580.dp).align(Alignment.CenterHorizontally)
        .verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
    ) {
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Text(stringResource(R.string.now_playing).uppercase(), color = MaterialTheme.colorScheme.primary,
          style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
        Row {
          IconButton(onClick = { dialog = "info" }) { Icon(Icons.Default.Info, stringResource(R.string.track_details)) }
          IconButton(onClick = {
            // Share metadata only: private/signed streaming URLs and local paths stay on device.
            val text = listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" — ")
            val intent = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            runCatching { context.startActivity(Intent.createChooser(intent, context.getString(R.string.share_track))) }
          }) { Icon(Icons.Default.Share, stringResource(R.string.share_track)) }
        }
      }
      AnimatedContent(
        targetState = track,
        transitionSpec = { fadeIn(motion.defaultEffectsSpec()) togetherWith fadeOut(motion.fastEffectsSpec()) },
        label = "track artwork and metadata",
      ) { current ->
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
          BoxWithConstraints(
            Modifier.fillMaxWidth().background(
              Brush.radialGradient(listOf(MaterialTheme.colorScheme.primaryContainer.copy(alpha = .7f), MaterialTheme.colorScheme.surface)),
              RoundedCornerShape(36.dp),
            ).padding(18.dp), contentAlignment = Alignment.Center,
          ) { TrackArtwork(current, minOf(maxWidth - 36.dp, 280.dp).coerceAtLeast(100.dp)) }
          Spacer(Modifier.height(18.dp))
          Text(current.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, maxLines = 2, overflow = TextOverflow.Ellipsis)
          Text(current.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 5.dp))
          Text(sourceLabel(current), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 7.dp))
        }
      }
      IconButton(onClick = { vm.toggleFavorite(track) }) {
        val favorite = track.id in favorites
        Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
          contentDescription = stringResource(if (favorite) R.string.remove_favorite else R.string.add_favorite),
          tint = if (favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant)
      }
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
      Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        IconButton(onClick = vm::toggleShuffle) {
          Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.shuffle),
            tint = if (state.shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
        }
        IconButton(onClick = vm::previous, modifier = Modifier.size(52.dp)) {
          Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous_track), modifier = Modifier.size(32.dp))
        }
        Surface(shape = CircleShape, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(72.dp)) {
          IconButton(onClick = vm::togglePlay, modifier = Modifier.size(72.dp)) {
            if (state.isBuffering) LoadingIndicator(modifier = Modifier.size(30.dp), color = MaterialTheme.colorScheme.onPrimary)
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
      FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        TextButton(onClick = { vm.jumpBy(-10_000) }, enabled = state.canSeek) { Text(stringResource(R.string.jump_back_10)) }
        TextButton(onClick = { vm.jumpBy(30_000) }, enabled = state.canSeek) { Text(stringResource(R.string.jump_forward_30)) }
      }
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        OutlinedButton(onClick = { dialog = "queue" }) {
          Icon(Icons.AutoMirrored.Filled.QueueMusic, null, Modifier.size(18.dp))
          Text(stringResource(R.string.queue_count, queue.size), Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { dialog = "timer" }) { Text(stringResource(R.string.sleep_timer)) }
        OutlinedButton(onClick = { dialog = "audio" }) { Text(stringResource(R.string.sound_controls)) }
      }
      FlowRow(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(2.dp)) {
        FilterChip(selected = enhanced.loopStartMs != null, onClick = vm::setLoopStart, enabled = state.canSeek,
          label = { Text(enhanced.loopStartMs?.let { "A ${formatTime(it)}" } ?: stringResource(R.string.loop_set_a)) })
        FilterChip(selected = enhanced.loopEndMs != null, onClick = vm::setLoopEnd, enabled = state.canSeek && enhanced.loopStartMs != null,
          label = { Text(enhanced.loopEndMs?.let { "B ${formatTime(it)}" } ?: stringResource(R.string.loop_set_b)) })
        if (enhanced.loopStartMs != null) TextButton(onClick = PlaybackEnhancements::clearLoop) { Text(stringResource(R.string.loop_clear)) }
      }
      if (enhanced.sleepAtTrackEnd || enhanced.sleepRemainingSeconds != null) {
        Text(if (enhanced.sleepAtTrackEnd) stringResource(R.string.sleep_active_end)
          else stringResource(R.string.sleep_active_time, formatTime((enhanced.sleepRemainingSeconds ?: 0) * 1000)),
          style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
      }
      Spacer(Modifier.height(28.dp))
    }
  }
  when (dialog) {
    "queue" -> QueueDialog(queue, state.queueIndex, state.shuffle, vm) { dialog = "" }
    "timer" -> SleepTimerDialog(enhanced) { dialog = "" }
    "audio" -> AudioControlsDialog(enhanced, onEqualizer = { dialog = ""; onEqualizer() }) { dialog = "" }
    "info" -> TrackDetailsDialog(track, state.durationMs) { dialog = "" }
  }
}

@Composable
private fun QueueDialog(queue: List<LibraryTrack>, currentIndex: Int, shuffled: Boolean, vm: MusicViewModel, onDismiss: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.play_queue)) },
    text = {
      Column {
        if (shuffled) Text(stringResource(R.string.queue_shuffle_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (queue.isEmpty()) Text(stringResource(R.string.queue_empty))
        else LazyColumn(Modifier.heightIn(max = 380.dp)) {
          itemsIndexed(queue) { index, track ->
            Row(verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f).clickable { vm.playQueueItem(index) }.padding(vertical = 10.dp)) {
                Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, maxLines = 1, overflow = TextOverflow.Ellipsis,
                  color = if (index == currentIndex) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                  fontWeight = if (index == currentIndex) FontWeight.Bold else FontWeight.Normal)
                Text(track.artist.ifBlank { stringResource(R.string.unknown_artist) }, style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
              }
              IconButton(onClick = { vm.moveQueueItem(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowUpward, stringResource(R.string.move_up), Modifier.size(20.dp))
              }
              IconButton(onClick = { vm.moveQueueItem(index, index + 1) }, enabled = index < queue.lastIndex, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.ArrowDownward, stringResource(R.string.move_down), Modifier.size(20.dp))
              }
              IconButton(onClick = { vm.removeQueueItem(index) }, modifier = Modifier.size(40.dp)) {
                Icon(Icons.Default.DeleteOutline, stringResource(R.string.remove_from_queue), Modifier.size(20.dp))
              }
            }
          }
        }
      }
    },
    confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } },
  )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SleepTimerDialog(state: PlaybackEnhancementState, onDismiss: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sleep_timer)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.sleep_description), style = MaterialTheme.typography.bodySmall)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(5, 10, 15, 30, 45, 60, 90).forEach { minutes ->
            FilterChip(selected = false, onClick = { PlaybackEnhancements.sleepAfter(minutes); onDismiss() },
              label = { Text(stringResource(R.string.minutes_short, minutes)) })
          }
        }
        OutlinedButton(onClick = { PlaybackEnhancements.sleepAfterCurrentTrack(); onDismiss() }) { Text(stringResource(R.string.sleep_end_track)) }
        if (state.sleepAtTrackEnd || state.sleepRemainingSeconds != null) {
          TextButton(onClick = { PlaybackEnhancements.cancelSleep(); onDismiss() }) { Text(stringResource(R.string.sleep_cancel)) }
        }
      }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun AudioControlsDialog(state: PlaybackEnhancementState, onEqualizer: () -> Unit, onDismiss: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.sound_controls)) },
    text = {
      Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.speed_value, String.format(Locale.getDefault(), "%.2f", state.speed)))
        Slider(value = state.speed, onValueChange = PlaybackEnhancements::setSpeed, valueRange = 0.5f..2f, steps = 5, enabled = state.available)
        Text(stringResource(R.string.pitch_value, String.format(Locale.getDefault(), "%.2f", state.pitch)))
        Slider(value = state.pitch, onValueChange = PlaybackEnhancements::setPitch, valueRange = 0.5f..2f, steps = 5, enabled = state.available)
        Text(stringResource(R.string.volume_value, (state.volume * 100).toInt()))
        Slider(value = state.volume, onValueChange = PlaybackEnhancements::setVolume, valueRange = 0f..1f, enabled = state.available)
        Row(verticalAlignment = Alignment.CenterVertically) {
          Column(Modifier.weight(1f)) {
            Text(stringResource(R.string.skip_silence))
            Text(stringResource(R.string.skip_silence_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          }
          Switch(checked = state.skipSilence, onCheckedChange = PlaybackEnhancements::setSkipSilence, enabled = state.available)
        }
        OutlinedButton(onClick = onEqualizer) { Text(stringResource(R.string.open_equalizer)) }
        TextButton(onClick = {
          PlaybackEnhancements.setSpeed(1f); PlaybackEnhancements.setPitch(1f); PlaybackEnhancements.setVolume(1f)
          PlaybackEnhancements.setSkipSilence(false)
        }) { Text(stringResource(R.string.reset_sound_controls)) }
      }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}

@Composable
private fun TrackDetailsDialog(track: LibraryTrack, duration: Long, onDismiss: () -> Unit) {
  AlertDialog(onDismissRequest = onDismiss, title = { Text(stringResource(R.string.track_details)) },
    text = {
      Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(track.title.ifBlank { stringResource(R.string.unknown_track) }, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.detail_artist, track.artist.ifBlank { stringResource(R.string.unknown_artist) }))
        Text(stringResource(R.string.detail_album, track.album.ifBlank { stringResource(R.string.unknown_album) }))
        Text(stringResource(R.string.detail_source, sourceLabel(track)))
        Text(stringResource(R.string.detail_duration, if (duration > 0) formatTime(duration) else stringResource(R.string.live_or_unknown)))
        Text(stringResource(R.string.share_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
      }
    }, confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.done)) } })
}
