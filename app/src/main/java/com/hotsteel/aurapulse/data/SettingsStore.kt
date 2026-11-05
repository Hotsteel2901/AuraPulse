package com.hotsteel.aurapulse.data

import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

enum class ThemeMode { SYSTEM, LIGHT, DARK, BLACK }
enum class Palette { AURA, EMBER, TIDE, BLOOM, GRAPHITE, SOLAR }
enum class Contrast { LOW, STANDARD, HIGH }
enum class MotionLevel { FULL, REDUCED, SYSTEM }
enum class TrackSort { ADDED, TITLE, ARTIST, DURATION, PLAYS }
enum class LibraryFilter { ALL, FAVORITES, DOWNLOADED }

data class AppSettings(
  val theme: ThemeMode = ThemeMode.SYSTEM,
  val palette: Palette = Palette.AURA,
  val dynamicColor: Boolean = true,
  val contrast: Contrast = Contrast.STANDARD,
  val motion: MotionLevel = MotionLevel.FULL,
  val haptics: Boolean = true,
  val cornerScale: Float = 1f,
  val resumeOnLaunch: Boolean = true,
  val librarySort: TrackSort = TrackSort.ADDED,
  val libraryFilter: LibraryFilter = LibraryFilter.ALL,
  val onboardingDone: Boolean = false,
  val language: String = "system",
  val searchHistory: List<String> = emptyList(),
)

/**
 * Single source of truth for presentation preferences. Small, synchronous and
 * copy-on-write so Compose can observe it without a database round trip.
 */
class SettingsStore(context: Context) {
  private val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
  private val mutable = MutableStateFlow(read())
  val state: StateFlow<AppSettings> = mutable.asStateFlow()

  val current: AppSettings get() = mutable.value

  fun update(transform: (AppSettings) -> AppSettings) {
    val next = transform(mutable.value)
    if (next == mutable.value) return
    mutable.value = next
    write(next)
  }

  fun addSearchTerm(term: String) {
    val clean = term.trim().take(80)
    if (clean.length < 2) return
    update { it.copy(searchHistory = (listOf(clean) + it.searchHistory.filterNot { old -> old.equals(clean, true) }).take(8)) }
  }

  fun clearSearchHistory() = update { it.copy(searchHistory = emptyList()) }

  fun setLanguage(tag: String) = update { it.copy(language = tag) }

  private fun read(): AppSettings = AppSettings(
    theme = enum(prefs.getString("theme", null), ThemeMode.SYSTEM),
    palette = enum(prefs.getString("palette", null), Palette.AURA),
    dynamicColor = prefs.getBoolean("dynamic", true),
    contrast = enum(prefs.getString("contrast", null), Contrast.STANDARD),
    motion = enum(prefs.getString("motion", null), MotionLevel.FULL),
    haptics = prefs.getBoolean("haptics", true),
    cornerScale = prefs.getFloat("corner_scale", 1f).coerceIn(0.4f, 1.6f),
    resumeOnLaunch = prefs.getBoolean("resume", true),
    librarySort = enum(prefs.getString("sort", null), TrackSort.ADDED),
    libraryFilter = enum(prefs.getString("filter", null), LibraryFilter.ALL),
    onboardingDone = prefs.getBoolean("onboarding", false),
    language = prefs.getString("language", "system") ?: "system",
    searchHistory = prefs.getString("search_history", null)
      ?.split('\u0001')?.filter { it.isNotBlank() }?.take(8).orEmpty(),
  )

  private fun write(settings: AppSettings) {
    prefs.edit {
      putString("theme", settings.theme.name)
      putString("palette", settings.palette.name)
      putBoolean("dynamic", settings.dynamicColor)
      putString("contrast", settings.contrast.name)
      putString("motion", settings.motion.name)
      putBoolean("haptics", settings.haptics)
      putFloat("corner_scale", settings.cornerScale)
      putBoolean("resume", settings.resumeOnLaunch)
      putString("sort", settings.librarySort.name)
      putString("filter", settings.libraryFilter.name)
      putBoolean("onboarding", settings.onboardingDone)
      putString("language", settings.language)
      putString("search_history", settings.searchHistory.joinToString("\u0001"))
    }
  }

  private inline fun <reified T : Enum<T>> enum(name: String?, fallback: T): T =
    name?.let { value -> enumValues<T>().firstOrNull { it.name == value } } ?: fallback

  companion object {
    const val PREFS = "aurapulse_settings"
  }
}
