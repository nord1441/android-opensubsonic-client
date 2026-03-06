package com.opensubsonic.client.widget

import android.app.Service
import android.content.ComponentName
import android.content.Intent
import android.os.IBinder
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import com.opensubsonic.client.service.PlaybackService

class WidgetActionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == null) {
            stopSelf()
            return START_NOT_STICKY
        }

        val sessionToken = SessionToken(this, ComponentName(this, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(this, sessionToken).buildAsync()

        controllerFuture.addListener({
            try {
                val controller = controllerFuture.get()
                when (action) {
                    PlaybackWidgetProvider.ACTION_PLAY_PAUSE -> {
                        if (controller.isPlaying) controller.pause()
                        else controller.play()
                    }
                    PlaybackWidgetProvider.ACTION_NEXT -> controller.seekToNext()
                    PlaybackWidgetProvider.ACTION_PREVIOUS -> controller.seekToPrevious()
                }
                // Update widget state after action
                val title = controller.mediaMetadata.title?.toString()
                val artist = controller.mediaMetadata.artist?.toString()
                PlaybackWidgetProvider.updateWidget(this, title, artist, controller.isPlaying)

                MediaController.releaseFuture(controllerFuture)
            } catch (_: Exception) {}
            stopSelf()
        }, MoreExecutors.directExecutor())

        return START_NOT_STICKY
    }
}
