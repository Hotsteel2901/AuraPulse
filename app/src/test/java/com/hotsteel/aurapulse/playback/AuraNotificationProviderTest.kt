package com.hotsteel.aurapulse.playback

import android.app.Notification
import android.app.PendingIntent
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.test.core.app.ApplicationProvider
import com.google.common.collect.ImmutableList
import com.hotsteel.aurapulse.AuraApp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * Guards the replacement for media3's default notification provider.
 *
 * That default called `NotificationManager.getNotificationChannel()` on every
 * playback state change — a call the framework answers by copying the channel
 * and rebuilding its vibration effect, which is what wedged the main thread on
 * device. This provider must build the notification from player state alone.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class AuraNotificationProviderTest {

  @Test
  fun `notification comes from the app channel with transport actions`() {
    val app = ApplicationProvider.getApplicationContext<AuraApp>()
    val player = ExoPlayer.Builder(app).build()
    player.setMediaItem(
      MediaItem.Builder()
        .setUri("https://example.invalid/track-1.mp3")
        .setMediaId("track-1")
        .setMediaMetadata(
          MediaMetadata.Builder().setTitle("Nightfall").setArtist("Aura").build(),
        )
        .build(),
    )
    val session = MediaSession.Builder(app, player).build()

    try {
      val built = AuraNotificationProvider(app).createNotification(
        session,
        ImmutableList.of(),
        FakeActionFactory(app),
        object : MediaNotification.Provider.Callback {
          override fun onNotificationChanged(notification: MediaNotification) = Unit
        },
      )

      assertEquals(AuraNotificationProvider.NOTIFICATION_ID, built.notificationId)
      assertEquals(AuraApp.CHANNEL_ID, built.notification.channelId)
      assertEquals("Nightfall", built.notification.extras.getCharSequence(Notification.EXTRA_TITLE).toString())
      assertNotNull(built.notification.actions)
    } finally {
      session.release()
      player.release()
    }
  }

  /** Stand-in for media3's internal factory: real actions need a session token PendingIntent. */
  private class FakeActionFactory(private val app: AuraApp) : MediaNotification.ActionFactory {
    private fun intent(): PendingIntent = PendingIntent.getBroadcast(
      app,
      0,
      Intent("com.hotsteel.aurapulse.TEST_MEDIA_ACTION"),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    override fun createMediaAction(
      session: MediaSession,
      icon: IconCompat,
      title: CharSequence,
      command: Int,
    ): NotificationCompat.Action = NotificationCompat.Action.Builder(icon, title, intent()).build()

    override fun createCustomAction(
      session: MediaSession,
      icon: IconCompat,
      title: CharSequence,
      action: String,
      extras: android.os.Bundle,
    ): NotificationCompat.Action = NotificationCompat.Action.Builder(icon, title, intent()).build()

    override fun createCustomActionFromCustomCommandButton(
      session: MediaSession,
      customCommandButton: androidx.media3.session.CommandButton,
    ): NotificationCompat.Action = NotificationCompat.Action.Builder(
      IconCompat.createWithResource(app, com.hotsteel.aurapulse.R.drawable.ic_stat_aura),
      "custom",
      intent(),
    ).build()

    override fun createMediaActionPendingIntent(session: MediaSession, action: Long): PendingIntent = intent()

    override fun createNotificationDismissalIntent(session: MediaSession): PendingIntent = intent()
  }
}
