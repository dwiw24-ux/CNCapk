package com.grbl.cnc

import android.R
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat

@Suppress("DEPRECATION")
class StreamKeepAliveService : Service() {

    companion object {
        const val CHANNEL_ID = "cnc_stream_channel"
        const val NOTIF_ID = 1001
        const val ACTION_STOP = "STOP_SERVICE"
        const val ACTION_UPDATE_PROGRESS = "UPDATE_PROGRESS"
        const val EXTRA_PROGRESS = "progress"
        const val EXTRA_FILENAME = "filename"

        // WakeLock per-acquire: 10 menit.
        // Diperbarui setiap kali ada update progress dari FileFragment,
        // sehingga tidak ada batas total durasi streaming.
        private const val WAKELOCK_RENEW_MS = 10 * 60 * 1000L
    }

    private var currentFileName: String = ""
    private var wakeLock: PowerManager.WakeLock? = null

    // =============================
    // LIFECYCLE
    // =============================

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        when (intent?.action) {

            // ⭐ STOP → hentikan service
            ACTION_STOP -> {
                releaseWakeLock()
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                return START_NOT_STICKY
            }

            // ⭐ UPDATE PROGRESS → perbarui notifikasi + refresh WakeLock
            ACTION_UPDATE_PROGRESS -> {
                intent.getStringExtra(EXTRA_FILENAME)?.let {
                    currentFileName = it
                }
                val progress = intent.getIntExtra(EXTRA_PROGRESS, 0)
                updateProgressNotification(progress)
                return START_STICKY
            }

            // ⭐ Default start (Idle Mode)
            else -> {
                startForeground(NOTIF_ID, buildIdleNotification())
                return START_STICKY
            }
        }
    }

    override fun onDestroy() {
        releaseWakeLock()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    // =============================
    // WAKELOCK CONTROL
    // =============================

    /**
     * Selalu release dulu lalu acquire ulang dengan timeout baru.
     * Dipanggil setiap kali ada update progress → WakeLock terus
     * diperbarui selama streaming aktif, tanpa batas total durasi.
     *
     * Jika streaming berhenti (tidak ada update progress masuk),
     * WakeLock akan otomatis expire setelah WAKELOCK_RENEW_MS —
     * ini justru perilaku yang diinginkan sebagai safety net.
     */
    private fun renewWakeLock() {
        // Release dulu agar tidak double-held
        wakeLock?.let { if (it.isHeld) it.release() }

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "CNC::StreamWakeLock"
        )
        wakeLock?.acquire(WAKELOCK_RENEW_MS)
    }

    private fun releaseWakeLock() {
        wakeLock?.let { if (it.isHeld) it.release() }
        wakeLock = null
    }

    // =============================
    // NOTIFICATION
    // =============================

    private fun updateProgressNotification(progress: Int) {
        if (progress >= 100) {
            // Streaming selesai → lepas WakeLock, kembali ke idle
            releaseWakeLock()
            showIdleNotification()
            return
        }

        // Streaming aktif → refresh WakeLock setiap update progress
        renewWakeLock()

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("File Streaming")
            .setContentText("$currentFileName • $progress%")
            .setSmallIcon(R.drawable.stat_sys_upload)
            .setProgress(100, progress, false) //on off progress
            .setOngoing(true)
            .build()

        val manager = getSystemService(NotificationManager::class.java)
        manager.notify(NOTIF_ID, notification)
    }

    private fun buildIdleNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("CNC Controller")
            .setContentText("Aplikasi Aktif")
            .setSmallIcon(R.drawable.stat_sys_download)
            .setOngoing(true)
            .build()
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
}