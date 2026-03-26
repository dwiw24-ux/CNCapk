package com.grbl.cnc

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar


class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

        setContentView(R.layout.activity_setting)

        val toolbar = findViewById<Toolbar>(R.id.toolbarSetting)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = "About Us"
            setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingContainer, AppAboutFragment())
                .commit()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}