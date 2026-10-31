package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudQueue
import androidx.compose.material.icons.filled.DownloadForOffline
import androidx.compose.material.icons.filled.FastForward
import androidx.compose.material.icons.filled.FastRewind
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Clear
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.OfflineTrackEntity
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.theme.CustomApiEmerald
import com.example.ui.theme.GlobalSkyBlue
import com.example.ui.theme.SubsonicOrange
import com.example.ui.theme.parseHexColor
import com.example.ui.viewmodel.PlayerUiState
import java.util.Locale

@Composable
fun HiFiDeckScreen(
  playerState: PlayerUiState,
  tracks: List<OfflineTrackEntity>,
  spectrumBins: List<Float>,
  rmsLevelDb: Float,
  onSelectTrack: (OfflineTrackEntity) -> Unit,
  onTogglePlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrevious: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onToggleOfflineDownload: (OfflineTrackEntity) -> Unit,
  onSelectServiceFilter: (String) -> Unit,
  onUpdateSearchQuery: (String) -> Unit,
  onToggleDynamicWallpaper: () -> Unit,
) {
  val strings = LocalAuraStrings.current
  val currentTrack = playerState.currentTrack ?: tracks.firstOrNull()
  val filteredTracks =
    tracks.filter { track ->
      val serviceMatch =
        playerState.activeServiceFilter == "ALL" ||
          (playerState.activeServiceFilter == "OFFLINE" && track.isDownloadedOffline) ||
          track.sourceProvider == playerState.activeServiceFilter
      val queryMatch =
        playerState.searchQuery.isBlank() ||
          track.title.contains(playerState.searchQuery, ignoreCase = true) ||
          track.artist.contains(playerState.searchQuery, ignoreCase = true) ||
          track.album.contains(playerState.searchQuery, ignoreCase = true)
      serviceMatch && queryMatch
    }

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("hifi_deck_list"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      if (currentTrack != null) {
        ExpressiveNowPlayingHeroCard(
          track = currentTrack,
          isPlaying = playerState.isPlaying,
          positionMs = playerState.positionMs,
          spectrumBins = spectrumBins,
          rmsLevelDb = rmsLevelDb,
          bitPerfect = playerState.dacBitPerfectMode,
          useSystemDynamic = playerState.useSystemDynamicWallpaper,
          activeRegion = playerState.selectedGlobalRegion,
          onTogglePlayPause = onTogglePlayPause,
          onSkipNext = onSkipNext,
          onSkipPrevious = onSkipPrevious,
          onSeekTo = onSeekTo,
          onToggleOfflineDownload = { onToggleOfflineDownload(currentTrack) },
          onToggleDynamicWallpaper = onToggleDynamicWallpaper,
        )
      }
    }

    item {
      Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(
          value = playerState.searchQuery,
          onValueChange = onUpdateSearchQuery,
          modifier = Modifier.fillMaxWidth().testTag("catalog_search_input"),
          placeholder = { Text(strings.searchPlaceholder) },
          leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search") },
          trailingIcon = {
            if (playerState.searchQuery.isNotEmpty()) {
              IconButton(onClick = { onUpdateSearchQuery("") }) {
                Icon(Icons.Outlined.Clear, contentDescription = "Clear search")
              }
            }
          },
          singleLine = true,
          shape = MaterialTheme.shapes.medium,
        )

        val filters =
          listOf(
            "ALL" to strings.filterAll,
            "AURA_HIFI" to strings.filterAuraHifi,
            "CUSTOM_API" to strings.filterCustomApi,
            "SUBSONIC" to strings.filterSubsonic,
            "OPEN_GLOBAL" to strings.filterOpenGlobal,
            "OFFLINE" to strings.filterOffline,
          )
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(filters) { (key, label) ->
            FilterChip(
              selected = playerState.activeServiceFilter == key,
              onClick = { onSelectServiceFilter(key) },
              label = { Text(label) },
              modifier = Modifier.testTag("filter_chip_$key"),
              colors =
                FilterChipDefaults.filterChipColors(
                  selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                  selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                ),
            )
          }
        }
      }
    }

    if (filteredTracks.isEmpty()) {
      item {
        Card(
          modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp),
          colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
          Column(
            modifier = Modifier.fillMaxWidth().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            Icon(
              Icons.Filled.GraphicEq,
              contentDescription = null,
              tint = MaterialTheme.colorScheme.primary,
              modifier = Modifier.size(36.dp),
            )
            Text(
              strings.emptyTracksTitle,
              style = MaterialTheme.typography.titleMedium,
            )
            Text(
              strings.emptyTracksSubtitle,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }
      }
    } else {
      items(filteredTracks, key = { it.trackId }) { track ->
        TrackRowCard(
          track = track,
          isCurrent = track.trackId == currentTrack?.trackId,
          isPlaying = playerState.isPlaying && track.trackId == currentTrack?.trackId,
          onSelect = { onSelectTrack(track) },
          onToggleOffline = { onToggleOfflineDownload(track) },
        )
      }
    }
  }
}

@Composable
private fun ExpressiveNowPlayingHeroCard(
  track: OfflineTrackEntity,
  isPlaying: Boolean,
  positionMs: Long,
  spectrumBins: List<Float>,
  rmsLevelDb: Float,
  bitPerfect: Boolean,
  useSystemDynamic: Boolean,
  activeRegion: String,
  onTogglePlayPause: () -> Unit,
  onSkipNext: () -> Unit,
  onSkipPrevious: () -> Unit,
  onSeekTo: (Long) -> Unit,
  onToggleOfflineDownload: () -> Unit,
  onToggleDynamicWallpaper: () -> Unit,
) {
  val accentColor = parseHexColor(track.accentHex, MaterialTheme.colorScheme.primary)
  val secondaryColor = parseHexColor(track.secondaryHex, MaterialTheme.colorScheme.secondary)

  val infiniteTransition = rememberInfiniteTransition(label = "vinylSpin")
  val rawAngle by
    infiniteTransition.animateFloat(
      initialValue = 0f,
      targetValue = 360f,
      animationSpec =
        infiniteRepeatable(
          animation = tween(durationMillis = 12000, easing = LinearEasing),
          repeatMode = RepeatMode.Restart,
        ),
      label = "discRotation",
    )
  val rotationAngle = if (isPlaying) rawAngle else 0f

  Card(
    modifier = Modifier.fillMaxWidth().testTag("now_playing_hero_card"),
    shape = MaterialTheme.shapes.extraLarge,
    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
  ) {
    Box(
      modifier =
        Modifier.fillMaxWidth()
          .background(
            Brush.linearGradient(
              colors =
                listOf(
                  accentColor.copy(alpha = 0.26f),
                  secondaryColor.copy(alpha = 0.20f),
                  MaterialTheme.colorScheme.surface,
                )
            )
          )
          .padding(20.dp)
    ) {
      Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceBetween,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
          ) {
            ProviderBadge(track.sourceProvider)
            Surface(
              color = accentColor.copy(alpha = 0.18f),
              shape = RoundedCornerShape(10.dp),
            ) {
              Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
              ) {
                Icon(
                  Icons.Filled.HighQuality,
                  contentDescription = null,
                  tint = accentColor,
                  modifier = Modifier.size(15.dp),
                )
                Text(
                  text =
                    "${track.bitDepth}-BIT / ${track.sampleRateKhz}kHz • $activeRegion",
                  style = MaterialTheme.typography.labelSmall,
                  color = MaterialTheme.colorScheme.onSurface,
                )
              }
            }
          }

          IconButton(
            onClick = onToggleDynamicWallpaper,
            modifier = Modifier.testTag("toggle_dynamic_color_mode"),
          ) {
            Icon(
              imageVector = Icons.Outlined.Palette,
              contentDescription = "Toggle Album Art vs System Dynamic Colors",
              tint = if (useSystemDynamic) MaterialTheme.colorScheme.tertiary else accentColor,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.spacedBy(18.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Box(
            modifier = Modifier.size(116.dp),
            contentAlignment = Alignment.Center,
          ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
              val radius = size.minDimension / 2f
              rotate(rotationAngle) {
                drawCircle(
                  brush =
                    Brush.sweepGradient(
                      listOf(
                        Color(0xFF0B0E17),
                        accentColor.copy(alpha = 0.55f),
                        Color(0xFF141A29),
                        secondaryColor.copy(alpha = 0.55f),
                        Color(0xFF0B0E17),
                      )
                    ),
                  radius = radius,
                )
                drawCircle(
                  color = Color.White.copy(alpha = 0.12f),
                  radius = radius * 0.78f,
                  style = Stroke(width = 1.5.dp.toPx()),
                )
                drawCircle(
                  color = Color.White.copy(alpha = 0.10f),
                  radius = radius * 0.58f,
                  style = Stroke(width = 1.dp.toPx()),
                )
                drawCircle(color = accentColor, radius = radius * 0.30f)
                drawCircle(color = Color(0xFF0A0D14), radius = radius * 0.09f)
              }
            }
          }

          Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp),
          ) {
            Text(
              text = track.title,
              style = MaterialTheme.typography.headlineMedium,
              color = MaterialTheme.colorScheme.onSurface,
              maxLines = 2,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = track.artist,
              style = MaterialTheme.typography.titleMedium,
              color = accentColor,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Text(
              text = track.album,
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
              overflow = TextOverflow.Ellipsis,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(8.dp),
              verticalAlignment = Alignment.CenterVertically,
            ) {
              Text(
                text =
                  if (bitPerfect) "USB DAC BIT-PERFECT DIRECT"
                  else "10-BAND BIQUAD DSP • ${String.format(Locale.US, "%.1f dBFS", rmsLevelDb)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.tertiary,
              )
            }
          }
        }

        LiveSpectrumVisualizer(
          bins = spectrumBins,
          accentColor = accentColor,
          secondaryColor = secondaryColor,
        )

        Column {
          val durationFloat = track.durationMs.coerceAtLeast(1L).toFloat()
          Slider(
            value = positionMs.toFloat().coerceIn(0f, durationFloat),
            onValueChange = { onSeekTo(it.toLong()) },
            valueRange = 0f..durationFloat,
            modifier = Modifier.fillMaxWidth().testTag("playback_seek_slider"),
            colors =
              SliderDefaults.colors(
                thumbColor = accentColor,
                activeTrackColor = accentColor,
              ),
          )
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = formatDurationMs(positionMs),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
              text = track.codec,
              style = MaterialTheme.typography.labelLarge,
              color = accentColor,
            )
            Text(
              text = formatDurationMs(track.durationMs),
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
        }

        Row(
          modifier = Modifier.fillMaxWidth(),
          horizontalArrangement = Arrangement.SpaceEvenly,
          verticalAlignment = Alignment.CenterVertically,
        ) {
          IconButton(
            onClick = onToggleOfflineDownload,
            modifier = Modifier.size(48.dp).testTag("hero_offline_pin_button"),
          ) {
            Icon(
              imageVector =
                if (track.isDownloadedOffline) Icons.Filled.DownloadForOffline
                else Icons.Outlined.Download,
              contentDescription = "Pin track for offline lossless playback",
              tint =
                if (track.isDownloadedOffline) accentColor
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }

          FilledIconButton(
            onClick = onSkipPrevious,
            modifier = Modifier.size(52.dp).testTag("skip_previous_button"),
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
              ),
          ) {
            Icon(
              Icons.Filled.FastRewind,
              contentDescription = "Previous Track",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }

          FilledIconButton(
            onClick = onTogglePlayPause,
            modifier = Modifier.size(68.dp).testTag("play_pause_button"),
            shape = RoundedCornerShape(24.dp),
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = accentColor,
                contentColor = Color(0xFF041318),
              ),
          ) {
            Icon(
              imageVector = if (isPlaying) Icons.Filled.Pause else Icons.Filled.PlayArrow,
              contentDescription = if (isPlaying) "Pause" else "Play",
              modifier = Modifier.size(34.dp),
            )
          }

          FilledIconButton(
            onClick = onSkipNext,
            modifier = Modifier.size(52.dp).testTag("skip_next_button"),
            colors =
              IconButtonDefaults.filledIconButtonColors(
                containerColor = MaterialTheme.colorScheme.surface
              ),
          ) {
            Icon(
              Icons.Filled.FastForward,
              contentDescription = "Next Track",
              tint = MaterialTheme.colorScheme.onSurface,
            )
          }

          Surface(
            shape = CircleShape,
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.7f),
            modifier = Modifier.size(48.dp),
          ) {
            Box(contentAlignment = Alignment.Center) {
              Icon(
                imageVector = Icons.Filled.CloudQueue,
                contentDescription = "Synced to Cloud",
                tint = accentColor,
                modifier = Modifier.size(20.dp),
              )
            }
          }
        }
      }
    }
  }
}

@Composable
private fun LiveSpectrumVisualizer(
  bins: List<Float>,
  accentColor: Color,
  secondaryColor: Color,
) {
  val animatedBins =
    bins.mapIndexed { idx, value ->
      val anim by
        animateFloatAsState(
          targetValue = value,
          animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
          label = "bin_$idx",
        )
      anim
    }

  Canvas(
    modifier =
      Modifier.fillMaxWidth()
        .height(46.dp)
        .clip(RoundedCornerShape(12.dp))
        .background(Color(0xFF080B12).copy(alpha = 0.65f))
        .padding(horizontal = 10.dp, vertical = 6.dp)
  ) {
    val count = animatedBins.size.coerceAtLeast(1)
    val spacing = 6.dp.toPx()
    val barWidth = ((size.width - spacing * (count - 1)) / count).coerceAtLeast(4f)
    animatedBins.forEachIndexed { index, magnitude ->
      val barHeight = (size.height * magnitude.coerceIn(0.1f, 1f))
      val left = index * (barWidth + spacing)
      val top = size.height - barHeight
      drawRoundRect(
        brush = Brush.verticalGradient(listOf(accentColor, secondaryColor)),
        topLeft = Offset(left, top),
        size = Size(barWidth, barHeight),
        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx()),
      )
    }
  }
}

@Composable
fun TrackRowCard(
  track: OfflineTrackEntity,
  isCurrent: Boolean,
  isPlaying: Boolean,
  onSelect: () -> Unit,
  onToggleOffline: () -> Unit,
) {
  val trackAccent = parseHexColor(track.accentHex, MaterialTheme.colorScheme.primary)
  Card(
    modifier =
      Modifier.fillMaxWidth()
        .clickable(onClick = onSelect)
        .testTag("track_card_${track.trackId}"),
    shape = MaterialTheme.shapes.medium,
    colors =
      CardDefaults.cardColors(
        containerColor =
          if (isCurrent) trackAccent.copy(alpha = 0.16f)
          else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f)
      ),
  ) {
    Row(
      modifier = Modifier.fillMaxWidth().padding(14.dp),
      verticalAlignment = Alignment.CenterVertically,
      horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
      Surface(
        modifier = Modifier.size(52.dp),
        shape = RoundedCornerShape(14.dp),
        color = trackAccent.copy(alpha = 0.24f),
      ) {
        Box(contentAlignment = Alignment.Center) {
          Icon(
            imageVector = if (isPlaying) Icons.Filled.GraphicEq else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = trackAccent,
            modifier = Modifier.size(26.dp),
          )
        }
      }

      Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
          Text(
            text = track.title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.SemiBold,
            color = if (isCurrent) trackAccent else MaterialTheme.colorScheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
          )
        }
        Text(
          text = "${track.artist} • ${track.album}",
          style = MaterialTheme.typography.bodyMedium,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 1,
          overflow = TextOverflow.Ellipsis,
        )
        Row(
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          ProviderBadge(track.sourceProvider)
          Text(
            text = "${track.bitDepth}-bit/${track.sampleRateKhz}kHz ${track.codec}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
          )
          AnimatedVisibility(visible = track.isDownloadedOffline) {
            Row(
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
              Icon(
                Icons.Filled.CheckCircle,
                contentDescription = "Offline Ready",
                tint = trackAccent,
                modifier = Modifier.size(13.dp),
              )
              Text(
                text = "OFFLINE",
                style = MaterialTheme.typography.labelSmall,
                color = trackAccent,
              )
            }
          }
        }
      }

      IconButton(
        onClick = onToggleOffline,
        modifier = Modifier.size(48.dp).testTag("offline_toggle_${track.trackId}"),
      ) {
        Icon(
          imageVector =
            if (track.isDownloadedOffline) Icons.Filled.DownloadForOffline
            else Icons.Outlined.Download,
          contentDescription = "Toggle Offline Storage",
          tint =
            if (track.isDownloadedOffline) trackAccent
            else MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
  }
}

@Composable
fun ProviderBadge(provider: String) {
  val (label, badgeColor) =
    when (provider) {
      "CUSTOM_API" -> "CUSTOM API" to CustomApiEmerald
      "SUBSONIC" -> "SUBSONIC" to SubsonicOrange
      "OPEN_GLOBAL" -> "GLOBAL OPEN" to GlobalSkyBlue
      "HYBRID" -> "MULTI-SOURCE" to Color(0xFFFF9F1C)
      else -> "AURA HI-FI" to Color(0xFF00F5D4)
    }
  Surface(
    color = badgeColor.copy(alpha = 0.18f),
    shape = RoundedCornerShape(6.dp),
  ) {
    Text(
      text = label,
      style = MaterialTheme.typography.labelSmall,
      color = badgeColor,
      modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
    )
  }
}

fun formatDurationMs(ms: Long): String {
  val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
  val minutes = totalSeconds / 60L
  val seconds = totalSeconds % 60L
  return String.format(Locale.US, "%d:%02d", minutes, seconds)
}
