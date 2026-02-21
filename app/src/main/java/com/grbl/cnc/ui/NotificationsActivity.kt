package com.grbl.cnc.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.grbl.cnc.R

class NotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

        setContentView(R.layout.activity_notifications)

        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
    }
}