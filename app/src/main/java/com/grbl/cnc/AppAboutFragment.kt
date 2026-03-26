package com.grbl.cnc

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat

class AppAboutFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.activity_about, rootKey)

        val version = try {
            requireContext()
                .packageManager
                .getPackageInfo(requireContext().packageName, 0)
                .versionName
        } catch (_: Exception) {
            "Unknown"
        }

        findPreference<Preference>("pref_app_version")
            ?.summary = version
    }
}