package com.hotsteel.aurapulse.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ToggleButtonDefaults
import androidx.compose.material3.ButtonGroup
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.data.discovery.DiscoverySource
import com.hotsteel.aurapulse.data.discovery.RemoteTrack
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.PageHeader
import com.hotsteel.aurapulse.ui.components.SectionHeader

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun DiscoverScreen(vm: AuraViewModel, onOpenDownloads: () -> Unit) {
  val state by vm.discovery.collectAsStateWithLifecycle()
  val library by vm.tracks.collectAsStateWithLifecycle()
  var showLink by remember { mutableStateOf(false) }
  var showCustom by remember { mutableStateOf(false) }
  var showImport by remember { mutableStateOf(false) }
  val libraryIds = remember(library) { library.map { it.id }.toSet() }

  LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
    item("header") {
      PageHeader(
        eyebrow = stringResource(R.string.nav_discover),
        title = stringResource(R.string.nav_discover),
        subtitle = stringResource(R.string.discover_subtitle),
        trailing = {
          FilledTonalIconButton(onClick = onOpenDownloads, modifier = Modifier.size(46.dp)) {
            Icon(Icons.Default.CloudDownload, contentDescription = stringResource(R.string.downloads_title))
          }
        },
      )
    }

    item("sources") {
      SourceSelector(state.source) { vm.setDiscoverySource(it) }
      Spacer(Modifier.height(10.dp))
    }

    item("search") {
      Row(
        Modifier.fillMaxWidth().padding(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
      ) {
        OutlinedTextField(
          value = state.query,
          onValueChange = vm::setDiscoveryQuery,
          modifier = Modifier.weight(1f),
          label = { Text(stringResource(R.string.discover_search_hint, sourceLabel(state.source))) },
          singleLine = true,
          shape = MaterialTheme.shapes.extraLarge,
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
          keyboardActions = KeyboardActions(onSearch = { vm.searchDiscovery() }),
        )
        if (state.source == DiscoverySource.PREVIEWS || state.source == DiscoverySource.RADIO) {
          OutlinedTextField(
            value = state.country,
            onValueChange = vm::setCountry,
            modifier = Modifier.width(84.dp),
            label = { Text("CC") },
            singleLine = true,
            shape = MaterialTheme.shapes.extraLarge,
          )
        }
      }
      Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Button(onClick = vm::searchDiscovery) { Text(stringResource(R.string.discover_search)) }
        OutlinedButton(onClick = { showLink = true }) {
          Icon(Icons.Default.Link, null, Modifier.size(18.dp))
          Text(stringResource(R.string.add_direct_title), Modifier.padding(start = 6.dp))
        }
        OutlinedButton(onClick = { showImport = true }) {
          Icon(Icons.Default.Add, null, Modifier.size(18.dp))
          Text(stringResource(R.string.discover_import_playlist), Modifier.padding(start = 6.dp))
        }
      }
    }

    item("note") {
      val note = when (state.source) {
        DiscoverySource.PREVIEWS -> stringResource(R.string.preview_note)
        DiscoverySource.RADIO -> stringResource(R.string.radio_note)
        else -> stringResource(R.string.custom_api_help)
      }
      Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.medium,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
      ) {
        Text(
          note,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(14.dp),
        )
      }
    }

    if (state.source == DiscoverySource.RADIO) {
      item("tags") {
        SectionHeader(stringResource(R.string.radio_popular_tags))
        FlowRow(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp),
          horizontalArrangement = Arrangement.spacedBy(8.dp),
          verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
          listOf("jazz", "rock", "lofi", "classical", "news", "electronic", "ambient", "pop").forEach { tag ->
            AssistChip(onClick = { vm.browseRadioTag(tag) }, label = { Text(tag) })
          }
        }
      }
    }

    if (state.source == DiscoverySource.CUSTOM) {
      item("custom") {
        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp)) {
          Text(
            if (state.customName.isBlank()) stringResource(R.string.custom_api_title) else state.customName,
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.weight(1f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
          )
          IconButton(onClick = { showCustom = true }) { Icon(Icons.Default.Settings, contentDescription = stringResource(R.string.custom_api_title)) }
        }
      }
    }

    when {
      state.loading -> item("loading") {
        Row(Modifier.fillMaxWidth().padding(40.dp), horizontalArrangement = Arrangement.Center) {
          CircularProgressIndicator()
        }
      }
      state.error != null -> item("error") {
        EmptyState(title = stringResource(R.string.action_failed, ""), body = state.error.orEmpty())
      }
      state.searched && state.results.isEmpty() -> item("none") {
        EmptyState(title = stringResource(R.string.discover_search), body = stringResource(R.string.discover_empty_hint))
      }
      !state.searched -> item("hint") {
        EmptyState(title = stringResource(R.string.nav_discover), body = stringResource(R.string.discover_empty_hint))
      }
      else -> items(state.results, key = { it.streamUrl }) { remote ->
        RemoteRow(
          remote = remote,
          inLibrary = com.hotsteel.aurapulse.data.library.stableId("remote", remote.streamUrl) in libraryIds,
          onPlay = { vm.playRemote(remote, state.results) },
          onAdd = { vm.addRemote(remote) },
        )
      }
    }
  }

  if (showLink) {
    var title by remember { mutableStateOf("") }
    var url by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showLink = false },
      title = { Text(stringResource(R.string.add_direct_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = title, onValueChange = { title = it }, label = { Text(stringResource(R.string.add_direct_name)) }, singleLine = true)
          OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.add_direct_url)) }, singleLine = true)
          Text(stringResource(R.string.add_direct_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        TextButton(onClick = { if (vm.addDirectUrl(title, url)) showLink = false }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = { showLink = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }

  if (showCustom) {
    var name by remember { mutableStateOf(state.customName) }
    var template by remember { mutableStateOf(state.customTemplate) }
    AlertDialog(
      onDismissRequest = { showCustom = false },
      title = { Text(stringResource(R.string.custom_api_title)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text(stringResource(R.string.custom_api_name)) }, singleLine = true)
          OutlinedTextField(value = template, onValueChange = { template = it }, label = { Text(stringResource(R.string.custom_api_template)) }, singleLine = true)
          Text(stringResource(R.string.custom_api_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        TextButton(onClick = { if (vm.saveCustomApi(name, template)) showCustom = false }) { Text(stringResource(R.string.save)) }
      },
      dismissButton = { TextButton(onClick = { showCustom = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }

  if (showImport) {
    var url by remember { mutableStateOf("") }
    AlertDialog(
      onDismissRequest = { showImport = false },
      title = { Text(stringResource(R.string.discover_import_playlist)) },
      text = {
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
          OutlinedTextField(value = url, onValueChange = { url = it }, label = { Text(stringResource(R.string.playlist_url)) }, singleLine = true)
          Text(stringResource(R.string.custom_api_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      },
      confirmButton = {
        TextButton(onClick = { vm.importPlaylistFromUrl(url); showImport = false }) { Text(stringResource(R.string.library_import)) }
      },
      dismissButton = { TextButton(onClick = { showImport = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun SourceSelector(selected: DiscoverySource, onSelect: (DiscoverySource) -> Unit) {
  val sources = listOf(
    DiscoverySource.PREVIEWS to R.string.source_previews,
    DiscoverySource.RADIO to R.string.source_radio,
    DiscoverySource.CUSTOM to R.string.source_custom,
  )
  ButtonGroup(
    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
    horizontalArrangement = Arrangement.spacedBy(4.dp),
    overflowIndicator = { menu -> ButtonGroupDefaults.OverflowIndicator(menu) },
  ) {
    sources.forEach { (source, label) ->
      customItem(
        buttonGroupContent = {
          ToggleButton(
            checked = selected == source,
            onCheckedChange = { onSelect(source) },
            shapes = ToggleButtonDefaults.shapes(),
            modifier = Modifier.height(44.dp),
          ) { Text(stringResource(label), style = MaterialTheme.typography.labelLarge) }
        },
        menuContent = { menu ->
          androidx.compose.material3.DropdownMenuItem(
            text = { Text(stringResource(label)) },
            onClick = { menu.dismiss(); onSelect(source) },
          )
        },
      )
    }
  }
}

@Composable
private fun RemoteRow(
  remote: RemoteTrack,
  inLibrary: Boolean,
  onPlay: () -> Unit,
  onAdd: () -> Unit,
) {
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    Artwork(
      com.hotsteel.aurapulse.data.library.TrackEntity(
        id = remote.streamUrl, uri = remote.streamUrl, title = remote.title,
        artist = remote.artist, album = remote.album, artworkUri = remote.artworkUrl,
        source = remote.source, durationMs = remote.durationMs,
      ),
      54.dp, corner = 14.dp,
    )
    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
      Text(remote.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
      Text(
        listOf(remote.artist, remote.subtitle).filter { it.isNotBlank() }.joinToString(" · "),
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
    }
    IconButton(onClick = onAdd, enabled = !inLibrary) {
      Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_to_library))
    }
    FilledTonalIconButton(onClick = onPlay, modifier = Modifier.size(42.dp)) {
      Icon(Icons.Default.PlayArrow, contentDescription = stringResource(R.string.play))
    }
  }
}

@Composable
private fun sourceLabel(source: DiscoverySource): String = stringResource(
  when (source) {
    DiscoverySource.PREVIEWS -> R.string.source_previews
    DiscoverySource.RADIO -> R.string.source_radio
    DiscoverySource.CUSTOM -> R.string.source_custom
  },
)
