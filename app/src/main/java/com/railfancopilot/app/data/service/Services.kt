package com.railfancopilot.app.data.service

import android.app.*
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer

// ── Scanner Service ───────────────────────────────────────────────────────────

class ScannerService : Service() {

    private var player: ExoPlayer? = null
    private val CHANNEL_ID = "scanner_channel"
    private val NOTIF_ID = 1001

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        player = ExoPlayer.Builder(this).build()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val streamUrl = intent?.getStringExtra(EXTRA_STREAM_URL) ?: return START_NOT_STICKY
        val channelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Railroad Scanner"

        startForeground(NOTIF_ID, buildNotification(channelName))
        playStream(streamUrl)
        return START_STICKY
    }

    private fun playStream(url: String) {
        player?.apply {
            stop()
            setMediaItem(MediaItem.fromUri(url))
            prepare()
            play()
        }
    }

    private fun createNotificationChannel() {
        val channel = NotificationChannel(
            CHANNEL_ID, "Scanner Playback",
            NotificationManager.IMPORTANCE_LOW
        ).apply { description = "Railroad radio scanner streaming" }
        getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
    }

    private fun buildNotification(channelName: String): Notification =
        NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("RailFan Scanner")
            .setContentText("Streaming: $channelName")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setOngoing(true)
            .build()

    override fun onDestroy() {
        player?.release()
        player = null
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val EXTRA_STREAM_URL = "stream_url"
        const val EXTRA_CHANNEL_NAME = "channel_name"

        fun start(context: Context, streamUrl: String, channelName: String) {
            val intent = Intent(context, ScannerService::class.java).apply {
                putExtra(EXTRA_STREAM_URL, streamUrl)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
            }
            context.startForegroundService(intent)
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, ScannerService::class.java))
        }
    }
}
