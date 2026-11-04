package com.hotsteel.aurapulse.data.library

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray

/**
 * Small, local-only state that is not worth a database row: favourites, the
 * recent queue, and the resume point. Nothing here ever leaves the device
 * unless the person explicitly pushes a gist backup.
 */
class ListeningStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences("aurapulse_listening", Context.MODE_PRIVATE)
  private val mutableFavorites = MutableStateFlow(readSet("favorites"))
  val favorites: StateFlow<Set<String>> = mutableFavorites.asStateFlow()

  private val mutableHistory = MutableStateFlow(readArray("history"))
  val history: StateFlow<List<String>> = mutableHistory.asStateFlow()

  private val mutableResume = MutableStateFlow(readResume())
  val resume: StateFlow<ResumePoint?> = mutableResume.asStateFlow()

  data class ResumePoint(val trackId: String, val positionMs: Long, val queueIds: List<String> = emptyList())

  fun toggleFavorite(id: String): Boolean {
    if (id.isBlank()) return false
    val updated = mutableFavorites.value.toMutableSet()
    val added = if (!updated.add(id)) { updated.remove(id); false } else true
    mutableFavorites.value = updated
    prefs.edit().putString("favorites", JSONArray(updated.toList()).toString()).apply()
    return added
  }

  fun setFavorites(ids: Set<String>) {
    if (ids == mutableFavorites.value) return
    mutableFavorites.value = ids
    prefs.edit().putString("favorites", JSONArray(ids.toList()).toString()).apply()
  }

  fun recordHistory(id: String) {
    if (id.isBlank()) return
    val updated = (listOf(id) + mutableHistory.value.filterNot { it == id }).take(120)
    if (updated == mutableHistory.value) return
    mutableHistory.value = updated
    prefs.edit().putString("history", JSONArray(updated).toString()).apply()
  }

  fun clearHistory() {
    mutableHistory.value = emptyList()
    prefs.edit().remove("history").apply()
  }

  fun rememberResume(trackId: String, positionMs: Long, queueIds: List<String> = emptyList()) {
    val point = ResumePoint(trackId, positionMs.coerceAtLeast(0), queueIds)
    if (point.trackId == mutableResume.value?.trackId && kotlin.math.abs((mutableResume.value?.positionMs ?: 0) - point.positionMs) < 2500) return
    mutableResume.value = point
    prefs.edit().putString("resume_track", point.trackId)
      .putLong("resume_position", point.positionMs)
      .putString("resume_queue", JSONArray(point.queueIds).toString())
      .apply()
  }

  fun clearResume() {
    mutableResume.value = null
    prefs.edit().remove("resume_track").remove("resume_position").remove("resume_queue").apply()
  }

  fun removeTrack(id: String) {
    if (id in mutableFavorites.value) {
      mutableFavorites.value = mutableFavorites.value - id
      prefs.edit().putString("favorites", JSONArray(mutableFavorites.value.toList()).toString()).apply()
    }
    if (id in mutableHistory.value) {
      mutableHistory.value = mutableHistory.value - id
      prefs.edit().putString("history", JSONArray(mutableHistory.value).toString()).apply()
    }
    if (mutableResume.value?.trackId == id) clearResume()
  }

  private fun readSet(key: String): Set<String> = runCatching {
    val array = JSONArray(prefs.getString(key, "[]"))
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }.toSet()
  }.getOrDefault(emptySet())

  private fun readArray(key: String): List<String> = runCatching {
    val array = JSONArray(prefs.getString(key, "[]"))
    (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
  }.getOrDefault(emptyList())

  private fun readResume(): ResumePoint? {
    val track = prefs.getString("resume_track", null)?.takeIf { it.isNotBlank() } ?: return null
    val queue = runCatching {
      val array = JSONArray(prefs.getString("resume_queue", "[]"))
      (0 until array.length()).mapNotNull { array.optString(it).takeIf(String::isNotBlank) }
    }.getOrDefault(emptyList())
    return ResumePoint(track, prefs.getLong("resume_position", 0), queue)
  }
}
