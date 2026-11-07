package com.hotsteel.aurapulse.data.library

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Download bookkeeping must update an existing library row.
 *
 * [LibraryRepository.add] is INSERT OR IGNORE so imports never replace a known
 * track. Reusing it for the downloaded path meant the row was left untouched:
 * the file existed, the UI said "download complete", but the track never
 * appeared under Downloads and playback kept using the remote stream.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DownloadBookkeepingTest {

  private lateinit var db: AuraDatabase
  private lateinit var library: LibraryRepository

  @Before
  fun setUp() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    db = Room.inMemoryDatabaseBuilder(context, AuraDatabase::class.java)
      .allowMainThreadQueries()
      .build()
    library = LibraryRepository(context, db)
  }

  @After
  fun tearDown() {
    db.close()
  }

  @Test
  fun `a completed download is recorded on the existing track`() = runBlocking {
    val track = TrackEntity(
      id = "remote-1", uri = "https://example.invalid/1.mp3", title = "One", source = Source.DIRECT,
    )
    assertTrue(library.add(track))

    library.setDownloadedPath(track.id, "/data/user/0/app/files/downloads/remote-1.mp3")

    assertEquals(
      "/data/user/0/app/files/downloads/remote-1.mp3",
      library.tracksSnapshot().single().downloadedPath,
    )
  }

  @Test
  fun `removing a download clears only the path`() = runBlocking {
    val track = TrackEntity(
      id = "remote-2", uri = "https://example.invalid/2.mp3", title = "Two",
      source = Source.DIRECT, downloadedPath = "/data/user/0/app/files/downloads/remote-2.mp3",
    )
    assertTrue(library.add(track))

    library.setDownloadedPath(track.id, null)

    val stored = library.tracksSnapshot().single()
    assertNull(stored.downloadedPath)
    assertEquals("Two", stored.title)
    assertEquals("https://example.invalid/2.mp3", stored.uri)
  }
}
