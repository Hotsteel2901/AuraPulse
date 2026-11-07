package com.hotsteel.aurapulse.playback

import android.content.Context
import android.graphics.Bitmap
import androidx.annotation.OptIn
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.common.util.Util
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.FutureCallback
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.MoreExecutors
import com.hotsteel.aurapulse.AuraApp
import com.hotsteel.aurapulse.R
import java.util.concurrent.atomic.AtomicInteger

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

  /** Only the newest pending artwork load may post a notification. */
  private val artworkRequest = AtomicInteger(0)

  override fun createNotification(
    mediaSession: MediaSession,
    customLayout: ImmutableList<CommandButton>,
    actionFactory: MediaNotification.ActionFactory,
    onNotificationChangedCallback: MediaNotification.Provider.Callback,
  ): MediaNotification {
    val player = mediaSession.player
    val metadata = player.mediaMetadata
    // The transport action toggles playWhenReady, so the icon has to follow
    // playWhenReady too. isPlaying is false while a stream buffers, which made
    // the notification offer "Play" and then pause when it was tapped.
    val showPlayButton = Util.shouldShowPlayButton(player)

    val builder = NotificationCompat.Builder(context, AuraApp.CHANNEL_ID)
      .setSmallIcon(R.drawable.ic_stat_aura)
      .setContentTitle(metadata.title ?: context.getString(R.string.app_name))
      .setContentText(metadata.artist)
      .setContentIntent(mediaSession.sessionActivity)
      .setDeleteIntent(actionFactory.createNotificationDismissalIntent(mediaSession))
      .setOnlyAlertOnce(true)
      .setShowWhen(false)
      .setSilent(true)
      .setOngoing(!showPlayButton)
      .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
      .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
      .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

    val compact = mutableListOf<Int>()
    var actionIndex = 0
    fun addTransport(action: NotificationCompat.Action) {
      builder.addAction(action)
      if (compact.size < 3) compact += actionIndex
      actionIndex++
    }

    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_PREVIOUS)) {
      addTransport(
        action(
          actionFactory, mediaSession, R.drawable.ic_notif_previous,
          R.string.previous_track, Player.COMMAND_SEEK_TO_PREVIOUS,
        ),
      )
    }
    addTransport(
      action(
        actionFactory, mediaSession,
        if (showPlayButton) R.drawable.ic_notif_play else R.drawable.ic_notif_pause,
        if (showPlayButton) R.string.play else R.string.pause,
        Player.COMMAND_PLAY_PAUSE,
      ),
    )
    if (player.isCommandAvailable(Player.COMMAND_SEEK_TO_NEXT)) {
      addTransport(
        action(
          actionFactory, mediaSession, R.drawable.ic_notif_next,
          R.string.next_track, Player.COMMAND_SEEK_TO_NEXT,
        ),
      )
    }

    // Only request the indices that were actually added; the old hard-coded
    // (0, 1, 2) pointed past the end of a one or two action notification.
    builder.setStyle(
      MediaStyleNotificationHelper.MediaStyle(mediaSession)
        .setShowActionsInCompactView(*compact.toIntArray()),
    )

    // DefaultMediaNotificationProvider used to put the cover into the
    // notification and the lock screen. Without this the artwork vanished.
    if (player.isCommandAvailable(Player.COMMAND_GET_METADATA) &&
      (metadata.artworkData != null || metadata.artworkUri != null)
    ) {
      val bitmapFuture = mediaSession.bitmapLoader.loadBitmapFromMetadata(metadata)
      if (bitmapFuture != null) {
        if (bitmapFuture.isDone) {
          runCatching { builder.setLargeIcon(Futures.getDone(bitmapFuture)) }
        } else {
          val request = artworkRequest.incrementAndGet()
          Futures.addCallback(
            bitmapFuture,
            object : FutureCallback<Bitmap> {
              override fun onSuccess(result: Bitmap?) {
                if (result == null || artworkRequest.get() != request) return
                builder.setLargeIcon(result)
                onNotificationChangedCallback.onNotificationChanged(
                  MediaNotification(NOTIFICATION_ID, builder.build()),
                )
              }

              override fun onFailure(error: Throwable) = Unit
            },
            ContextCompat.getMainExecutor(context),
          )
        }
      }
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
