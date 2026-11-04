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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.core.fmt
import com.hotsteel.aurapulse.data.library.TrackEntity
import com.hotsteel.aurapulse.ui.AuraViewModel
import com.hotsteel.aurapulse.ui.components.Artwork
import com.hotsteel.aurapulse.ui.components.EmptyState
import com.hotsteel.aurapulse.ui.components.TrackRow

@Composable
fun DetailTopBar(
  title: String,
  onBack: () -> Unit,
  modifier: Modifier = Modifier,
  trailing: (@Composable () -> Unit)? = null,
) {
  Row(
    modifier.fillMaxWidth().padding(horizontal = 6.dp, vertical = 4.dp),
    verticalAlignment = Alignment.CenterVertically,
  ) {
    IconButton(onClick = onBack) {
      Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
    }
    Text(
      title,
      style = MaterialTheme.typography.titleMedium,
      modifier = Modifier.weight(1f).padding(start = 4.dp),
      maxLines = 1,
      overflow = TextOverflow.Ellipsis,
    )
    trailing?.invoke()
  }
}

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun CollectionHeader(
  title: String,
  subtitle: String,
  tracks: List<TrackEntity>,
  onPlay: () -> Unit,
  onShuffle: () -> Unit,
  roundArt: Boolean = false,
) {
  Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
      if (roundArt) {
        Box(
          Modifier.size(124.dp).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
          contentAlignment = Alignment.Center,
        ) {
          Icon(Icons.Default.Person, null, Modifier.size(52.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
        }
      } else {
        Artwork(tracks.firstOrNull(), 124.dp, corner = 26.dp)
      }
      Column(Modifier.weight(1f)) {
        Text(title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.height(4.dp))
        Text(
          subtitle,
          style = MaterialTheme.typography.bodySmall,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          maxLines = 2,
          overflow = TextOverflow.Ellipsis,
        )
      }
    }
    Spacer(Modifier.height(16.dp))
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
      Button(onClick = onPlay) {
        Icon(Icons.Default.PlayArrow, null, Modifier.size(20.dp))
        Text(stringResource(R.string.playlist_play_all), Modifier.padding(start = 6.dp))
      }
      FilledTonalButton(onClick = onShuffle) {
        Icon(Icons.Default.Shuffle, null, Modifier.size(18.dp))
        Text(stringResource(R.string.playlist_shuffle_all), Modifier.padding(start = 6.dp))
      }
    }
    Spacer(Modifier.height(10.dp))
  }
}

@Composable
fun AllTracksScreen(vm: AuraViewModel, onBack: () -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val settings by vm.settings.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val stats by vm.stats.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  val visible = vm.sortedTracks(tracks, settings.librarySort, favorites, stats, settings.libraryFilter)

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(stringResource(R.string.section_tracks), onBack)
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
      item("count") {
        Text(
          stringResource(R.string.library_tracks_count, visible.size),
          style = MaterialTheme.typography.labelLarge,
          color = MaterialTheme.colorScheme.onSurfaceVariant,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
      }
      items(visible, key = { it.id }) { track ->
        TrackRow(
          track = track,
          vm = vm,
          playing = playback.track?.id == track.id,
          progress = downloads[track.id],
          onClick = { vm.play(track, visible) },
        )
      }
    }
  }
}

@Composable
fun FavoritesScreen(vm: AuraViewModel, onBack: () -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val favorites by vm.favorites.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val list = remember(tracks, favorites) { tracks.filter { it.id in favorites } }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(stringResource(R.string.filter_favorites), onBack)
    if (list.isEmpty()) {
      EmptyState(
        title = stringResource(R.string.filter_favorites),
        body = stringResource(R.string.empty_library_body),
      )
    } else {
      LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
        item("header") {
          CollectionHeader(
            title = stringResource(R.string.filter_favorites),
            subtitle = stringResource(R.string.library_tracks_count, list.size),
            tracks = list,
            onPlay = { vm.play(list.first(), list) },
            onShuffle = { vm.playShuffled(list) },
          )
        }
        items(list, key = { it.id }) { track ->
          TrackRow(
            track = track,
            vm = vm,
            playing = playback.track?.id == track.id,
            onClick = { vm.play(track, list) },
            trailing = {
              Icon(
                Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 8.dp).size(18.dp),
              )
            },
          )
        }
      }
    }
  }
}

@Composable
fun AlbumScreen(vm: AuraViewModel, key: String, onBack: () -> Unit, onOpenArtist: (String) -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  val album = remember(tracks, key) { albumGroups(tracks).firstOrNull { it.key == key } }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(album?.title ?: stringResource(R.string.section_albums), onBack)
    if (album == null) {
      EmptyState(title = stringResource(R.string.section_albums), body = stringResource(R.string.search_empty))
      return@Column
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
      item("header") {
        CollectionHeader(
          title = album.title,
          subtitle = album.artist + " · " + stringResource(R.string.library_tracks_count, album.tracks.size) + " · " +
            fmt.duration(album.tracks.sumOf { it.durationMs }),
          tracks = album.tracks,
          onPlay = { vm.play(album.tracks.first(), album.tracks) },
          onShuffle = { vm.playShuffled(album.tracks) },
        )
        Text(
          album.artist,
          style = MaterialTheme.typography.titleSmall,
          color = MaterialTheme.colorScheme.primary,
          modifier = Modifier.clickable { onOpenArtist(album.artist) }
            .padding(horizontal = 20.dp, vertical = 6.dp),
        )
      }
      items(album.tracks, key = { it.id }) { track ->
        TrackRow(
          track = track,
          vm = vm,
          playing = playback.track?.id == track.id,
          progress = downloads[track.id],
          onClick = { vm.play(track, album.tracks) },
          trailing = {
            if (track.trackNumber > 0) {
              Text(
                "%02d".format(track.trackNumber),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(30.dp),
              )
            }
          },
        )
      }
    }
  }
}

@Composable
fun ArtistScreen(vm: AuraViewModel, name: String, onBack: () -> Unit, onOpenAlbum: (String) -> Unit) {
  val tracks by vm.tracks.collectAsStateWithLifecycle()
  val playback by vm.playback.collectAsStateWithLifecycle()
  val downloads by vm.downloads.collectAsStateWithLifecycle()
  val artistTracks = remember(tracks, name) {
    tracks.filter { it.artist.equals(name, true) || it.albumArtist.equals(name, true) }
  }
  val albums = remember(artistTracks) { albumGroups(artistTracks) }

  Column(Modifier.fillMaxSize()) {
    DetailTopBar(name, onBack)
    if (artistTracks.isEmpty()) {
      EmptyState(title = name, body = stringResource(R.string.search_empty))
      return@Column
    }
    LazyColumn(contentPadding = PaddingValues(bottom = 120.dp), modifier = Modifier.fillMaxSize()) {
      item("header") {
        CollectionHeader(
          title = name,
          subtitle = stringResource(R.string.library_tracks_count, artistTracks.size) + " · " +
            stringResource(R.string.library_albums_count, albums.size),
          tracks = artistTracks,
          onPlay = { vm.play(artistTracks.first(), artistTracks) },
          onShuffle = { vm.playShuffled(artistTracks) },
          roundArt = true,
        )
      }
      if (albums.isNotEmpty()) {
        item("albums") {
          Text(
            stringResource(R.string.section_albums),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
          )
          LazyRow(contentPadding = PaddingValues(horizontal = 20.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(albums, key = { it.key }) { album ->
              Column(Modifier.width(132.dp).clickable { onOpenAlbum(album.key) }) {
                Artwork(album.tracks.firstOrNull(), 132.dp, corner = 20.dp)
                Spacer(Modifier.height(6.dp))
                Text(album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                  stringResource(R.string.library_tracks_count, album.tracks.size),
                  style = MaterialTheme.typography.bodySmall,
                  color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
              }
            }
          }
        }
      }
      item("tracks-title") {
        Text(
          stringResource(R.string.section_tracks),
          style = MaterialTheme.typography.titleMedium,
          modifier = Modifier.padding(horizontal = 20.dp, vertical = 10.dp),
        )
      }
      items(artistTracks, key = { it.id }) { track ->
        TrackRow(
          track = track,
          vm = vm,
          playing = playback.track?.id == track.id,
          progress = downloads[track.id],
          onClick = { vm.play(track, artistTracks) },
        )
      }
    }
  }
}
