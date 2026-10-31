package com.example.data.cloud

import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.data.library.encodeTrackIds
import com.example.data.library.stableTrackId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class BackupCodecTest {
  @Test fun backupExcludesLocalUrisAndDownloadedPathsButKeepsPlaylistReferences() {
    val localUri = "content://provider/document/song"
    val onlineUri = "https://media.example/song.mp3"
    val local = LibraryTrack(stableTrackId(localUri), localUri, "Local", source = TrackSource.LOCAL)
    val remote = LibraryTrack(stableTrackId(onlineUri), onlineUri, "Online", source = TrackSource.DIRECT, downloadedPath = "/private/song.audio")
    val playlist = LibraryPlaylist("playlist_test-123", "Mix", encodeTrackIds(listOf(local.id, remote.id)), 42)
    val json = BackupCodec.encode(listOf(local, remote), listOf(playlist))
    assertFalse(json.contains("content://"))
    assertFalse(json.contains("/private/song.audio"))
    val decoded = BackupCodec.decode(json)
    assertEquals(listOf(remote.id), decoded.tracks.map { it.id })
    assertNull(decoded.tracks.single().downloadedPath)
    assertEquals(listOf(local.id, remote.id), decoded.playlists.single().trackIds())
  }

  @Test fun rejectsFileUrisAndTamperedTrackIds() {
    val uri = "https://media.example/song.mp3"
    val track = LibraryTrack(stableTrackId(uri), uri, "Song", source = TrackSource.DIRECT)
    val json = BackupCodec.encode(listOf(track), emptyList())
    assertThrows(Exception::class.java) { BackupCodec.decode(json.replace(uri, "file:///private/data")) }
    assertThrows(Exception::class.java) { BackupCodec.decode(json.replace(track.id, "track_00000000000000000000000000000000")) }
  }

  @Test fun rejectsOversizedAndUnsupportedSnapshots() {
    assertThrows(Exception::class.java) { BackupCodec.decode("x".repeat(BackupCodec.MAX_BACKUP_BYTES + 1)) }
    assertThrows(Exception::class.java) { BackupCodec.decode("""{"version":2,"tracks":[],"playlists":[]}""") }
  }
}
