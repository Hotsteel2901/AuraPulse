package com.example.data.library

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class ListeningStoreTest {
  private val context: Context = ApplicationProvider.getApplicationContext()

  @Before fun clear() { context.getSharedPreferences("listening", Context.MODE_PRIVATE).edit().clear().commit() }

  @Test fun favoritesAndResumePersistWithoutDuplicatingHistory() {
    val store = ListeningStore(context)
    store.toggleFavorite("a")
    store.recordTrack("a")
    store.recordTrack("b")
    store.recordTrack("a")
    store.recordPosition("a", 12000)
    val restored = ListeningStore(context)
    assertEquals(listOf("a", "b"), restored.history.value)
    assertTrue("a" in restored.favorites.value)
    assertEquals(ListeningStore.ResumePoint("a", 12000), restored.resume.value)
    restored.removeTrack("a")
    assertFalse("a" in restored.favorites.value)
    assertNull(restored.resume.value)
    assertEquals(listOf("b"), restored.history.value)
  }

  @Test fun historyIsBoundedAndCanBeCleared() {
    val store = ListeningStore(context)
    repeat(120) { store.recordTrack(it.toString()) }
    assertEquals(100, store.history.value.size)
    assertEquals("119", store.history.value.first())
    store.clearHistory()
    assertTrue(ListeningStore(context).history.value.isEmpty())
  }
}
