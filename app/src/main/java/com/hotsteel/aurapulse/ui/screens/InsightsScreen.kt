package com.hotsteel.aurapulse.ui.screens

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.PageHeader
import com.hotsteel.aurapulse.ui.components.SectionHeader
import java.util.Calendar
import kotlin.math.max

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun InsightsScreen(vm: AuraViewModel) {
  val insights by vm.insights.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  var confirming by remember { mutableStateOf(false) }

  LaunchedEffect(insights.plays, playback.track?.id) { vm.refreshInsights() }

  LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
    item("header") {
      PageHeader(
        eyebrow = stringResource(R.string.nav_insights),
        title = stringResource(R.string.nav_insights),
        subtitle = stringResource(R.string.insights_subtitle),
        trailing = {
          androidx.compose.material3.IconButton(onClick = { confirming = true }) {
            Icon(Icons.Default.DeleteSweep, contentDescription = stringResource(R.string.insights_clear))
          }
        },
      )
    }

    if (insights.plays == 0L) {
      item("empty") { EmptyState(title = stringResource(R.string.nav_insights), body = stringResource(R.string.insights_empty)) }
      return@LazyColumn
    }

    item("tiles") {
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InsightTile(
          value = if (insights.totalMs >= 3_600_000) stringResource(R.string.insights_hours_short, (insights.totalMs / 3_600_000).toInt())
          else stringResource(R.string.insights_minutes_short, (insights.totalMs / 60_000).toInt()),
          label = stringResource(R.string.insights_total_time),
          modifier = Modifier.weight(1f),
        )
        InsightTile(
          value = insights.plays.toString(),
          label = stringResource(R.string.insights_plays),
          modifier = Modifier.weight(1f),
        )
      }
      Spacer(Modifier.height(10.dp))
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        InsightTile(
          value = insights.distinctTracks.toString(),
          label = stringResource(R.string.insights_tracks),
          modifier = Modifier.weight(1f),
        )
        InsightTile(
          value = insights.streak.toString(),
          label = stringResource(R.string.insights_streak),
          modifier = Modifier.weight(1f),
        )
      }
    }

    item("week") {
      SectionHeader(stringResource(R.string.insights_week), subtitle = fmt.duration(insights.week.sum()))
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
      ) {
        Column(Modifier.padding(18.dp)) {
          WeekChart(insights.week)
          Spacer(Modifier.height(10.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            (0..6).forEach { index ->
              val label = Calendar.getInstance().apply {
                add(Calendar.DAY_OF_YEAR, -(6 - index))
              }.get(Calendar.DAY_OF_WEEK)
              Text(
                fmt.weekdayInitial((label + 5) % 7),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
          }
        }
      }
    }

    item("hours") {
      SectionHeader("Listening clock")
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 6.dp),
      ) {
        Column(Modifier.padding(18.dp)) {
          HourChart(insights.hours)
          Spacer(Modifier.height(6.dp))
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            listOf("00", "06", "12", "18", "23").forEach {
              Text(it, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
          }
        }
      }
    }

    if (insights.topTracks.isNotEmpty()) {
      item("top-title") { SectionHeader(stringResource(R.string.insights_top_tracks)) }
      items(insights.topTracks.size) { index ->
        val (track, stat) = insights.topTracks[index]
        Row(
          Modifier.fillMaxWidth().clickable { vm.play(track, insights.topTracks.map { it.first }) }
            .padding(horizontal = 20.dp, vertical = 7.dp),
          verticalAlignment = Alignment.CenterVertically,
        ) {
          Text(
            "${index + 1}",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(30.dp),
          )
          Artwork(track, 46.dp, corner = 12.dp)
          Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
            Text(track.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
              track.artist.ifBlank { "Unknown artist" },
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              maxLines = 1,
            )
          }
          Text("${stat.plays}×", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
    }

    if (insights.topArtists.isNotEmpty()) {
      item("artists-title") { SectionHeader(stringResource(R.string.insights_top_artists)) }
      item("artists") {
        Column(Modifier.padding(horizontal = 20.dp)) {
          val peak = max(1L, insights.topArtists.first().second)
          insights.topArtists.forEach { (artist, plays) ->
            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(artist, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(4.dp))
                Box(
                  Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(4.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                ) {
                  Box(
                    Modifier.fillMaxWidth(plays.toFloat() / peak).height(8.dp)
                      .clip(RoundedCornerShape(4.dp))
                      .background(Brush.horizontalGradient(listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.tertiary))),
                  )
                }
              }
              Text(
                plays.toString(),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 14.dp),
              )
            }
          }
        }
      }
    }
  }

  if (confirming) {
    AlertDialog(
      onDismissRequest = { confirming = false },
      title = { Text(stringResource(R.string.insights_clear_title)) },
      text = { Text(stringResource(R.string.insights_clear_body)) },
      confirmButton = {
        TextButton(onClick = { vm.clearListening(); confirming = false }) { Text(stringResource(R.string.delete)) }
      },
      dismissButton = { TextButton(onClick = { confirming = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun InsightTile(value: String, label: String, modifier: Modifier = Modifier) {
  Surface(
    modifier = modifier,
    shape = MaterialTheme.shapes.large,
    color = MaterialTheme.colorScheme.surfaceContainer,
  ) {
    Column(Modifier.padding(16.dp)) {
      Text(value, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
      Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
  }
}

@Composable
private fun WeekChart(values: List<Long>) {
  val peak = max(1L, values.maxOrNull() ?: 1L)
  val animation by animateFloatAsState(1f, label = "week")
  Canvas(Modifier.fillMaxWidth().height(120.dp)) {
    val slot = size.width / values.size
    val barWidth = slot * 0.52f
    values.forEachIndexed { index, value ->
      val ratio = (value.toFloat() / peak) * animation
      val height = (size.height - 8f) * ratio
      val left = index * slot + (slot - barWidth) / 2f
      drawRoundRect(
        brush = Brush.verticalGradient(
          listOf(
            Color(0xFF9BE7A8),
            Color(0xFF6FD4C4).copy(alpha = 0.65f),
          ),
        ),
        topLeft = Offset(left, size.height - height),
        size = Size(barWidth, height.coerceAtLeast(4f)),
        cornerRadius = CornerRadius(barWidth / 2f),
      )
    }
  }
}

@Composable
private fun HourChart(hours: List<Int>) {
  val peak = max(1, hours.maxOrNull() ?: 1)
  Canvas(Modifier.fillMaxWidth().height(64.dp)) {
    val slot = size.width / 24f
    hours.forEachIndexed { index, value ->
      val ratio = value.toFloat() / peak
      val height = (size.height - 6f) * ratio
      drawRoundRect(
        color = if (ratio > 0.6f) Color(0xFFFFC98A) else Color(0xFF9BE7A8).copy(alpha = 0.55f),
        topLeft = Offset(index * slot + slot * 0.15f, size.height - height),
        size = Size(slot * 0.7f, height.coerceAtLeast(3f)),
        cornerRadius = CornerRadius(slot * 0.35f),
      )
    }
  }
}
