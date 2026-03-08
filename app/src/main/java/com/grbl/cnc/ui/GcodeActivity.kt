package com.grbl.cnc.ui

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import com.grbl.cnc.R
import com.grbl.cnc.ui.settings.SettingJogFragment
import com.grbl.cnc.ui.settings.SettingMenuFragment
import com.grbl.cnc.ui.settings.SettingProbeFragment

class GcodeActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate( savedInstanceState)

        setContentView(R.layout.activity_setting)

        val toolbar = findViewById<Toolbar>(R.id.toolbarSetting)
        setSupportActionBar(toolbar)

        supportActionBar?.apply {
            title = "Gcode Menu"
            setDisplayHomeAsUpEnabled(true)
        }

        if (savedInstanceState == null) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.settingContainer, GcodeMenuFragment())
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
            is GcodeMenuFragment -> "Gcode Menu"
            is GcodeCreatorFragment -> "Gcode Creator"
            is GcodeEditorFragment -> "Gcode Editor"
            else -> "Gcode Menu"
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}