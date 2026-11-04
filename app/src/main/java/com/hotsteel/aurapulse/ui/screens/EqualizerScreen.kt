package com.hotsteel.aurapulse.ui.screens

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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.playback.EffectState
import com.hotsteel.aurapulse.playback.EqPreset
import com.hotsteel.aurapulse.playback.EqualizerController
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun EqualizerScreen(vm: AuraViewModel, onBack: () -> Unit) {
  val state by vm.eq.collectAsStateWithLifecycle()
  var naming by remember { mutableStateOf(false) }
  var pendingDelete by remember { mutableStateOf<EqPreset?>(null) }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(stringResource(R.string.equalizer_title), onBack)
    when {
      !state.checked -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { LoadingIndicator() }
      !state.available -> EmptyState(
        title = stringResource(R.string.equalizer_title),
        body = stringResource(R.string.equalizer_unavailable),
      )
      else -> LazyColumn(
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
      ) {
        item {
          Surface(
            color = MaterialTheme.colorScheme.surfaceContainer,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
          ) {
            Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
              Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.equalizer_enable), style = MaterialTheme.typography.titleMedium)
                Text(
                  stringResource(R.string.effect_strength) + " · " + EqualizerController.bandLabel(1000),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
              Switch(checked = state.enabled, onCheckedChange = vm::setEqEnabled)
            }
          }
        }

        item { SectionHeader(stringResource(R.string.equalizer_bands)) }

        item {
          val bands = state.bands
          if (bands.isEmpty()) {
            Text(
              stringResource(R.string.equalizer_unavailable),
              style = MaterialTheme.typography.bodySmall,
              color = MaterialTheme.colorScheme.onSurfaceVariant,
              modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
          } else {
            Row(
              Modifier.fillMaxWidth().padding(horizontal = 8.dp),
              horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
              bands.forEach { band ->
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                  Text(
                    "${band.levelMb / 100}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                  )
                  Box(
                    Modifier.height(168.dp).width(44.dp),
                    contentAlignment = Alignment.Center,
                  ) {
                    Slider(
                      value = band.levelMb.toFloat(),
                      onValueChange = { vm.setEqBand(band.index, it.toInt()) },
                      valueRange = state.minimumMb.toFloat()..state.maximumMb.toFloat(),
                      enabled = state.enabled,
                      modifier = Modifier
                        .width(168.dp)
                        .graphicsLayer { rotationZ = -90f },
                    )
                  }
                  Text(
                    EqualizerController.bandLabel(band.frequencyHz),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                  )
                }
              }
            }
          }
        }

        item {
          Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            TextButton(onClick = vm::resetEq) { Text(stringResource(R.string.reset_sound_controls)) }
            Spacer(Modifier.weight(1f))
            FilledTonalButton(onClick = { naming = true }) { Text(stringResource(R.string.save_preset)) }
          }
        }

        item { SectionHeader(stringResource(R.string.speed_presets)) }

        item {
          LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
          ) {
            items(EqualizerController.builtIn, key = { it.id }) { preset ->
              PresetChip(
                label = preset.name ?: preset.nameRes?.let { stringResource(it) } ?: preset.id,
                selected = state.presetId == preset.id,
                onClick = { vm.applyPreset(preset) },
              )
            }
          }
        }

        if (state.customPresets.isNotEmpty()) {
          item { SectionHeader(stringResource(R.string.custom_presets)) }
          item {
            LazyRow(
              contentPadding = PaddingValues(horizontal = 16.dp),
              horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
              items(state.customPresets, key = { it.id }) { preset ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                  PresetChip(
                    label = preset.name ?: preset.id,
                    selected = state.presetId == preset.id,
                    onClick = { vm.applyPreset(preset) },
                  )
                  IconButton(onClick = { pendingDelete = preset }, modifier = Modifier.size(36.dp)) {
                    Icon(
                      Icons.Default.Close,
                      contentDescription = stringResource(R.string.delete_preset),
                      modifier = Modifier.size(16.dp),
                    )
                  }
                }
              }
            }
          }
        }

        item { SectionHeader(stringResource(R.string.sound_controls)) }

        item {
          Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            EffectCard(
              title = stringResource(R.string.bass_boost),
              state = state.bass,
              onChange = { enabled, strength -> vm.setBass(enabled, strength) },
            )
            EffectCard(
              title = stringResource(R.string.virtualizer),
              state = state.virtualizer,
              onChange = { enabled, strength -> vm.setVirtualizer(enabled, strength) },
            )
            EffectCard(
              title = stringResource(R.string.loudness),
              state = state.loudness,
              range = 0..1500,
              onChange = { enabled, strength -> vm.setLoudness(enabled, strength) },
            )
          }
        }
      }
    }
  }

  if (naming) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { naming = false },
      title = { Text(stringResource(R.string.save_preset)) },
      text = {
        OutlinedTextField(
          value = name,
          onValueChange = { name = it.take(40) },
          label = { Text(stringResource(R.string.preset_name)) },
          singleLine = true,
        )
      },
      confirmButton = {
        TextButton(
          onClick = {
            if (name.isNotBlank()) vm.savePreset(name)
            naming = false
          },
        ) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = { naming = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }

  pendingDelete?.let { preset ->
    AlertDialog(
      onDismissRequest = { pendingDelete = null },
      title = { Text(stringResource(R.string.delete_preset)) },
      text = { Text(preset.name ?: preset.id) },
      confirmButton = {
        TextButton(onClick = { vm.deletePreset(preset.id); pendingDelete = null }) {
          Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error)
        }
      },
      dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun PresetChip(
  label: String,
  selected: Boolean,
  onClick: () -> Unit,
) {
  Surface(
    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceContainerHigh,
    contentColor = if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
    shape = CircleShape,
    modifier = Modifier.clip(CircleShape).clickable(onClick = onClick),
  ) {
    Text(
      label,
      style = MaterialTheme.typography.labelLarge,
      maxLines = 1,
      modifier = Modifier.padding(horizontal = 16.dp, vertical = 9.dp),
    )
  }
}

@Composable
private fun EffectCard(
  title: String,
  state: EffectState,
  range: IntRange = 0..1000,
  onChange: (Boolean, Int) -> Unit,
) {
  val supported = state.supported
  Surface(
    color = MaterialTheme.colorScheme.surfaceContainer,
    shape = MaterialTheme.shapes.large,
    modifier = Modifier.fillMaxWidth(),
  ) {
    Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
      Row(verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
          Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = if (supported) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant,
          )
          if (!supported) {
            Text(
              stringResource(R.string.effect_unsupported),
              style = MaterialTheme.typography.labelSmall,
              color = MaterialTheme.colorScheme.error,
            )
          }
        }
        Switch(
          checked = supported && state.enabled,
          enabled = supported,
          onCheckedChange = { onChange(it, state.strength) },
        )
      }
      if (supported) {
        Row(verticalAlignment = Alignment.CenterVertically) {
          Text(
            stringResource(R.string.effect_strength),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(70.dp),
          )
          Slider(
            value = state.strength.toFloat(),
            onValueChange = { onChange(state.enabled, it.toInt()) },
            valueRange = range.first.toFloat()..range.last.toFloat(),
            enabled = state.enabled,
            modifier = Modifier.weight(1f),
          )
          Text(
            "${state.strength}",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            textAlign = TextAlign.End,
            modifier = Modifier.width(44.dp),
          )
        }
      }
    }
  }
}
