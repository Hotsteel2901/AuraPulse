package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.example.audio.HiFiAudioEngine
import com.example.data.model.SoundProfile
import com.example.ui.i18n.LocalAuraStrings
import com.example.ui.viewmodel.PlayerUiState
import com.example.ui.viewmodel.UiState
import java.util.Locale

@Composable
fun EqualizerScreen(
  playerState: PlayerUiState,
  soundProfilesState: UiState<List<SoundProfile>>,
  onUpdateBand: (Int, Long) -> Unit,
  onUpdatePreamp: (Long) -> Unit,
  onUpdateBassBoost: (Long) -> Unit,
  onUpdateSpatializer: (Long) -> Unit,
  onToggleBitPerfect: () -> Unit,
  onSelectPresetCategory: (String) -> Unit,
  onSaveSoundProfile: (String) -> Unit,
  onLoadSoundProfile: (SoundProfile) -> Unit,
  onDeleteSoundProfile: (String) -> Unit,
  onBackToDeck: () -> Unit,
) {
  BackHandler { onBackToDeck() }
  val strings = LocalAuraStrings.current
  var showSaveDialog by remember { mutableStateOf(false) }

  val presets =
    listOf(
      "HARMAN_TARGET" to strings.presetHarman,
      "FLAT_STUDIO" to strings.presetFlat,
      "WARM_TUBE" to strings.presetWarmTube,
      "BASS_SURGE" to strings.presetBassSurge,
      "VOCAL_CLARITY" to strings.presetVocal,
      "CUSTOM" to strings.presetCustom,
    )

  LazyColumn(
    modifier = Modifier.fillMaxSize().testTag("equalizer_lazy_column"),
    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
    verticalArrangement = Arrangement.spacedBy(16.dp),
  ) {
    item {
      Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
      ) {
        Column(modifier = Modifier.weight(1f)) {
          Text(
            text = strings.eqTitle,
            style = MaterialTheme.typography.headlineMedium,
          )
          Text(
            text = strings.eqSubtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
        }
        Button(
          onClick = { showSaveDialog = true },
          modifier = Modifier.testTag("save_eq_profile_button"),
        ) {
          Icon(Icons.Filled.Save, contentDescription = null)
          Spacer(modifier = Modifier.width(6.dp))
          Text(strings.saveButton)
        }
      }
    }

    // Spline Frequency Response Curve Canvas
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
          ) {
            Text(
              text = strings.splineHeader,
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.primary,
            )
            Text(
              text =
                if (playerState.dacBitPerfectMode) strings.bypassedBitPerfect
                else "PREAMP ${formatTenthsDb(playerState.preampTenthsDb)}",
              style = MaterialTheme.typography.labelLarge,
              color = MaterialTheme.colorScheme.tertiary,
            )
          }

          FrequencyResponseCanvas(
            bandsTenthsDb = playerState.bandsTenthsDb,
            bitPerfect = playerState.dacBitPerfectMode,
            primaryColor = MaterialTheme.colorScheme.primary,
            secondaryColor = MaterialTheme.colorScheme.secondary,
          )

          LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            items(presets) { (key, label) ->
              FilterChip(
                selected = playerState.activePresetCategory == key,
                onClick = { onSelectPresetCategory(key) },
                label = { Text(label) },
                modifier = Modifier.testTag("eq_preset_$key"),
              )
            }
          }
        }
      }
    }

    // USB DAC Bit-Perfect & Spatial DSP Controls
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
          ) {
            Column(modifier = Modifier.weight(1f)) {
              Text(
                text = strings.bitPerfectTitle,
                style = MaterialTheme.typography.titleMedium,
              )
              Text(
                text = strings.bitPerfectSubtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            Switch(
              checked = playerState.dacBitPerfectMode,
              onCheckedChange = { onToggleBitPerfect() },
              modifier = Modifier.testTag("bit_perfect_switch"),
            )
          }

          DspSliderRow(
            label = strings.preampGainLabel,
            valueText = formatTenthsDb(playerState.preampTenthsDb),
            value = playerState.preampTenthsDb.toFloat(),
            range = -120f..120f,
            enabled = !playerState.dacBitPerfectMode,
            onValueChange = { onUpdatePreamp(it.toLong()) },
          )

          DspSliderRow(
            label = strings.bassBoostLabel,
            valueText = "${playerState.bassBoostPercent}%",
            value = playerState.bassBoostPercent.toFloat(),
            range = 0f..100f,
            enabled = !playerState.dacBitPerfectMode,
            onValueChange = { onUpdateBassBoost(it.toLong()) },
          )

          DspSliderRow(
            label = strings.spatializerLabel,
            valueText = "${playerState.spatializerStrength}%",
            value = playerState.spatializerStrength.toFloat(),
            range = 0f..100f,
            enabled = !playerState.dacBitPerfectMode,
            onValueChange = { onUpdateSpatializer(it.toLong()) },
          )
        }
      }
    }

    // 10-Band Sliders
    item {
      Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
      ) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
          Text(
            text = strings.octaveFiltersHeader,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
          )
          HiFiAudioEngine.EQ_FREQUENCIES_LABELS.forEachIndexed { index, freqLabel ->
            val currentVal = playerState.bandsTenthsDb.getOrElse(index) { 0L }
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically,
              horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
              Text(
                text = freqLabel,
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.width(48.dp),
              )
              Slider(
                value = currentVal.toFloat(),
                onValueChange = { onUpdateBand(index, it.toLong()) },
                valueRange = -120f..120f,
                enabled = !playerState.dacBitPerfectMode,
                modifier = Modifier.weight(1f).testTag("eq_band_slider_$index"),
              )
              Text(
                text = formatTenthsDb(currentVal),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.width(64.dp),
              )
            }
          }
        }
      }
    }

    // Cloud Synced Sound Profiles
    item {
      Text(
        text = "Cloud-Synced Sound Profiles",
        style = MaterialTheme.typography.titleLarge,
      )
    }

    val savedProfiles = (soundProfilesState as? UiState.Success)?.data ?: emptyList()
    items(savedProfiles, key = { it.id }) { profile ->
      Card(
        modifier =
          Modifier.fillMaxWidth()
            .clickable { onLoadSoundProfile(profile) }
            .testTag("sound_profile_card_${profile.id}"),
        colors =
          CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)
          ),
      ) {
        Row(
          modifier = Modifier.fillMaxWidth().padding(14.dp),
          verticalAlignment = Alignment.CenterVertically,
          horizontalArrangement = Arrangement.SpaceBetween,
        ) {
          Column(modifier = Modifier.weight(1f)) {
            Text(text = profile.name, style = MaterialTheme.typography.titleMedium)
            Text(
              text =
                "${profile.presetCategory} • Bass ${profile.bassBoostPercent}% • Stage ${profile.spatializerStrength}%",
              style = MaterialTheme.typography.bodyMedium,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
          }
          IconButton(onClick = { onDeleteSoundProfile(profile.id) }) {
            Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete Sound Profile")
          }
        }
      }
    }
  }

  if (showSaveDialog) {
    var profileName by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showSaveDialog = false },
      title = { Text("Save Personalized Sound Profile") },
      text = {
        OutlinedTextField(
          value = profileName,
          onValueChange = { profileName = it },
          label = { Text("Profile Name (e.g., Sennheiser HD800S Warm)") },
          singleLine = true,
          modifier = Modifier.fillMaxWidth().testTag("eq_profile_name_input"),
        )
      },
      confirmButton = {
        Button(
          onClick = {
            onSaveSoundProfile(profileName.ifBlank { "Custom Audiophile Curve" })
            showSaveDialog = false
          },
          modifier = Modifier.testTag("confirm_save_eq_profile_button"),
        ) {
          Text("Save to Cloud")
        }
      },
      dismissButton = {
        TextButton(onClick = { showSaveDialog = false }) { Text("Cancel") }
      },
    )
  }
}

@Composable
private fun FrequencyResponseCanvas(
  bandsTenthsDb: List<Long>,
  bitPerfect: Boolean,
  primaryColor: Color,
  secondaryColor: Color,
) {
  Canvas(
    modifier =
      Modifier.fillMaxWidth()
        .height(120.dp)
        .clip(RoundedCornerShape(14.dp))
        .background(Color(0xFF070A10))
        .padding(12.dp)
  ) {
    val midY = size.height / 2f
    // Zero dB reference line
    drawLine(
      color = Color.White.copy(alpha = 0.15f),
      start = Offset(0f, midY),
      end = Offset(size.width, midY),
      strokeWidth = 1.dp.toPx(),
    )

    val points =
      bandsTenthsDb.mapIndexed { idx, tenths ->
        val x = (idx.toFloat() / (bandsTenthsDb.size - 1).coerceAtLeast(1)) * size.width
        val normalized = if (bitPerfect) 0f else (tenths.toFloat() / 120f).coerceIn(-1f, 1f)
        val y = midY - normalized * (size.height * 0.42f)
        Offset(x, y)
      }

    if (points.size >= 2) {
      val path = Path().apply {
        moveTo(points.first().x, points.first().y)
        for (i in 0 until points.size - 1) {
          val p0 = points[i]
          val p1 = points[i + 1]
          val controlX = (p0.x + p1.x) / 2f
          cubicTo(controlX, p0.y, controlX, p1.y, p1.x, p1.y)
        }
      }
      drawPath(
        path = path,
        brush = Brush.horizontalGradient(listOf(primaryColor, secondaryColor)),
        style = Stroke(width = 3.dp.toPx()),
      )
      points.forEach { pt ->
        drawCircle(color = primaryColor, radius = 4.dp.toPx(), center = pt)
      }
    }
  }
}

@Composable
private fun DspSliderRow(
  label: String,
  valueText: String,
  value: Float,
  range: ClosedFloatingPointRange<Float>,
  enabled: Boolean,
  onValueChange: (Float) -> Unit,
) {
  Column {
    Row(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement = Arrangement.SpaceBetween,
    ) {
      Text(text = label, style = MaterialTheme.typography.bodyMedium)
      Text(
        text = valueText,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
      )
    }
    Slider(
      value = value,
      onValueChange = onValueChange,
      valueRange = range,
      enabled = enabled,
      modifier = Modifier.fillMaxWidth(),
    )
  }
}

private fun formatTenthsDb(tenths: Long): String {
  val db = tenths / 10.0
  val sign = if (db > 0) "+" else ""
  return String.format(Locale.US, "%s%.1f dB", sign, db)
}
