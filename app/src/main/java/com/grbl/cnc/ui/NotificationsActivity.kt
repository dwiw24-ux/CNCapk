package com.grbl.cnc.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import com.grbl.cnc.R

class NotificationsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)
        setContentView(R.layout.activity_notifications)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        findViewById<Button>(R.id.btnNotificationsBack).setOnClickListener {
            val intent = Intent(this, MainActivity::class.java)
            startActivity(intent)
            finish()
        }
    }

    @Deprecated("This method has been deprecated in favor of using the\n" +
            "{@link OnBackPressedDispatcher} via {@link #getOnBackPressedDispatcher()}.\n" +
            "The OnBackPressedDispatcher controls how back button events are dispatched\n" +
            "to one or more {@link OnBackPressedCallback} objects.")
    @SuppressLint("MissingSuperCall", "GestureBackNavigation")
    override fun onBackPressed() {
        val intent = Intent(this, MainActivity::class.java)
        startActivity(intent)
        finish()
    }
}