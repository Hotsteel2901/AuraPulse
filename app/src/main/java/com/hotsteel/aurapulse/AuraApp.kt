package com.hotsteel.aurapulse

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import com.hotsteel.aurapulse.data.SettingsStore
import com.hotsteel.aurapulse.data.discovery.DiscoveryClient
import com.hotsteel.aurapulse.data.github.GistSync
import com.hotsteel.aurapulse.data.github.GitHubAccount
import com.hotsteel.aurapulse.data.library.AuraDatabase
import com.hotsteel.aurapulse.data.library.DownloadManager
import com.hotsteel.aurapulse.data.library.LibraryRepository
import com.hotsteel.aurapulse.data.library.ListeningStore
import com.hotsteel.aurapulse.data.library.PlaylistImporter
import com.hotsteel.aurapulse.playback.EqualizerController

/** Everything long-lived, created once and shared by the activity and the service. */
class AppContainer(application: Application) {
  val settings = SettingsStore(application)
  val database = AuraDatabase.get(application)
  val library = LibraryRepository(application, database)
  val listening = ListeningStore(application)
  val downloads = DownloadManager(application)
  val discovery = DiscoveryClient()
  val importer = PlaylistImporter()
  val account = GitHubAccount(application)
  val gistSync = GistSync(
    context = application,
    account = account,
    library = library,
    favorites = { listening.favorites.value },
    restoreFavorites = { listening.setFavorites(it) },
  )
}

class AuraApp : Application() {
  lateinit var container: AppContainer
    private set

  override fun onCreate() {
    super.onCreate()
    instance = this
    container = AppContainer(this)
    EqualizerController.initialize(this)
    createChannel()
  }

  private fun createChannel() {
    if (Build.VERSION.SDK_INT < 26) return
    val manager = getSystemService(NotificationManager::class.java) ?: return
    val channel = NotificationChannel(
      CHANNEL_ID,
      getString(R.string.notification_channel_playback),
      NotificationManager.IMPORTANCE_LOW,
    ).apply {
      description = getString(R.string.notification_channel_playback_desc)
      setShowBadge(false)
      enableVibration(false)
    }
    manager.createNotificationChannel(channel)
  }

  companion object {
    const val CHANNEL_ID = "aurapulse_playback"
    lateinit var instance: AuraApp
      private set

    val container: AppContainer get() = instance.container
  }
}
