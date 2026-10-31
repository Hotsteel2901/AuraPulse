package com.example.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryRepositoryTest {
  private lateinit var database: LibraryDatabase
  private lateinit var library: LibraryRepository

  @Before fun setup() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    database = Room.inMemoryDatabaseBuilder(context, LibraryDatabase::class.java).allowMainThreadQueries().build()
    library = LibraryRepository(context, database)
  }
  @After fun close() = database.close()

  @Test fun libraryAndPlaylistPersistAndRemovalPrunesPlaylist() = runBlocking {
    val uri = "content://documents/song"
    val track = LibraryTrack(stableTrackId(uri), uri, "Song", source = TrackSource.LOCAL)
    assertTrue(library.addTrack(track))
    assertFalse(library.addTrack(track))
    val playlist = library.createPlaylist("Favorites")
    library.setPlaylistTracks(playlist.id, listOf(track.id, track.id))
    assertEquals(listOf(track.id), database.libraryDao().getPlaylist(playlist.id)?.trackIds())
    library.deleteTrack(track)
    assertTrue(database.libraryDao().getPlaylist(playlist.id)!!.trackIds().isEmpty())
    assertNull(database.libraryDao().getTrack(track.id))
  }

  @Test fun folderDetectionHandlesMimeAndCommonExtensions() {
    assertTrue(LibraryRepository.isAudioDocument("audio/flac", "recording"))
    assertTrue(LibraryRepository.isAudioDocument("application/octet-stream", "song.MP3"))
    assertFalse(LibraryRepository.isAudioDocument("text/plain", "notes.txt"))
    assertEquals(stableTrackId("content://one"), stableTrackId("content://one"))
    assertNotEquals(stableTrackId("content://one"), stableTrackId("content://two"))
  }
}
