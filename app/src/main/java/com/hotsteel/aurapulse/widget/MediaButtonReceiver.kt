package com.hotsteel.aurapulse.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioManager
import android.view.KeyEvent

/**
 * Widget taps are turned into real media-key events, which the active
 * MediaSession handles exactly like a headset button.
 */
class MediaButtonReceiver : BroadcastReceiver() {
  override fun onReceive(context: Context, intent: Intent) {
    if (intent.action != ACTION) return
    val key = when (intent.getStringExtra(EXTRA)) {
      "play" -> KeyEvent.KEYCODE_MEDIA_PLAY
      "pause" -> KeyEvent.KEYCODE_MEDIA_PAUSE
      "toggle" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
      "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
      "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
      else -> return
    }
    val audio = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, key))
    audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, key))
  }

  companion object {
    const val ACTION = "com.hotsteel.aurapulse.MEDIA_ACTION"
    const val EXTRA = "action"
  }
}
