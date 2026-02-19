package com.grbl.cnc.ui

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat

@Suppress("DEPRECATION")
class StreamKeepAliveService : Service() {
    private val chanelId = "cnc_stream_chanel"
    private val notifId = 101

    override fun onCreate() {
        super.onCreate()
        createChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            "START" -> {
                startForeground(notifId,createNotification("Streaming...", 0))
            }

            "UPDATE" -> {
                val progress = intent.getIntExtra("progress", 0)
                updateNotification(progress)
            }

            "STOP" -> {
                stopForeground(true)
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    fun updateNotification(progress: Int) {
        val manager = getSystemService(NotificationManager::class.java)

        manager.notify(notifId,createNotification("Streaming...$progress%",progress))
    }

    private fun createNotification(text: String, progress: Int): Notification {
        return NotificationCompat.Builder(this, chanelId)
            .setContentTitle("CNC Controller")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.stat_sys_upload)
            .setProgress(100, progress,false)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .build()
    }

    private fun createChannel(){
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(chanelId, "CNC Streaming",
            NotificationManager.IMPORTANCE_LOW
            )

            val manager =getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null
}