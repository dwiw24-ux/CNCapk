package com.grbl.cnc.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
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

    private var wakeLock: PowerManager.WakeLock? = null

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        // ⭐ kalau intent STOP → hentikan service
        if (intent?.action == ACTION_STOP) {
            releaseWakeLock()
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        // ⭐ Update progress
        if (intent?.action == ACTION_UPDATE_PROGRESS) {
            val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
            updateProgressNotification(progress)
            return START_STICKY
        }

        // ⭐ Default start (Idle Mode)
        startForeground(NOTIF_ID, buildIdleNotification())
        return START_STICKY
    }

    // =============================
    // WAKELOCK CONTROL
    // =============================

    private fun acquireWakeLock() {
        if (wakeLock?.isHeld == true) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CNC::StreamWakeLock"
        )
        wakeLock?.acquire()
    }

    private fun releaseWakeLock() {
        wakeLock?.let {
            if (it.isHeld) it.release()
        }
        wakeLock = null
    }

    // =============================
    // NOTIFICATION
    // =============================

    private fun buildIdleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CNC Controller")
            .setContentText("Aplikasi Aktif")
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
    }

    private fun updateProgressNotification(progress: Int) {

        if (progress >= 100) {
            releaseWakeLock()
            showIdleNotification()
            return
        }

    // ⭐ Streaming aktif → nyalakan WakeLock
    acquireWakeLock()

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

private fun showIdleNotification() {
    val manager = getSystemService(NotificationManager::class.java)
    manager.notify(NOTIF_ID, buildIdleNotification())
}

    // =============================
    // CHANNEL
    // =============================

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "CNC Streaming",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}