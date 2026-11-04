package com.hotsteel.aurapulse.ui

/** Flat route table; the back stack is a simple list, which is all this app needs. */
sealed interface Route {
  data object Library : Route
  data object Discover : Route
  data object Insights : Route
  data object Settings : Route
  data object Search : Route
  data object AllTracks : Route
  data object Favorites : Route
  data object Playlists : Route
  data class Playlist(val id: String) : Route
  data class Album(val key: String) : Route
  data class Artist(val name: String) : Route
  data object Equalizer : Route
  data object Account : Route
  data object Downloads : Route
  data object About : Route
}

val Route.isRoot: Boolean
  get() = this is Route.Library || this is Route.Discover || this is Route.Insights || this is Route.Settings
