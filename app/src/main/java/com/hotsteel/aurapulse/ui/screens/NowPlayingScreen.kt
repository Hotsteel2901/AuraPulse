package com.hotsteel.aurapulse.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Forward30
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay10
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.toShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.library.LyricLine
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.playback.EnhancementState
import com.hotsteel.aurapulse.playback.SleepState
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.MarqueeText
import com.hotsteel.aurapulse.ui.components.PlayingBars
import com.hotsteel.aurapulse.ui.components.SourceBadge
import com.hotsteel.aurapulse.ui.components.TrackActionsSheet
import com.hotsteel.aurapulse.ui.components.artworkColors
import com.hotsteel.aurapulse.ui.components.rememberPressScale
import com.hotsteel.aurapulse.ui.theme.hueGradient
import kotlin.math.cos
import kotlin.math.sin
import kotlinx.coroutines.delay

private enum class PlayerPanel { NONE, LYRICS, QUEUE, SOUND, TIMER }

/**
 * Full-screen player. Everything here answers a real player need: seek, shuffle,
 * repeat, favourite, queue surgery, synced lyrics, playback speed, A–B loop and a
 * sleep timer that keeps running with the screen off.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun NowPlayingScreen(
  vm: AuraViewModel,
  onDismiss: () -> Unit,
  onOpenEqualizer: () -> Unit,
  onOpenArtist: (String) -> Unit,
) {
  val playback by vm.playback.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val enhancements by vm.enhancements.collectAsStateWithLifecycle()
  val sleep by vm.sleep.collectAsStateWithLifecycle()
  val lyrics by vm.lyrics.collectAsStateWithLifecycle()
  val haptics = LocalHapticFeedback.current

  var panel by remember { mutableStateOf(PlayerPanel.NONE) }
  var actions by remember { mutableStateOf(false) }

  val track = playback.track
  val palette = remember(track?.id) { track?.artworkColors() ?: hueGradient(210f) }

  val surfaceColor = MaterialTheme.colorScheme.surface
  Box(Modifier.fillMaxSize().background(surfaceColor)) {
    // Deep gradient derived from the artwork, plus the drifting aura used app-wide.
    Canvas(Modifier.fillMaxSize()) {
      drawRect(
        Brush.verticalGradient(
          listOf(
            palette.first().copy(alpha = 0.34f),
            surfaceColor.copy(alpha = 0.94f),
            surfaceColor,
          ),
        ),
      )
      drawCircle(
        brush = Brush.radialGradient(
          listOf(palette.last().copy(alpha = 0.26f), Color.Transparent),
          center = Offset(size.width * 0.86f, size.height * 0.08f),
          radius = size.minDimension * 0.9f,
        ),
        radius = size.minDimension * 0.9f,
        center = Offset(size.width * 0.86f, size.height * 0.08f),
      )
    }

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp)) {
      PlayerTopBar(
        onDismiss = onDismiss,
        onMore = { actions = true },
        favorite = track != null && track.id in favorites,
        onFavorite = { track?.let(vm::toggleFavorite) },
      )

      if (track == null) {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          Text(
            stringResource(R.string.player_connecting),
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
      } else {
        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
          BoxWithConstraints(contentAlignment = Alignment.Center) {
            val side = minOf(maxWidth - 24.dp, maxHeight - 16.dp).coerceAtLeast(120.dp)
            AuraRing(
              size = side,
              playing = playback.playing,
              colors = palette,
              modifier = Modifier.align(Alignment.Center),
            )
            Artwork(
              track = track,
              size = (side - 26.dp).coerceAtLeast(96.dp),
              modifier = breathingModifier(playback.playing),
              corner = 28.dp,
            )
          }
        }

        MarqueTitle(track, playing = playback.playing)
        Spacer(Modifier.height(4.dp))
        ArtistLine(track) { onOpenArtist(track.artist) }
        Spacer(Modifier.height(10.dp))

        WavySeekBar(
          positionMs = playback.positionMs,
          durationMs = playback.durationMs,
          canSeek = playback.canSeek,
          playing = playback.playing,
          onSeek = vm::seek,
        )
        Row(Modifier.fillMaxWidth().padding(top = 2.dp), verticalAlignment = Alignment.CenterVertically) {
          Text(
            fmt.duration(playback.positionMs),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          Spacer(Modifier.weight(1f))
          Text(
            if (playback.durationMs > 0) fmt.duration(playback.durationMs) else stringResource(R.string.live_or_unknown),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }

        TransportRow(vm, playback.playing, playback.shuffle, playback.repeat)
        Spacer(Modifier.height(10.dp))
        PanelChips(
          panel = panel,
          sleepActive = sleep.active,
          hasLyrics = lyrics != null,
          onPick = { chosen ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
            panel = if (panel == chosen) PlayerPanel.NONE else chosen
          },
        )
        Spacer(Modifier.height(18.dp))
      }
    }

    if (actions && track != null) {
      TrackActionsSheet(track, vm, onDismiss = { actions = false })
    }

    if (panel != PlayerPanel.NONE && track != null) {
      PlayerSheet(
        panel = panel,
        onSwitch = { panel = it },
        onClose = { panel = PlayerPanel.NONE },
        vm = vm,
        track = track,
        enhancements = enhancements,
        sleep = sleep,
        onOpenEqualizer = {
          panel = PlayerPanel.NONE
          onOpenEqualizer()
        },
      )
    }
  }
}

@Composable
private fun breathingModifier(playing: Boolean): Modifier {
  if (!playing) return Modifier
  val transition = rememberInfiniteTransition(label = "breath")
  val pulse by transition.animateFloat(
    initialValue = 0.988f,
    targetValue = 1f,
    animationSpec = infiniteRepeatable(tween(3600, easing = FastOutSlowInEasing), RepeatMode.Reverse),
    label = "pulse",
  )
  return Modifier.graphicsLayer { scaleX = pulse; scaleY = pulse }
}

@Composable
private fun PlayerTopBar(
  onDismiss: () -> Unit,
  onMore: () -> Unit,
  favorite: Boolean,
  onFavorite: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(top = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onDismiss) {
      Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.minimize_player))
    }
    Text(
      stringResource(R.string.now_playing).uppercase(),
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
      letterSpacing = 2.2.sp,
      textAlign = TextAlign.Center,
      modifier = Modifier.weight(1f),
    )
    IconButton(onClick = onFavorite) {
      Icon(
        if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
        contentDescription = stringResource(if (favorite) R.string.favorite_remove else R.string.favorite_add),
        tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = onMore) {
      Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
    }
  }
}

/** Rotating shape-morph ring behind the artwork; purely decorative, stops when paused. */
@Composable
private fun AuraRing(size: androidx.compose.ui.unit.Dp, playing: Boolean, colors: List<Color>, modifier: Modifier = Modifier) {
  val transition = rememberInfiniteTransition(label = "ring")
  val spin by transition.animateFloat(
    initialValue = 0f,
    targetValue = 360f,
    animationSpec = infiniteRepeatable(
      animation = tween(if (playing) 26_000 else 64_000, easing = LinearEasing),
      repeatMode = RepeatMode.Restart,
    ),
    label = "spin",
  )
  Canvas(modifier.size(size)) {
    val radius = this.size.minDimension / 2f - 2.dp.toPx()
    drawCircle(
      brush = Brush.sweepGradient(colors.map { it.copy(alpha = 0.55f) } + colors.first().copy(alpha = 0.55f)),
      radius = radius,
      style = androidx.compose.ui.graphics.drawscope.Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
    )
    val inner = radius - 9.dp.toPx()
    for (index in 0..2) {
      val start = spin + index * 120f
      drawArc(
        color = colors[index % colors.size].copy(alpha = 0.5f),
        startAngle = start,
        sweepAngle = 46f,
        useCenter = false,
        topLeft = Offset(center.x - inner, center.y - inner),
        size = androidx.compose.ui.geometry.Size(inner * 2, inner * 2),
        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 6.dp.toPx(), cap = StrokeCap.Round),
      )
    }
    // Three drifting satellites, echoing the app backdrop.
    repeat(3) { index ->
      val angle = Math.toRadians((spin * 1.4 + index * 120).toDouble())
      val distance = radius - 22.dp.toPx()
      drawCircle(
        color = colors[index % colors.size].copy(alpha = 0.85f),
        radius = (2.6f - index * 0.4f).dp.toPx(),
        center = Offset(
          center.x + (cos(angle) * distance).toFloat(),
          center.y + (sin(angle) * distance).toFloat(),
        ),
      )
    }
  }
}

@Composable
private fun MarqueTitle(track: TrackEntity, playing: Boolean) {
  Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
    Box(Modifier.weight(1f, fill = false)) {
      MarqueeText(
        text = track.title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.headlineSmallEmphasized,
        color = MaterialTheme.colorScheme.onSurface,
      )
    }
    if (playing) PlayingBars()
    SourceBadge(track)
  }
}

@Composable
private fun ArtistLine(track: TrackEntity, onOpenArtist: () -> Unit) {
  val artist = track.artist.ifBlank { track.albumArtist }
  Row(
    Modifier
      .clip(CircleShape)
      .clickable(enabled = artist.isNotBlank(), onClick = onOpenArtist)
      .padding(vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Text(
      artist.ifBlank { stringResource(R.string.unknown_artist) },
      style = MaterialTheme.typography.titleMedium,
      color = MaterialTheme.colorScheme.primary,
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
      modifier = Modifier.weight(1f, fill = false),
    )
    if (track.album.isNotBlank()) {
      Text(
        "  ·  ${track.album}",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
  }
}

/**
 * Wave-shaped progress bar. Dragging or tapping scrubs; the wave only animates
 * while audio is actually playing, so the UI never lies about the state.
 */
@Composable
private fun WavySeekBar(
  positionMs: Long,
  durationMs: Long,
  canSeek: Boolean,
  playing: Boolean,
  onSeek: (Long) -> Unit,
) {
  var dragFraction by remember { mutableStateOf<Float?>(null) }
  val liveFraction = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f
  val fraction = dragFraction ?: liveFraction

  Box(
    Modifier
      .fillMaxWidth()
      .height(34.dp)
      .then(
        if (canSeek) {
          Modifier
            .pointerInput(durationMs) {
              detectTapGestures { offset ->
                if (durationMs > 0) onSeek((offset.x / size.width * durationMs).toLong())
              }
            }
            .pointerInput(durationMs) {
              detectHorizontalDragGestures(
                onDragStart = { offset ->
                  if (durationMs > 0) dragFraction = (offset.x / size.width).coerceIn(0f, 1f)
                },
                onHorizontalDrag = { change, _ ->
                  if (durationMs > 0) dragFraction = (change.position.x / size.width).coerceIn(0f, 1f)
                },
                onDragEnd = {
                  dragFraction?.let { value -> if (durationMs > 0) onSeek((value * durationMs).toLong()) }
                  dragFraction = null
                },
                onDragCancel = { dragFraction = null },
              )
            }
        } else Modifier,
      ),
    contentAlignment = Alignment.Center,
  ) {
    LinearWavyProgressIndicator(
      progress = { fraction },
      modifier = Modifier.fillMaxWidth(),
      color = MaterialTheme.colorScheme.primary,
      trackColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
      waveSpeed = if (playing && canSeek) 16.dp else 0.dp,
      amplitude = { if (playing && canSeek) 1f else 0f },
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun TransportRow(vm: AuraViewModel, playing: Boolean, shuffle: Boolean, repeat: Int) {
  val haptics = LocalHapticFeedback.current
  Row(
    Modifier.fillMaxWidth(),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.SpaceBetween,
  ) {
    IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.toggleShuffle() }) {
      Icon(
        Icons.Default.Shuffle,
        contentDescription = stringResource(R.string.shuffle),
        tint = if (shuffle) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }
    IconButton(onClick = vm::previous, modifier = Modifier.size(52.dp)) {
      Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.previous_track), modifier = Modifier.size(32.dp))
    }
    PlayPauseButton(vm, playing)
    IconButton(onClick = vm::next, modifier = Modifier.size(52.dp)) {
      Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.next_track), modifier = Modifier.size(32.dp))
    }
    IconButton(onClick = { haptics.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.cycleRepeat() }) {
      Icon(
        if (repeat == androidx.media3.common.Player.REPEAT_MODE_ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
        contentDescription = when (repeat) {
          androidx.media3.common.Player.REPEAT_MODE_ONE -> stringResource(R.string.repeat_one)
          androidx.media3.common.Player.REPEAT_MODE_ALL -> stringResource(R.string.repeat_all)
          else -> stringResource(R.string.repeat_off)
        },
        tint = if (repeat == androidx.media3.common.Player.REPEAT_MODE_OFF) {
          MaterialTheme.colorScheme.onSurfaceVariant
        } else MaterialTheme.colorScheme.primary,
      )
    }
  }
}

@Composable
private fun PlayPauseButton(vm: AuraViewModel, playing: Boolean) {
  val haptics = LocalHapticFeedback.current
  val interaction = remember { MutableInteractionSource() }
  val scale = rememberPressScale(interaction, 0.9f)
  val motion = MaterialTheme.motionScheme
  val container by animateColorAsState(
    targetValue = MaterialTheme.colorScheme.primary,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "playContainer",
  )
  Surface(
    modifier = Modifier
      .size(84.dp)
      .graphicsLayer { scaleX = scale; scaleY = scale },
    shape = MaterialShapes.Cookie9Sided.toShape(),
    color = container,
    tonalElevation = 6.dp,
    shadowElevation = 10.dp,
    onClick = {
      haptics.performHapticFeedback(HapticFeedbackType.LongPress)
      vm.toggle()
    },
    interactionSource = interaction,
  ) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
      AnimatedContent(
        targetState = playing,
        transitionSpec = {
          (fadeIn(motion.fastEffectsSpec()) +
            scaleIn(motion.fastSpatialSpec(), initialScale = 0.7f)) togetherWith
            fadeOut(motion.fastEffectsSpec())
        },
        label = "playIcon",
      ) { isPlaying ->
        Icon(
          if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
          contentDescription = if (isPlaying) stringResource(R.string.pause) else stringResource(R.string.play),
          tint = MaterialTheme.colorScheme.onPrimary,
          modifier = Modifier.size(40.dp),
        )
      }
    }
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PanelChips(
  panel: PlayerPanel,
  sleepActive: Boolean,
  hasLyrics: Boolean,
  onPick: (PlayerPanel) -> Unit,
) {
  Row(
    Modifier.fillMaxWidth(),
    horizontalArrangement = Arrangement.spacedBy(8.dp),
  ) {
    PanelChip(
      label = stringResource(R.string.player_lyrics),
      icon = Icons.Default.Subtitles,
      selected = panel == PlayerPanel.LYRICS,
      badge = hasLyrics,
      modifier = Modifier.weight(1f),
    ) { onPick(PlayerPanel.LYRICS) }
    PanelChip(
      label = stringResource(R.string.player_queue),
      icon = Icons.AutoMirrored.Filled.QueueMusic,
      selected = panel == PlayerPanel.QUEUE,
      modifier = Modifier.weight(1f),
    ) { onPick(PlayerPanel.QUEUE) }
    PanelChip(
      label = stringResource(R.string.player_sound),
      icon = Icons.Default.GraphicEq,
      selected = panel == PlayerPanel.SOUND,
      modifier = Modifier.weight(1f),
    ) { onPick(PlayerPanel.SOUND) }
    PanelChip(
      label = stringResource(R.string.player_timer),
      icon = Icons.Default.Timer,
      selected = panel == PlayerPanel.TIMER,
      badge = sleepActive,
      modifier = Modifier.weight(1f),
    ) { onPick(PlayerPanel.TIMER) }
  }
}

@Composable
private fun PanelChip(
  label: String,
  icon: androidx.compose.ui.graphics.vector.ImageVector,
  selected: Boolean,
  modifier: Modifier = Modifier,
  badge: Boolean = false,
  onClick: () -> Unit,
) {
  val container by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "chip",
  )
  val content by animateColorAsState(
    targetValue = if (selected) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "chipContent",
  )
  Column(
    modifier
      .clip(MaterialTheme.shapes.large)
      .background(container)
      .clickable(onClick = onClick)
      .padding(vertical = 10.dp),
    horizontalAlignment = Alignment.CenterHorizontally,
    verticalArrangement = Arrangement.spacedBy(3.dp),
  ) {
    Box {
      Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(20.dp))
      if (badge) {
        Box(
          Modifier
            .align(Alignment.TopEnd)
            .size(6.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.primary),
        )
      }
    }
    Text(label, style = MaterialTheme.typography.labelSmall, color = content, maxLines = 1)
  }
}

// ------------------------------------------------------------------ sheet

@OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun PlayerSheet(
  panel: PlayerPanel,
  onSwitch: (PlayerPanel) -> Unit,
  onClose: () -> Unit,
  vm: AuraViewModel,
  track: TrackEntity,
  enhancements: EnhancementState,
  sleep: SleepState,
  onOpenEqualizer: () -> Unit,
) {
  val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
  ModalBottomSheet(onDismissRequest = onClose, sheetState = sheet) {
    Column(Modifier.fillMaxWidth()) {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Text(
          when (panel) {
            PlayerPanel.LYRICS -> stringResource(R.string.lyrics_title)
            PlayerPanel.QUEUE -> stringResource(R.string.queue_title)
            PlayerPanel.SOUND -> stringResource(R.string.sound_controls)
            else -> stringResource(R.string.sleep_timer)
          },
          style = MaterialTheme.typography.titleLargeEmphasized,
          modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onClose) {
          Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close))
        }
      }
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
      ) {
        listOf(
          PlayerPanel.LYRICS to R.string.player_lyrics,
          PlayerPanel.QUEUE to R.string.player_queue,
          PlayerPanel.SOUND to R.string.player_sound,
          PlayerPanel.TIMER to R.string.player_timer,
        ).forEach { (target, label) ->
          Surface(
            color = if (panel == target) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
            contentColor = if (panel == target) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
            shape = CircleShape,
            modifier = Modifier.weight(1f).clickable { onSwitch(target) },
          ) {
            Text(
              stringResource(label),
              style = MaterialTheme.typography.labelMedium,
              textAlign = TextAlign.Center,
              maxLines = 1,
              modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
            )
          }
        }
      }
      Spacer(Modifier.height(8.dp))
      when (panel) {
        PlayerPanel.LYRICS -> LyricsPanel(vm, track)
        PlayerPanel.QUEUE -> QueuePanel(vm)
        PlayerPanel.SOUND -> SoundPanel(vm, enhancements, onOpenEqualizer)
        PlayerPanel.TIMER -> TimerPanel(vm, sleep)
        PlayerPanel.NONE -> Unit
      }
      Spacer(Modifier.height(26.dp))
    }
  }
}

@Composable
private fun LyricsPanel(vm: AuraViewModel, track: TrackEntity) {
  val bundle by vm.lyrics.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val context = LocalContext.current
  val listState = rememberLazyListState()
  var follow by remember { mutableStateOf(true) }

  val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
    if (uri != null) {
      runCatching {
        context.contentResolver.takePersistableUriPermission(uri, android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
      }
      vm.attachLyrics(track.id, uri)
    }
  }

  val lyrics = bundle?.takeIf { it.trackId == track.id }?.lyrics
  val offset = bundle?.takeIf { it.trackId == track.id }?.offsetMs ?: 0

  if (lyrics == null || lyrics.lines.isEmpty()) {
    Column(
      Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 18.dp),
      horizontalAlignment = Alignment.CenterHorizontally,
      verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
      Text(
        stringResource(R.string.lyrics_empty),
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onSurface,
      )
      Text(
        stringResource(R.string.lyrics_empty_hint),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
      )
      FilledTonalButton(onClick = { picker.launch(arrayOf("text/*", "application/octet-stream", "*/*")) }) {
        Text(stringResource(R.string.lyrics_attach))
      }
    }
    return
  }

  val index = if (lyrics.synced) lyrics.indexAt(playback.positionMs - offset) else -1
  LaunchedEffect(index, follow) {
    if (follow && index >= 0) {
      listState.animateScrollToItem(index.coerceAtLeast(0), scrollOffset = -120)
    }
  }

  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
      TextButton(onClick = { vm.setLyricsOffset(track.id, offset - 500) }) { Text("−0.5s") }
      Text(
        stringResource(R.string.lyrics_offset_value, offset),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
      TextButton(onClick = { vm.setLyricsOffset(track.id, offset + 500) }) { Text("+0.5s") }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = { vm.detachLyrics(track.id) }) { Text(stringResource(R.string.lyrics_detach)) }
    }
    if (!lyrics.synced) {
      Text(
        stringResource(R.string.lyrics_unsynced),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.tertiary,
        modifier = Modifier.padding(horizontal = 20.dp),
      )
    }
    LazyColumn(
      state = listState,
      modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp, max = 420.dp),
      contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      itemsIndexed(lyrics.lines) { lineIndex, line ->
        LyricRow(
          line = line,
          active = lineIndex == index,
          synced = lyrics.synced,
          onClick = { if (lyrics.synced) vm.seek((line.timeMs + offset).coerceAtLeast(0)) },
        )
      }
    }
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.lyrics_follow),
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier.weight(1f),
      )
      Switch(checked = follow, onCheckedChange = { follow = it })
    }
  }
}

@Composable
private fun LyricRow(line: LyricLine, active: Boolean, synced: Boolean, onClick: () -> Unit) {
  val color by animateColorAsState(
    targetValue = if (active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
    animationSpec = MaterialTheme.motionScheme.defaultEffectsSpec(),
    label = "lyricColor",
  )
  val scale by animateFloatAsState(
    targetValue = if (active) 1f else 0.96f,
    animationSpec = MaterialTheme.motionScheme.defaultSpatialSpec(),
    label = "lyricScale",
  )
  Text(
    line.text.ifBlank { "♪" },
    style = if (active) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyLarge,
    color = color,
    fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
    modifier = Modifier
      .fillMaxWidth()
      .graphicsLayer { scaleX = scale; scaleY = scale; transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f) }
      .clip(MaterialTheme.shapes.small)
      .clickable(enabled = synced, onClick = onClick)
      .padding(vertical = 7.dp, horizontal = 4.dp),
  )
}

@Composable
private fun QueuePanel(vm: AuraViewModel) {
  val queue by vm.queue.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()

  Column(Modifier.fillMaxWidth()) {
    Row(
      Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
      verticalAlignment = Alignment.CenterVertically,
    ) {
      Text(
        stringResource(R.string.queue_count, queue.size),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.weight(1f),
      )
      TextButton(onClick = vm::clearQueue) { Text(stringResource(R.string.queue_clear)) }
    }
    if (queue.isEmpty()) {
      Text(
        stringResource(R.string.queue_empty),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 24.dp),
      )
      return
    }
    LazyColumn(
      Modifier.fillMaxWidth().heightIn(max = 460.dp),
      contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
    ) {
      itemsIndexed(queue, key = { index, item -> "${item.id}_$index" }) { index, item ->
        val current = index == playback.index
        Row(
          Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (current) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f) else Color.Transparent)
            .clickable { vm.seekToIndex(index) }
            .padding(start = 8.dp, end = 2.dp, top = 6.dp, bottom = 6.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
          Text(
            "$index",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(18.dp),
            textAlign = TextAlign.End,
          )
          Artwork(item, 38.dp, corner = 10.dp)
          Column(Modifier.weight(1f)) {
            Text(
              item.title.ifBlank { "Untitled" },
              style = MaterialTheme.typography.bodyMedium,
              color = if (current) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              item.artist.ifBlank { stringResource(R.string.unknown_artist) },
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
          if (current && playback.playing) PlayingBars()
          IconButton(onClick = { vm.moveQueue(index, index - 1) }, enabled = index > 0, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.move_up), modifier = Modifier.size(18.dp))
          }
          IconButton(
            onClick = { vm.moveQueue(index, index + 1) },
            enabled = index < queue.lastIndex,
            modifier = Modifier.size(34.dp),
          ) {
            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.move_down), modifier = Modifier.size(18.dp))
          }
          IconButton(onClick = { vm.removeQueue(index) }, modifier = Modifier.size(34.dp)) {
            Icon(Icons.Default.Close, contentDescription = stringResource(R.string.remove_from_queue), modifier = Modifier.size(18.dp))
          }
        }
      }
    }
  }
}

@Composable
private fun SoundPanel(vm: AuraViewModel, enhancements: EnhancementState, onOpenEqualizer: () -> Unit) {
  Column(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(4.dp),
  ) {
    if (!enhancements.available) {
      Text(
        stringResource(R.string.player_connecting),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
      )
    }

    ValueSlider(
      label = stringResource(R.string.speed_value, fmt.speed(enhancements.speed)),
      value = enhancements.speed,
      range = 0.5f..2f,
      onChange = vm::setSpeed,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
      listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
        Surface(
          color = if (kotlin.math.abs(preset - enhancements.speed) < 0.01f) {
            MaterialTheme.colorScheme.secondaryContainer
          } else MaterialTheme.colorScheme.surfaceContainerHigh,
          shape = CircleShape,
          modifier = Modifier.weight(1f).clickable { vm.setSpeed(preset) },
        ) {
          Text(
            "${fmt.speed(preset)}×",
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(vertical = 7.dp),
          )
        }
      }
    }
    Spacer(Modifier.height(6.dp))

    ValueSlider(
      label = stringResource(R.string.pitch_value, fmt.speed(enhancements.pitch)),
      value = enhancements.pitch,
      range = 0.5f..2f,
      onChange = vm::setPitch,
    )
    ValueSlider(
      label = stringResource(R.string.volume_value, (enhancements.volume * 100).toInt()),
      value = enhancements.volume,
      range = 0f..1f,
      onChange = vm::setVolume,
    )

    Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
      Column(Modifier.weight(1f)) {
        Text(stringResource(R.string.skip_silence), style = MaterialTheme.typography.bodyLarge)
        Text(
          stringResource(R.string.skip_silence_help),
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
      Switch(checked = enhancements.skipSilence, onCheckedChange = vm::setSkipSilence)
    }

    Spacer(Modifier.height(10.dp))
    Text(
      if (enhancements.looping) {
        stringResource(R.string.loop_active, fmt.duration(enhancements.loopStartMs ?: 0), fmt.duration(enhancements.loopEndMs ?: 0))
      } else {
        "A–B"
      },
      style = MaterialTheme.typography.labelMedium,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
      FilledTonalButton(onClick = vm::setLoopA, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.loop_set_a)) }
      FilledTonalButton(onClick = vm::setLoopB, modifier = Modifier.weight(1f)) { Text(stringResource(R.string.loop_set_b)) }
      TextButton(onClick = vm::clearLoop, enabled = enhancements.looping) { Text(stringResource(R.string.loop_clear)) }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      IconButton(onClick = { vm.jumpBy(-10_000) }) { Icon(Icons.Default.Replay10, stringResource(R.string.jump_back_10)) }
      IconButton(onClick = { vm.jumpBy(30_000) }) { Icon(Icons.Default.Forward30, stringResource(R.string.jump_forward_30)) }
      Spacer(Modifier.weight(1f))
      TextButton(onClick = vm::resetSound) { Text(stringResource(R.string.reset_sound_controls)) }
    }
    FilledTonalButton(onClick = onOpenEqualizer, modifier = Modifier.fillMaxWidth()) {
      Icon(Icons.Default.Equalizer, contentDescription = null, modifier = Modifier.size(18.dp))
      Spacer(Modifier.width(8.dp))
      Text(stringResource(R.string.open_equalizer))
    }
  }
}

@Composable
private fun ValueSlider(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
  Column(Modifier.fillMaxWidth()) {
    Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Slider(value = value, onValueChange = onChange, valueRange = range)
  }
}

@Composable
private fun TimerPanel(vm: AuraViewModel, sleep: SleepState) {
  var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
  LaunchedEffect(sleep.endsAt) {
    while (sleep.endsAt != null) {
      now = System.currentTimeMillis()
      delay(1000)
    }
  }
  val remaining = sleep.remainingMs(now)

  Column(
    Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    verticalArrangement = Arrangement.spacedBy(10.dp),
  ) {
    Text(
      stringResource(R.string.sleep_description),
      style = MaterialTheme.typography.bodySmall,
      color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    AnimatedVisibility(visible = sleep.active, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
      Surface(
        color = MaterialTheme.colorScheme.tertiaryContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
      ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
          Icon(Icons.Default.Bedtime, contentDescription = null, tint = MaterialTheme.colorScheme.onTertiaryContainer)
          Spacer(Modifier.width(12.dp))
          Text(
            when {
              sleep.fading -> stringResource(R.string.sleep_fading)
              sleep.atTrackEnd -> stringResource(R.string.sleep_active_end)
              remaining != null -> stringResource(R.string.sleep_active_time, fmt.duration(remaining))
              else -> stringResource(R.string.sleep_timer)
            },
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.weight(1f),
          )
          TextButton(onClick = vm::cancelSleep) { Text(stringResource(R.string.sleep_cancel)) }
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      listOf(15, 30, 45).forEach { minutes ->
        FilledTonalButton(onClick = { vm.startSleep(minutes) }, modifier = Modifier.weight(1f)) {
          Text("$minutes ${stringResource(R.string.minutes_short)}")
        }
      }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
      FilledTonalButton(onClick = { vm.startSleep(60) }, modifier = Modifier.weight(1f)) {
        Text("60 ${stringResource(R.string.minutes_short)}")
      }
      FilledTonalButton(onClick = { vm.startSleep(90) }, modifier = Modifier.weight(1f)) {
        Text("90 ${stringResource(R.string.minutes_short)}")
      }
    }
    FilledTonalButton(onClick = vm::sleepAtTrackEnd, modifier = Modifier.fillMaxWidth()) {
      Text(stringResource(R.string.sleep_end_track))
    }
  }
}
