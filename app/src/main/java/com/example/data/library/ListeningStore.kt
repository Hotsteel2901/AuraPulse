package com.example.data.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/** Local-only listening preferences; original files and document grants never leave this device. */
class ListeningStore(context: Context) {
  private val prefs = context.getSharedPreferences("listening", Context.MODE_PRIVATE)
  private val mutableFavorites = MutableStateFlow(prefs.getStringSet("favorites", emptySet())?.toSet() ?: emptySet())
  val favorites = mutableFavorites.asStateFlow()
  private val mutableHistory = MutableStateFlow(readHistory())
  val history = mutableHistory.asStateFlow()
  private val mutableResume = MutableStateFlow(readResume())
  val resume = mutableResume.asStateFlow()

  data class ResumePoint(val trackId: String, val positionMs: Long)

  fun toggleFavorite(id: String) {
    if (id.isBlank()) return
    val updated = mutableFavorites.value.toMutableSet().apply { if (!add(id)) remove(id) }.toSet()
    prefs.edit().putStringSet("favorites", updated).apply()
    mutableFavorites.value = updated
  }

  fun recordTrack(id: String) {
    if (id.isBlank()) return
    val updated = (listOf(id) + mutableHistory.value.filterNot { it == id }).take(MAX_HISTORY)
    if (updated == mutableHistory.value) return
    prefs.edit().putString("history", JSONArray(updated).toString()).apply()
    mutableHistory.value = updated
  }

  fun clearHistory() {
    prefs.edit().remove("history").apply()
    mutableHistory.value = emptyList()
  }

  fun recordPosition(id: String, positionMs: Long) {
    if (id.isBlank()) return
    val point = ResumePoint(id, positionMs.coerceAtLeast(0))
    if (point == mutableResume.value) return
    prefs.edit().putString("last_track", id).putLong("last_position_ms", point.positionMs).apply()
    mutableResume.value = point
  }

  fun removeTrack(id: String) {
    if (id in mutableFavorites.value) toggleFavorite(id)
    if (id in mutableHistory.value) {
      val updated = mutableHistory.value.filterNot { it == id }
      prefs.edit().putString("history", JSONArray(updated).toString()).apply()
      mutableHistory.value = updated
    }
    if (mutableResume.value?.trackId == id) {
      prefs.edit().remove("last_track").remove("last_position_ms").apply()
      mutableResume.value = null
    }
  }

  private fun readHistory(): List<String> = runCatching {
    val array = JSONArray(prefs.getString("history", "[]"))
    (0 until minOf(array.length(), MAX_HISTORY)).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.distinct()
  }.getOrDefault(emptyList())

  private fun readResume(): ResumePoint? = prefs.getString("last_track", null)?.takeIf(String::isNotBlank)
    ?.let { ResumePoint(it, prefs.getLong("last_position_ms", 0).coerceAtLeast(0)) }

  companion object { const val MAX_HISTORY = 100 }
}
