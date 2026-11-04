package com.example.audio

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import com.example.data.library.ListeningStore

/** Persists listening progress while the media service plays, even after the activity is closed. */
class ServiceListeningTracker(context: Context, private val player: Player) : Player.Listener {
  private val listening = ListeningStore.get(context)
  private val handler = Handler(Looper.getMainLooper())
  private val ticker = object : Runnable {
    override fun run() {
      savePosition()
      schedule()
    }
  }

  init {
    player.addListener(this)
    if (player.isPlaying) recordCurrent()
    schedule()
  }

  override fun onIsPlayingChanged(isPlaying: Boolean) {
    if (isPlaying) recordCurrent() else savePosition()
    schedule()
  }

  override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
    if (mediaItem != null && player.playWhenReady) listening.recordTrack(mediaItem.mediaId)
    savePosition()
    schedule()
  }

  override fun onPositionDiscontinuity(oldPosition: Player.PositionInfo, newPosition: Player.PositionInfo, reason: Int) {
    if (!player.isPlaying || reason == Player.DISCONTINUITY_REASON_SEEK) savePosition()
  }

  override fun onPlaybackStateChanged(playbackState: Int) {
    if (playbackState == Player.STATE_ENDED) savePosition()
    schedule()
  }

  private fun recordCurrent() {
    player.currentMediaItem?.mediaId?.let(listening::recordTrack)
    savePosition()
  }

  private fun savePosition() {
    val id = player.currentMediaItem?.mediaId ?: return
    val position = if (player.isCurrentMediaItemSeekable && player.playbackState != Player.STATE_ENDED) player.currentPosition else 0L
    listening.recordPosition(id, position)
  }

  private fun schedule() {
    handler.removeCallbacks(ticker)
    if (player.isPlaying) handler.postDelayed(ticker, 5_000L)
  }

  fun close() {
    handler.removeCallbacks(ticker)
    savePosition()
    player.removeListener(this)
  }
}
