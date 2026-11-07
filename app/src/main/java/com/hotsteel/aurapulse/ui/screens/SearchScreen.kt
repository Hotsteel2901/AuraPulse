package com.hotsteel.aurapulse.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AssistChip
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.SectionHeader
import com.hotsteel.aurapulse.ui.components.TrackRow

@OptIn(ExperimentalMaterial3ExpressiveApi::class, ExperimentalComposeUiApi::class)
@Composable
fun SearchScreen(
  vm: AuraViewModel,
  onBack: () -> Unit,
  onOpenAlbum: (String) -> Unit,
  onOpenArtist: (String) -> Unit,
) {
  val query by vm.searchQuery.collectAsStateWithLifecycle()
  val results by vm.searchResults.collectAsStateWithLifecycle()
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  val keyboard = LocalSoftwareKeyboardController.current
  val focus = remember { FocusRequester() }
  val albums = remember(results) { albumGroups(results).take(4) }
  val artists = remember(results) { artistGroups(results).take(4) }

  LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }

  Column(Modifier.fillMaxSize()) {
    Row(
      Modifier.fillMaxWidth().padding(start = 4.dp, end = 12.dp, top = 4.dp),
      verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
    ) {
      IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back)) }
      OutlinedTextField(
        value = query,
        onValueChange = vm::setSearchQuery,
        modifier = Modifier.weight(1f).focusRequester(focus),
        placeholder = { Text(stringResource(R.string.search_hint)) },
        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
        trailingIcon = {
          if (query.isNotEmpty()) {
            IconButton(onClick = { vm.setSearchQuery("") }) { Icon(Icons.Default.Close, contentDescription = stringResource(R.string.close)) }
          }
        },
        singleLine = true,
        shape = MaterialTheme.shapes.extraLarge,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { vm.submitSearch(query); keyboard?.hide() }),
      )
    }

    if (query.isBlank()) {
      if (settings.searchHistory.isNotEmpty()) {
        Row(
          Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp),
          verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
        ) {
          Text(stringResource(R.string.search_recent), style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
          IconButton(onClick = vm::clearSearchHistory) {
            Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.search_clear_history), modifier = Modifier.size(18.dp))
          }
        }
        LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
          items(settings.searchHistory) { term ->
            AssistChip(
              onClick = { vm.setSearchQuery(term); vm.submitSearch(term) },
              label = { Text(term) },
            )
          }
        }
        Spacer(Modifier.height(8.dp))
      }
      EmptyState(title = stringResource(R.string.search_title), body = stringResource(R.string.search_empty))
      return@Column
    }

    if (results.isEmpty()) {
      EmptyState(title = stringResource(R.string.search_no_results, query), body = stringResource(R.string.empty_library_body))
      return@Column
    }

    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
      if (albums.isNotEmpty()) {
        item("albums") {
          SectionHeader(stringResource(R.string.section_albums))
          LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.key }) { album ->
              Column(Modifier.width(120.dp).clickable { onOpenAlbum(album.key) }.padding(vertical = 4.dp)) {
                com.hotsteel.aurapulse.ui.components.Artwork(album.tracks.firstOrNull(), 120.dp, corner = 18.dp)
                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
                Text(
                  album.artist,
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                  maxLines = 1,
                )
              }
            }
          }
        }
      }
      if (artists.isNotEmpty()) {
        item("artists") {
          SectionHeader(stringResource(R.string.section_artists))
          LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            items(artists, key = { it.name }) { artist ->
              AssistChip(onClick = { onOpenArtist(artist.name) }, label = { Text(artist.name) })
            }
          }
        }
      }
      item("tracks-title") { SectionHeader(stringResource(R.string.section_tracks), subtitle = stringResource(R.string.library_tracks_count, results.size)) }
      items(results, key = { it.id }) { track: TrackEntity ->
        TrackRow(
          track = track,
          vm = vm,
          playing = playback.track?.id == track.id,
          onClick = { vm.play(track, results) },
        )
      }
    }
  }
}
