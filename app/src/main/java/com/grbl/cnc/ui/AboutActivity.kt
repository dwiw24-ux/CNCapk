package com.grbl.cnc.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.addCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.view.WindowCompat
import com.grbl.cnc.R
import com.grbl.cnc.ui.AppAboutFragment
import com.grbl.cnc.ui.settings.SettingJogFragment
import com.grbl.cnc.ui.settings.SettingMenuFragment
import com.grbl.cnc.ui.settings.SettingProbeFragment



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