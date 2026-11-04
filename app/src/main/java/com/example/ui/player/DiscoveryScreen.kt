package com.example.ui.player

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.discovery.DiscoverySource

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoveryScreen(vm: MusicViewModel) {
  val state by vm.discoveryState.collectAsStateWithLifecycle()
  val library by vm.tracks.collectAsStateWithLifecycle()
  var directDialog by rememberSaveable { mutableStateOf(false) }
  var sourceDialog by rememberSaveable { mutableStateOf(false) }
  val savedIds = remember(library) { library.map { it.id }.toSet() }

  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      Modifier.widthIn(max = 820.dp).fillMaxWidth(),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 30.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item { PageHeader(stringResource(R.string.discover_eyebrow), stringResource(R.string.discover_title), stringResource(R.string.discover_subtitle)) }
      item {
        Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          listOf(DiscoverySource.PREVIEWS to R.string.source_previews_tab, DiscoverySource.RADIO to R.string.source_radio_tab,
            DiscoverySource.CUSTOM to R.string.source_custom_tab).forEach { (source, label) ->
            FilterChip(selected = state.source == source, onClick = { vm.setDiscoverySource(source) }, shapes = FilterChipDefaults.shapes(), label = { Text(stringResource(label)) })
          }
        }
      }
      item {
        Surface(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .65f), shape = RoundedCornerShape(18.dp)) {
          Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(when (state.source) {
              DiscoverySource.PREVIEWS -> R.string.preview_disclosure
              DiscoverySource.RADIO -> R.string.radio_disclosure
              DiscoverySource.CUSTOM -> R.string.custom_disclosure
            }), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (state.source == DiscoverySource.CUSTOM) {
              TextButton(onClick = { sourceDialog = true }) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                Text(if (state.customName.isBlank()) stringResource(R.string.configure_source) else state.customName,
                  modifier = Modifier.padding(start = 6.dp))
              }
            }
          }
        }
      }
      item {
        OutlinedTextField(
          value = state.query, onValueChange = vm::setDiscoveryQuery, modifier = Modifier.fillMaxWidth(), singleLine = true,
          label = { Text(stringResource(R.string.search_online_hint)) },
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search), keyboardActions = KeyboardActions(onSearch = { vm.search() }),
          shape = RoundedCornerShape(16.dp),
        )
      }
      item {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
          OutlinedTextField(
            value = state.country, onValueChange = vm::setCountry, modifier = Modifier.width(112.dp), singleLine = true,
            label = { Text(stringResource(R.string.country_code)) }, enabled = state.source != DiscoverySource.RADIO,
          )
          Button(onClick = vm::search, shapes = ButtonDefaults.shapes(), enabled = !state.loading, modifier = Modifier.weight(1f)) {
            if (state.loading) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            else Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp))
            Text(stringResource(if (state.loading) R.string.searching else R.string.search), modifier = Modifier.padding(start = 8.dp))
          }
        }
      }
      item {
        OutlinedButton(onClick = { directDialog = true }, modifier = Modifier.fillMaxWidth()) {
          Icon(Icons.Default.Link, contentDescription = null, modifier = Modifier.size(18.dp))
          Text(stringResource(R.string.add_direct_link), modifier = Modifier.padding(start = 8.dp))
        }
      }
      if (state.error != null) {
        item { Text(stringResource(R.string.search_failed, state.error.orEmpty()), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
      } else if (!state.loading && state.hasSearched && state.results.isEmpty()) {
        item { Text(stringResource(R.string.no_online_results), modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
      } else if (!state.hasSearched) {
        item { Text(stringResource(R.string.discover_hint), modifier = Modifier.padding(vertical = 24.dp), color = MaterialTheme.colorScheme.onSurfaceVariant) }
      } else if (state.results.isNotEmpty()) {
        item { SectionTitle(stringResource(R.string.results_title), stringResource(R.string.visible_track_count, state.results.size)) }
        items(state.results, key = { it.id }) { track ->
          TrackRow(track, onPlay = { vm.play(track, state.results) }) {
            AddTrackButton(onClick = { vm.addTrack(track) }, alreadyAdded = track.id in savedIds)
          }
        }
      }
    }
  }
  if (directDialog) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { directDialog = false }, title = { Text(stringResource(R.string.add_direct_link)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.direct_link_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.track_title_label)) }, singleLine = true)
          OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.audio_url_label)) }, singleLine = true)
          Text(stringResource(R.string.http_warning), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
      },
      confirmButton = { TextButton(onClick = { if (vm.addDirectUrl(title, url)) directDialog = false }) { Text(stringResource(R.string.add)) } },
      dismissButton = { TextButton(onClick = { directDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
  if (sourceDialog) {
    var name by remember { mutableStateOf(state.customName) }
    var template by remember { mutableStateOf(state.customTemplate) }
    AlertDialog(
      onDismissRequest = { sourceDialog = false }, title = { Text(stringResource(R.string.configure_source)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
          Text(stringResource(R.string.custom_source_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.source_name_label)) }, singleLine = true)
          OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text(stringResource(R.string.search_url_label)) }, maxLines = 3)
          Text(stringResource(R.string.custom_source_privacy), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.tertiary)
        }
      },
      confirmButton = { TextButton(onClick = { if (vm.saveCustomSource(name, template)) sourceDialog = false }) { Text(stringResource(R.string.save)) } },
      dismissButton = { TextButton(onClick = { sourceDialog = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}
