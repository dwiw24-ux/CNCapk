package com.grbl.cnc.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.grbl.cnc.R
import com.grbl.cnc.ui.settings.SettingJogFragment
import com.grbl.cnc.ui.settings.SettingMenuFragment
import com.grbl.cnc.ui.settings.SettingProbeFragment

class SettingActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

        setContentView(R.layout.activity_setting)

        val toolbar = findViewById<Toolbar>(R.id.toolbarSetting)
        setSupportActionBar(toolbar)

        supportActionBar?.title = "Settings"
        supportActionBar?.setDisplayHomeAsUpEnabled(true)

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingContainer, SettingMenuFragment())
                .commit()
        }

        supportFragmentManager.addOnBackStackChangedListener {
            updateTitle()
        }
    }

    private fun updateTitle() {
        val fragment = supportFragmentManager
            .findFragmentById(R.id.settingContainer)

        supportActionBar?.title = when (fragment) {
            is SettingMenuFragment -> "Settings"
            is SettingProbeFragment -> "Probe Settings"
            is SettingJogFragment -> "Jog Settings"
            else -> "Settings"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}