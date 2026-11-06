package com.hotsteel.aurapulse.playback

import android.content.Context
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.hotsteel.aurapulse.AuraApp
import com.hotsteel.aurapulse.R

/**
 * Builds the playback notification without ever asking the system about the
 * channel.
 *
 * Media3's [androidx.media3.session.DefaultMediaNotificationProvider] calls
 * `NotificationManager.getNotificationChannel()` on every single update, and the
 * framework answers by copying the channel — a copy that rebuilds its
 * `VibrationEffect`. On this device that path blocked the main thread inside
 * `VibrationEffect.createWaveform`, which is exactly the freeze reported when the
 * play button was tapped: playback starts, the session updates the notification,
 * and the service's main thread never comes back.
 *
 * The channel is created once in [AuraApp]; building the notification only reads
 * player state, so a notification update is now pure local work.
 */
@OptIn(UnstableApi::class)
class AuraNotificationProvider(private val context: Context) : MediaNotification.Provider {

  override fun createNotification(
    mediaSession: MediaSession,
    customLayout: ImmutableList<CommandButton>,
    actionFactory: MediaNotification.ActionFactory,
    onNotificationChangedCallback: MediaNotification.Provider.Callback,
  ): MediaNotification {
    val player = mediaSession.player
    val metadata = player.mediaMetadata
    val playing = player.isPlaying

    val builder = NotificationCompat.Builder(context, AuraApp.CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_aura)
      .setContentTitle(metadata.title ?: context.getString(R.string.app_name))
      .setContentText(metadata.artist)
      .setContentIntent(mediaSession.sessionActivity)
      .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setSilent(true)
      .setOngoing(playing)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
      .setStyle(
        MediaStyleNotificationHelper.MediaStyle(mediaSession)
          .setShowActionsInCompactView(0, 1, 2),
      )

    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
      builder.addAction(
        action(
          actionFactory, mediaSession, R.drawable.ic_notif_previous,
          R.string.previous_track, Player.COMMAND_SEEK_TO_PREVIOUS,
        ),
      )
    }
    builder.addAction(
      action(
        actionFactory, mediaSession,
        if (playing) R.drawable.ic_notif_pause else R.drawable.ic_notif_play,
        if (playing) R.string.pause else R.string.play,
        Player.COMMAND_PLAY_PAUSE,
      ),
    )
    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
      builder.addAction(
        action(
          actionFactory, mediaSession, R.drawable.ic_notif_next,
          R.string.next_track, Player.COMMAND_SEEK_TO_NEXT,
        ),
      )
    }

    return MediaNotification(NOTIFICATION_ID, builder.build())
  }

  private fun action(
    factory: MediaNotification.ActionFactory,
    session: MediaSession,
    icon: Int,
    title: Int,
    command: Int,
  ): NotificationCompat.Action = factory.createMediaAction(
    session,
    IconCompat.createWithResource(context, icon),
    context.getString(title),
    command,
  )

  override fun handleCustomCommand(mediaSession: MediaSession, action: String, extras: android.os.Bundle): Boolean = false

  companion object {
    const val NOTIFICATION_ID = 1001
  }
}
