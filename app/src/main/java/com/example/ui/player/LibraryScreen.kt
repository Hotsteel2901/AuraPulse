package com.example.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.library.TrackSource

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LibraryScreen(vm: MusicViewModel, importing: Boolean, onPickFiles: () -> Unit, onPickFolder: () -> Unit, onDiscover: () -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playlists by vm.playlists.collectAsStateWithLifecycle()
  val downloads by vm.downloadState.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val history by vm.history.collectAsStateWithLifecycle()
  val resume by vm.resume.collectAsStateWithLifecycle()
  var query by rememberSaveable { mutableStateOf("") }
  var filter by rememberSaveable { mutableStateOf("all") }
  var sort by rememberSaveable { mutableStateOf("recent") }
  var sortMenu by remember { mutableStateOf(false) }
  val visible = remember(tracks, query, filter, favorites, history, sort) {
    val historyOrder = history.withIndex().associate { it.value to it.index }
    val filtered = tracks.filter { track ->
      val matchesFilter = when (filter) {
        "files" -> track.source == TrackSource.LOCAL
        "online" -> track.source != TrackSource.LOCAL
        "favorites" -> track.id in favorites
        "downloads" -> track.downloadedPath != null
        "history" -> track.id in historyOrder
        else -> true
      }
      matchesFilter && (query.isBlank() || listOf(track.title, track.artist, track.album).any { it.contains(query.trim(), ignoreCase = true) })
    }
    when {
      sort == "title" -> filtered.sortedBy { it.title.lowercase() }
      sort == "artist" -> filtered.sortedWith(compareBy({ it.artist.lowercase() }, { it.title.lowercase() }))
      sort == "duration" -> filtered.sortedByDescending { it.durationMs }
      filter == "history" -> filtered.sortedBy { historyOrder[it.id] ?: Int.MAX_VALUE }
      else -> filtered
    }
  }
  val resumeTrack = remember(tracks, resume) { tracks.find { it.id == resume?.trackId } }
  Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
    LazyColumn(
      modifier = Modifier.fillMaxWidth().widthIn(max = 820.dp),
      contentPadding = PaddingValues(start = 20.dp, end = 20.dp, top = 28.dp, bottom = 30.dp),
      verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
      item {
        PageHeader(stringResource(R.string.library_eyebrow), stringResource(R.string.library_title), stringResource(R.string.library_subtitle, tracks.size))
      }
      item {
        Column(
          Modifier.fillMaxWidth().background(
            Brush.linearGradient(listOf(Color(0xFF304C38), Color(0xFF1C332A), Color(0xFF24352C))), RoundedCornerShape(24.dp)
          ).padding(22.dp), verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
          Text(stringResource(R.string.hero_kicker).uppercase(), style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
          Text(stringResource(R.string.hero_title), style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
          Text(stringResource(R.string.hero_body), style = MaterialTheme.typography.bodyMedium, color = Color(0xFFD0E0D0))
          Spacer(Modifier.height(4.dp))
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Button(onClick = onPickFiles, shapes = ButtonDefaults.shapes(), enabled = !importing) {
              if (importing) CircularProgressIndicator(Modifier.size(17.dp), strokeWidth = 2.dp)
              else Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(18.dp))
              Text(stringResource(if (importing) R.string.importing else R.string.import_files), modifier = Modifier.padding(start = 7.dp), maxLines = 1)
            }
            OutlinedButton(onClick = onPickFolder, shapes = ButtonDefaults.shapes(), enabled = !importing) {
              Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
              Text(stringResource(R.string.import_folder), modifier = Modifier.padding(start = 6.dp), maxLines = 1)
            }
          }
        }
      }
      if (resumeTrack != null && resume != null) {
        item {
          Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
              TrackArtwork(resumeTrack, 44.dp)
              Column(Modifier.weight(1f)) {
                Text(stringResource(R.string.resume_listening), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                Text(resumeTrack.title, style = MaterialTheme.typography.titleSmall, maxLines = 1)
              }
              TextButton(onClick = vm::resumeLast) {
                Icon(Icons.Default.PlayArrow, null, Modifier.size(18.dp))
                Text(formatTime(resume?.positionMs ?: 0L), Modifier.padding(start = 4.dp))
              }
            }
          }
        }
      }
      item {
        OutlinedTextField(
          value = query, onValueChange = { query = it }, modifier = Modifier.fillMaxWidth(), singleLine = true,
          leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
          placeholder = { Text(stringResource(R.string.search_library)) }, shape = RoundedCornerShape(16.dp),
        )
      }
      item {
        Column {
          FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            listOf("all" to R.string.filter_all, "files" to R.string.filter_files, "online" to R.string.filter_online,
              "favorites" to R.string.filter_favorites, "downloads" to R.string.filter_downloads, "history" to R.string.filter_history).forEach { (id, label) ->
              FilterChip(selected = filter == id, onClick = { filter = id }, shapes = FilterChipDefaults.shapes(), label = { Text(stringResource(label)) })
            }
          }
          Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Box {
              TextButton(onClick = { sortMenu = true }) {
                Icon(Icons.AutoMirrored.Filled.Sort, null, Modifier.size(18.dp))
                Text(stringResource(R.string.sort_by), Modifier.padding(start = 6.dp))
              }
              DropdownMenu(expanded = sortMenu, onDismissRequest = { sortMenu = false }) {
                listOf("recent" to R.string.sort_recent, "title" to R.string.sort_title, "artist" to R.string.sort_artist,
                  "duration" to R.string.sort_duration).forEach { (id, label) ->
                  DropdownMenuItem(text = { Text(stringResource(label)) }, onClick = { sort = id; sortMenu = false })
                }
              }
            }
            if (filter == "history" && history.isNotEmpty()) TextButton(onClick = vm::clearHistory) { Text(stringResource(R.string.clear_history)) }
          }
          if (filter == "history") Text(stringResource(R.string.history_local_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
      }
      if (visible.isEmpty()) {
        item {
          Column(Modifier.fillMaxWidth().padding(vertical = 28.dp), horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Default.AudioFile, contentDescription = null, modifier = Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(if (tracks.isEmpty()) R.string.empty_library_title else R.string.empty_search),
              style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
            if (tracks.isEmpty()) {
              Text(stringResource(R.string.empty_library_body), textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
              TextButton(onClick = onDiscover) { Text(stringResource(R.string.explore_sources)) }
            }
          }
        }
      } else {
        item { SectionTitle(stringResource(R.string.all_tracks), stringResource(R.string.visible_track_count, visible.size)) }
        items(visible, key = { it.id }) { track ->
          TrackRow(track = track, onPlay = { vm.play(track, visible) }, progress = downloads[track.id]) {
            LibraryTrackActions(track, playlists, downloads[track.id], vm)
          }
        }
      }
    }
  }
}
