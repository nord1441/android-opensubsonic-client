package com.opensubsonic.client.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.opensubsonic.client.R
import com.opensubsonic.client.data.model.ServerConfig
import com.opensubsonic.client.ui.MainActivity
import com.opensubsonic.client.util.DownloadManager
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class DownloadService : Service() {

    @Inject lateinit var downloadManager: DownloadManager

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var isRunning = false

    companion object {
        private const val CHANNEL_ID = "subtune_download"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_SERVER_URL = "server_url"
        private const val EXTRA_SERVER_USERNAME = "server_username"
        private const val EXTRA_SERVER_PASSWORD = "server_password"

        fun start(context: Context, server: ServerConfig) {
            val intent = Intent(context, DownloadService::class.java).apply {
                putExtra(EXTRA_SERVER_URL, server.url)
                putExtra(EXTRA_SERVER_USERNAME, server.username)
                putExtra(EXTRA_SERVER_PASSWORD, server.password)
            }
            context.startForegroundService(intent)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (isRunning) return START_NOT_STICKY

        val url = intent?.getStringExtra(EXTRA_SERVER_URL) ?: run { stopSelf(); return START_NOT_STICKY }
        val username = intent.getStringExtra(EXTRA_SERVER_USERNAME) ?: run { stopSelf(); return START_NOT_STICKY }
        val password = intent.getStringExtra(EXTRA_SERVER_PASSWORD) ?: run { stopSelf(); return START_NOT_STICKY }

        val server = ServerConfig(url = url, username = username, password = password)

        isRunning = true
        startForeground(NOTIFICATION_ID, buildNotification("Preparing download..."))

        serviceScope.launch {
            try {
                // Observe progress and update notification
                val progressJob = launch {
                    downloadManager.bulkDownloadState.collect { state ->
                        if (state.isDownloading) {
                            val text = "${state.completedTracks} / ${state.totalTracks} tracks"
                            val detail = state.currentTrackName ?: ""
                            updateNotification(text, detail, state.completedTracks, state.totalTracks)
                        }
                    }
                }

                downloadManager.downloadAllAlbums(server)
                progressJob.cancel()
            } finally {
                isRunning = false
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        }

        return START_NOT_STICKY
    }

    override fun onDestroy() {
        serviceScope.cancel()
        super.onDestroy()
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Downloads",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Bulk download progress"
            setShowBadge(false)
        }
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(channel)
    }

    private fun buildNotification(text: String): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading library")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun updateNotification(text: String, detail: String, progress: Int, max: Int) {
        val pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Downloading library")
            .setContentText(text)
            .setSubText(detail)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .setProgress(max, progress, false)
            .setContentIntent(pendingIntent)
            .build()
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, notification)
    }
}
