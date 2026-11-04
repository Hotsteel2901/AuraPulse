package com.hotsteel.aurapulse.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.library.Source
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.SectionHeader

/**
 * Downloads are real private copies of remote audio stored in app storage, so the
 * screen shows byte progress, the on-disk total and a way to reclaim space.
 */
@Composable
fun DownloadsScreen(vm: AuraViewModel, onBack: () -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  var confirmingClear by remember { mutableStateOf(false) }

  val finished = tracks.filter { it.downloadedPath != null }
  val active = downloads.keys.mapNotNull { id -> tracks.firstOrNull { it.id == id } }
  val totalBytes = finished.sumOf { it.sizeBytes }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(stringResource(R.string.downloads_title), onBack)
    if (finished.isEmpty() && active.isEmpty()) {
      EmptyState(
        title = stringResource(R.string.downloads_empty_title),
        body = stringResource(R.string.downloads_empty_body),
      )
      return@Column
    }
    LazyColumn(
      contentPadding = PaddingValues(bottom = 28.dp),
      verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
      item {
        Surface(
          color = MaterialTheme.colorScheme.surfaceContainer,
          shape = MaterialTheme.shapes.large,
          modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
          Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
              Text(
                fmt.bytes(totalBytes),
                style = MaterialTheme.typography.headlineSmallEmphasized,
                color = MaterialTheme.colorScheme.onSurface,
              )
              Text(
                stringResource(R.string.downloads_total) + " · " + finished.size,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
              )
            }
            TextButton(onClick = { confirmingClear = true }) { Text(stringResource(R.string.downloads_clear)) }
          }
        }
      }

      if (active.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.downloads_active)) }
        items(active, key = { it.id }) { track ->
          val progress = downloads[track.id]
          DownloadRow(
            track = track,
            progressBytes = progress?.bytes ?: 0,
            totalBytes = progress?.total ?: track.sizeBytes.takeIf { it > 0 },
            onCancel = { vm.cancelDownload(track.id) },
          )
        }
      }

      if (finished.isNotEmpty()) {
        item { SectionHeader(stringResource(R.string.downloaded)) }
        items(finished, key = { it.id }) { track ->
          DownloadRow(
            track = track,
            progressBytes = -1,
            totalBytes = track.sizeBytes.takeIf { it > 0 },
            onRemove = { vm.removeDownload(track) },
          )
        }
      }
    }
  }

  if (confirmingClear) {
    androidx.compose.material3.AlertDialog(
      onDismissRequest = { confirmingClear = false },
      title = { Text(stringResource(R.string.downloads_clear)) },
      text = { Text(stringResource(R.string.downloads_clear_body) + "\n" + fmt.bytes(totalBytes)) },
      confirmButton = {
        TextButton(
          onClick = {
            finished.forEach { vm.removeDownload(it) }
            confirmingClear = false
          },
        ) { Text(stringResource(R.string.delete), color = MaterialTheme.colorScheme.error) }
      },
      dismissButton = { TextButton(onClick = { confirmingClear = false }) { Text(stringResource(R.string.cancel)) } },
    )
  }
}

@Composable
private fun DownloadRow(
  track: com.hotsteel.aurapulse.data.library.TrackEntity,
  progressBytes: Long,
  totalBytes: Long?,
  onCancel: (() -> Unit)? = null,
  onRemove: (() -> Unit)? = null,
) {
  val downloading = progressBytes >= 0
  Row(
    Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp),
    verticalAlignment = Alignment.CenterVertically,
    horizontalArrangement = Arrangement.spacedBy(12.dp),
  ) {
    Artwork(track, 52.dp, corner = 14.dp)
    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
      Text(
        track.title.ifBlank { "Untitled" },
        style = MaterialTheme.typography.titleSmall,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      Text(
        track.artist.ifBlank { stringResource(R.string.unknown_artist) },
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
      )
      if (downloading) {
        val fraction = totalBytes?.takeIf { it > 0 }?.let { (progressBytes.toFloat() / it).coerceIn(0f, 1f) }
        if (fraction != null) {
          LinearProgressIndicator(progress = { fraction }, modifier = Modifier.fillMaxWidth().height(4.dp))
        } else {
          LinearProgressIndicator(Modifier.fillMaxWidth().height(4.dp))
        }
        Text(
          if (totalBytes != null && totalBytes > 0) {
            fmt.bytes(progressBytes) + " / " + fmt.bytes(totalBytes)
          } else {
            fmt.bytes(progressBytes)
          },
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.tertiary,
        )
      } else if (totalBytes != null) {
        Text(
          fmt.bytes(totalBytes),
          style = MaterialTheme.typography.labelSmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
      }
    }
    when {
      onCancel != null -> IconButton(onClick = onCancel) {
        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.cancel_download))
      }
      onRemove != null -> IconButton(onClick = onRemove) {
        Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.remove_download))
      }
    }
  }
}
