package com.hotsteel.aurapulse.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.RemoteViews
import com.hotsteel.aurapulse.MainActivity
import com.hotsteel.aurapulse.R
import java.io.File
import org.json.JSONObject

/** Small home-screen surface that mirrors the service snapshot without waking the player. */
class NowPlayingWidget : AppWidgetProvider() {
  override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
    ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
  }

  override fun onReceive(context: Context, intent: Intent) {
    super.onReceive(context, intent)
    if (intent.action == ACTION_REFRESH) {
      val manager = AppWidgetManager.getInstance(context)
      val ids = manager.getAppWidgetIds(ComponentName(context, NowPlayingWidget::class.java))
      ids.forEach { id -> manager.updateAppWidget(id, build(context)) }
    }
  }

  companion object {
    const val ACTION_REFRESH = "com.hotsteel.aurapulse.WIDGET_REFRESH"
    private const val PREFS = "aurapulse_widget"

    /** Called by the playback service whenever the current item changes. */
    fun update(context: Context, title: String, artist: String, artworkPath: String?, playing: Boolean) {
      context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("title", title)
        .putString("artist", artist)
        .putString("art", artworkPath)
        .putBoolean("playing", playing)
        .apply()
      context.sendBroadcast(Intent(context, NowPlayingWidget::class.java).setAction(ACTION_REFRESH))
    }

    fun build(context: Context): RemoteViews {
      val views = RemoteViews(context.packageName, R.layout.now_playing_widget)
      val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
      val title = prefs.getString("title", null)?.takeIf { it.isNotBlank() }
      val artist = prefs.getString("artist", null).orEmpty()
      val playing = prefs.getBoolean("playing", false)
      views.setTextViewText(R.id.widget_title, title ?: context.getString(R.string.widget_empty))
      views.setTextViewText(R.id.widget_artist, if (title == null) context.getString(R.string.app_name) else artist)
      views.setImageViewResource(R.id.widget_toggle, if (playing) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play)
      views.setContentDescription(R.id.widget_toggle, context.getString(if (playing) R.string.widget_pause else R.string.widget_play))

      prefs.getString("art", null)?.let { path ->
        val file = File(Uri.parse(path).path.orEmpty())
        if (file.isFile) {
          val bitmap = decode(file)
          if (bitmap != null) views.setImageViewBitmap(R.id.widget_art, bitmap) else views.setImageViewResource(R.id.widget_art, android.R.drawable.ic_media_play)
        }
      }

      views.setOnClickPendingIntent(R.id.widget_root, launch(context))
      views.setOnClickPendingIntent(R.id.widget_toggle, mediaAction(context, if (playing) "pause" else "play", 1))
      views.setOnClickPendingIntent(R.id.widget_next, mediaAction(context, "next", 2))
      return views
    }

    private fun decode(file: File): Bitmap? = runCatching {
      val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
      BitmapFactory.decodeFile(file.absolutePath, bounds)
      var sample = 1
      while (bounds.outWidth / sample > 256) sample *= 2
      BitmapFactory.decodeFile(file.absolutePath, BitmapFactory.Options().apply { inSampleSize = sample })
    }.getOrNull()

    private fun launch(context: Context): PendingIntent = PendingIntent.getActivity(
      context, 0, Intent(context, MainActivity::class.java),
      PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun mediaAction(context: Context, action: String, request: Int): PendingIntent {
      val intent = Intent(context, MediaButtonReceiver::class.java).setAction(MediaButtonReceiver.ACTION)
        .putExtra(MediaButtonReceiver.EXTRA, action)
      return PendingIntent.getBroadcast(
        context, request, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
      )
    }
  }
}
