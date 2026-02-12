package com.grbl.cnc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

        supportFragmentManager.beginTransaction()
            .replace(android.R.id.content, AppAboutFragment())
            .commit()

        onBackPressedDispatcher.addCallback(this) {
            startActivity(Intent(this@AboutActivity, MainActivity::class.java))
            finish()
        }
    }
}