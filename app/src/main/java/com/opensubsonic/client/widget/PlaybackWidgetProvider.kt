package com.opensubsonic.client.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.opensubsonic.client.R
import com.opensubsonic.client.ui.MainActivity

class PlaybackWidgetProvider : AppWidgetProvider() {

    companion object {
        const val ACTION_PLAY_PAUSE = "com.opensubsonic.client.widget.PLAY_PAUSE"
        const val ACTION_NEXT = "com.opensubsonic.client.widget.NEXT"
        const val ACTION_PREVIOUS = "com.opensubsonic.client.widget.PREVIOUS"

        private const val PREFS_NAME = "widget_state"
        private const val KEY_TITLE = "title"
        private const val KEY_ARTIST = "artist"
        private const val KEY_IS_PLAYING = "is_playing"

        fun updateWidget(context: Context, title: String?, artist: String?, isPlaying: Boolean) {
            // Save state for future onUpdate calls
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit()
                .putString(KEY_TITLE, title)
                .putString(KEY_ARTIST, artist)
                .putBoolean(KEY_IS_PLAYING, isPlaying)
                .apply()

            val appWidgetManager = AppWidgetManager.getInstance(context)
            val widgetIds = appWidgetManager.getAppWidgetIds(
                ComponentName(context, PlaybackWidgetProvider::class.java)
            )
            if (widgetIds.isEmpty()) return

            val views = buildRemoteViews(context, title, artist, isPlaying)
            for (id in widgetIds) {
                appWidgetManager.updateAppWidget(id, views)
            }
        }

        private fun buildRemoteViews(
            context: Context,
            title: String?,
            artist: String?,
            isPlaying: Boolean
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_playback)

            // Set text
            views.setTextViewText(R.id.widget_title, title ?: "SubTune")
            views.setTextViewText(R.id.widget_artist, artist ?: "Not playing")

            // Set play/pause icon
            views.setImageViewResource(
                R.id.widget_play_pause,
                if (isPlaying) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )

            // Open app on tap
            val openAppIntent = Intent(context, MainActivity::class.java)
            val openAppPi = PendingIntent.getActivity(
                context, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_title, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_artist, openAppPi)
            views.setOnClickPendingIntent(R.id.widget_cover_art, openAppPi)

            // Control buttons
            views.setOnClickPendingIntent(R.id.widget_play_pause, makeActionPi(context, ACTION_PLAY_PAUSE, 1))
            views.setOnClickPendingIntent(R.id.widget_next, makeActionPi(context, ACTION_NEXT, 2))
            views.setOnClickPendingIntent(R.id.widget_prev, makeActionPi(context, ACTION_PREVIOUS, 3))

            return views
        }

        private fun makeActionPi(context: Context, action: String, requestCode: Int): PendingIntent {
            val intent = Intent(context, PlaybackWidgetProvider::class.java).apply {
                this.action = action
            }
            return PendingIntent.getBroadcast(
                context, requestCode, intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
        }
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val title = prefs.getString(KEY_TITLE, null)
        val artist = prefs.getString(KEY_ARTIST, null)
        val isPlaying = prefs.getBoolean(KEY_IS_PLAYING, false)

        val views = buildRemoteViews(context, title, artist, isPlaying)
        for (id in appWidgetIds) {
            appWidgetManager.updateAppWidget(id, views)
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)

        when (intent.action) {
            ACTION_PLAY_PAUSE, ACTION_NEXT, ACTION_PREVIOUS -> {
                // Forward to the playback service via a media button intent
                val serviceIntent = Intent(context, WidgetActionService::class.java).apply {
                    action = intent.action
                }
                context.startService(serviceIntent)
            }
        }
    }
}
