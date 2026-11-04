package com.hotsteel.aurapulse.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.hotsteel.aurapulse.AuraApp
import com.hotsteel.aurapulse.MainActivity
import com.hotsteel.aurapulse.R
import com.hotsteel.aurapulse.widget.NowPlayingWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Owns the real ExoPlayer and the media session. The service is also where the
 * sleep timer, the A–B loop, play logging and the resume snapshot are enforced,
 * so all of them keep working with the UI closed.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {
  private var session: MediaSession? = null
  private var player: ExoPlayer? = null
  private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

  private var currentTrackId: String? = null
  private var listeningSince: Long = 0
  private var listenedMs: Long = 0
  private var lastSave = 0L
  private var ticker: Job? = null

  private companion object {
    const val NOTIFICATION_ID = 1001
  }

  override fun onCreate() {
    super.onCreate()
    val exo = ExoPlayer.Builder(this)
      .setAudioAttributes(
        AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
        true,
      )
      .setWakeMode(C.WAKE_MODE_NETWORK)
      .setHandleAudioBecomingNoisy(true)
      .build()
    player = exo
    PlaybackEnhancements.attach(exo)

    exo.addListener(object : Player.Listener {
      override fun onAudioSessionIdChanged(audioSessionId: Int) {
        EqualizerController.attach(audioSessionId)
      }

      override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
        commitListening()
        mediaItem?.mediaId?.let {
          currentTrackId = it
          AuraApp.container.listening.recordHistory(it)
        }
      }

      override fun onIsPlayingChanged(isPlaying: Boolean) {
        if (isPlaying) {
          listeningSince = System.currentTimeMillis()
        } else {
          commitListening()
          persistQueue()
          publishWidget()
        }
        publishWidget()
      }

      override fun onEvents(player: Player, events: Player.Events) {
        if (events.contains(Player.EVENT_MEDIA_ITEM_TRANSITION) || events.contains(Player.EVENT_TIMELINE_CHANGED) ||
          events.contains(Player.EVENT_PLAYBACK_STATE_CHANGED)
        ) {
          publishWidget()
          persistQueueThrottled()
        }
      }

      override fun onPlaybackParametersChanged(playbackParameters: androidx.media3.common.PlaybackParameters) = Unit
    })

    if (exo.audioSessionId != C.AUDIO_SESSION_ID_UNSET) EqualizerController.attach(exo.audioSessionId)

    val intent = Intent(this, MainActivity::class.java)
    val pending = PendingIntent.getActivity(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
    session = MediaSession.Builder(this, exo).setSessionActivity(pending).build()
    // Reuse the app's low-importance channel so the notification obeys the user's
    // channel settings instead of the library's default channel.
    setMediaNotificationProvider(
      DefaultMediaNotificationProvider.Builder(this)
        .setChannelId(AuraApp.CHANNEL_ID)
        .setChannelName(R.string.notification_channel_playback)
        .setNotificationId(NOTIFICATION_ID)
        .build(),
    )
    startTicker()
  }

  private fun startTicker() {
    ticker?.cancel()
    ticker = scope.launch {
      while (isActive) {
        delay(400)
        val exo = player ?: continue
        val sleep = SleepTimer.state.value
        if (sleep.endsAt != null) {
          val remaining = sleep.remainingMs() ?: 0
          if (remaining <= SleepTimer.fadeWindowMs) {
            SleepTimer.markFading(true)
            val factor = SleepTimer.fadeFactor()
            exo.volume = (PlaybackEnhancements.state.value.volume * factor).coerceIn(0f, 1f)
          }
          if (remaining <= 0) {
            exo.pause()
            SleepTimer.cancel()
            SleepTimer.markFading(false)
            exo.volume = PlaybackEnhancements.state.value.volume
          }
        } else if (sleep.atTrackEnd && exo.playbackState == Player.STATE_ENDED) {
          exo.pause()
          SleepTimer.cancel()
        }
        // A cancelled timer (or a finished fade) must never leave the player quiet.
        if (sleep.endsAt == null && !sleep.fading) {
          val target = PlaybackEnhancements.state.value.volume
          if (kotlin.math.abs(exo.volume - target) > 0.001f) exo.volume = target
        }
        val loop = PlaybackEnhancements.loopTarget(exo.currentPosition)
        if (loop != null && exo.isPlaying) exo.seekTo(loop)
        if (exo.isPlaying) persistQueueThrottled(force = false)
      }
    }
  }

  private var lastWidgetPublish = 0L

  private fun publishWidget() {
    val exo = player ?: return
    val now = System.currentTimeMillis()
    if (now - lastWidgetPublish < 700) return
    lastWidgetPublish = now
    val metadata = exo.currentMediaItem?.mediaMetadata
    val artwork = metadata?.artworkUri?.takeIf { it.scheme == "file" }?.toString()
    NowPlayingWidget.update(
      this,
      metadata?.title?.toString().orEmpty(),
      metadata?.artist?.toString().orEmpty(),
      artwork,
      exo.isPlaying,
    )
  }

  private fun commitListening() {
    val id = currentTrackId
    val since = listeningSince
    if (id == null || since == 0L) {
      listeningSince = 0
      return
    }
    val delta = (System.currentTimeMillis() - since).coerceAtLeast(0)
    listeningSince = if (player?.isPlaying == true) System.currentTimeMillis() else 0
    listenedMs += delta
    if (delta > 1_000) {
      // Player state is only read on this (main) thread; the database write happens off it.
      val duration = player?.duration?.takeIf { it > 0 } ?: 0
      val position = player?.currentPosition ?: 0
      val completed = duration > 0 && position >= duration - 3000
      scope.launch(Dispatchers.IO) { AuraApp.container.library.recordPlay(id, delta, completed) }
    }
  }

  private fun persistQueueThrottled(force: Boolean = true) {
    val now = System.currentTimeMillis()
    if (!force && now - lastSave < 10_000) return
    lastSave = now
    persistQueue()
  }

  private fun persistQueue() {
    val exo = player ?: return
    val ids = (0 until exo.mediaItemCount).map { exo.getMediaItemAt(it).mediaId }
    val index = exo.currentMediaItemIndex.coerceAtLeast(0)
    val position = exo.currentPosition.coerceAtLeast(0)
    val current = exo.currentMediaItem?.mediaId
    scope.launch(Dispatchers.IO) {
      AuraApp.container.library.saveQueue(ids, index, position)
      if (current != null) AuraApp.container.listening.rememberResume(current, position, ids)
    }
  }

  override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

  override fun onTaskRemoved(rootIntent: Intent?) {
    val exo = player
    if (exo == null || !exo.playWhenReady) {
      persistQueue()
      stopSelf()
    }
  }

  override fun onDestroy() {
    commitListening()
    persistQueue()
    ticker?.cancel()
    scope.cancel()
    PlaybackEnhancements.detach()
    EqualizerController.release()
    session?.release()
    session = null
    player?.release()
    player = null
    super.onDestroy()
  }
}
