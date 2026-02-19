package com.grbl.cnc.ui

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat

@Suppress("DEPRECATION")
class StreamKeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "cnc_stream_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_UPDATE_PROGRESS = "UPDATE_PROGRESS"
        const val EXTRA_PROGRESS = "progress"
    }

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ⭐ kalau intent STOP → hentikan service
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // ⭐ intent untuk stop saat notif swipe
        val deleteIntent = Intent(this, StreamKeepAliveService::class.java).apply {
            action = ACTION_STOP
        }

        val deletePendingIntent = PendingIntent.getService(
            this, 0, deleteIntent, PendingIntent.FLAG_IMMUTABLE
        )

        if (intent?.action == ACTION_UPDATE_PROGRESS) {

            val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
            updateProgressNotification(progress)
            return START_STICKY
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CNC Controller")
            .setContentText("Aplikasi Aktif")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(false)
            .setDeleteIntent(deletePendingIntent)
            .build()

        startForeground(NOTIF_ID, notification)
        return START_STICKY
    }

    private fun createChannel(){
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "CNC Streaming",
            NotificationManager.IMPORTANCE_LOW
            )

            val manager =getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun updateProgressNotification(progress: Int) {
        if (progress >= 100) {
            showIdleNotification()
            return
        }

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Streaming")
            .setContentText("Progress $progress%")
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setOngoing(true)
            .setProgress(100, progress, false)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun showIdleNotification() {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CNC Controller")
            .setContentText("Aplikasi Aktif")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification)
    }

}