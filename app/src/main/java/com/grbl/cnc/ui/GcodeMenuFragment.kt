package com.grbl.cnc.ui

import android.os.Bundle
import android.view.View
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.SettingAdapter
import com.grbl.cnc.ui.settings.SettingItem
import com.grbl.cnc.ui.settings.SettingJogFragment
import com.grbl.cnc.ui.settings.SettingProbeFragment

class GcodeMenuFragment : Fragment(R.layout.fragment_settings) {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val list = listOf(
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Gcode Creator",
                "No Description",
                "creator"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Gcode Editor",
                "No Description",
                "editor"
            )
        )

        val adapter = SettingAdapter(list) { item ->
            when (item.key) {
                "creator" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.settingContainer, GcodeCreatorFragment())
                        .addToBackStack(null)
                        .commit()
                }

                "editor" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.settingContainer, GcodeEditorFragment())
                        .addToBackStack(null)
                        .commit()
                }
            }
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvSettings)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }
}