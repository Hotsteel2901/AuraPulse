package com.example.data.cloud

import com.example.data.library.LibraryPlaylist
import com.example.data.library.LibraryTrack
import com.example.data.library.TrackSource
import com.example.data.library.encodeTrackIds
import com.example.data.library.stableTrackId
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.json.JSONObject
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
    val encodedTracks = JSONObject(json).getJSONArray("tracks")
    assertEquals(1, encodedTracks.length())
    assertEquals(onlineUri, encodedTracks.getJSONObject(0).getString("uri"))
    assertFalse(encodedTracks.getJSONObject(0).has("downloadedPath"))
    val decoded = BackupCodec.decode(json)
    assertEquals(listOf(remote.id), decoded.tracks.map { it.id })
    assertNull(decoded.tracks.single().downloadedPath)
    assertEquals(listOf(local.id, remote.id), decoded.playlists.single().trackIds())
  }

  @Test fun rejectsFileUrisAndTamperedTrackIds() {
    val uri = "https://media.example/song.mp3"
    val track = LibraryTrack(stableTrackId(uri), uri, "Song", source = TrackSource.DIRECT)
    val json = BackupCodec.encode(listOf(track), emptyList())
    // Android's JSONObject may escape slashes, so mutate the parsed fields rather than
    // replacing substrings in the serialized JSON. Match the file URI's ID to isolate
    // the remote-URL validation from the separate ID validation.
    val fileBackup = JSONObject(json)
    val fileUri = "file:///private/data"
    fileBackup.getJSONArray("tracks").getJSONObject(0)
      .put("uri", fileUri).put("id", stableTrackId(fileUri))
    assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(fileBackup.toString()) }

    val tamperedBackup = JSONObject(json)
    tamperedBackup.getJSONArray("tracks").getJSONObject(0)
      .put("id", "track_00000000000000000000000000000000")
    assertThrows(IllegalArgumentException::class.java) { BackupCodec.decode(tamperedBackup.toString()) }
  }

  @Test fun rejectsOversizedAndUnsupportedSnapshots() {
    assertThrows(Exception::class.java) { BackupCodec.decode("x".repeat(BackupCodec.MAX_BACKUP_BYTES + 1)) }
    assertThrows(Exception::class.java) { BackupCodec.decode("""{"version":2,"tracks":[],"playlists":[]}""") }
  }
}
