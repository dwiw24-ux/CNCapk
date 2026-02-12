package com.grbl.cnc.ui

import android.os.Bundle
import androidx.preference.Preference
import androidx.preference.PreferenceFragmentCompat
import com.grbl.cnc.R

class AppAboutFragment : PreferenceFragmentCompat() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.activity_about, rootKey)

        val version = requireActivity()
            .packageManager
            .getPackageInfo(requireActivity().packageName, 0)
            .versionName

        findPreference<Preference>("pref_app_version")
            ?.summary = version
    }
}