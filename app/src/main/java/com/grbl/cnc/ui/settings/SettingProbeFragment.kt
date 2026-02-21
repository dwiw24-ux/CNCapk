package com.grbl.cnc.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.SettingAdapter

class SettingProbeFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var prefs: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val list = listOf(
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Probe Feed",
                "Current Speed when probing : ${prefs.getInt("probe_feed", 100)} (mm/min)",
                "probe_feed"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Probe Distance",
                "Current Maximum probing distance : ${prefs.getFloat("probe_dist", 50f)} (mm)",
                "probe_dist"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Probe Plate Thickness",
                "Current Thickness of plate : ${prefs.getFloat("probe_plate", 1.5f)} (mm)",
                "probe_plate"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Probe Retract",
                "Current Retract height after touch : ${prefs.getFloat("probe_retract", 5f)} (mm)",
                "probe_retract"
            )
        )

        val adapter = SettingAdapter(list) { item ->
            showNumberDialog(item)
        }

        val rv = view.findViewById<RecyclerView>(R.id.rvSettings)
        rv.layoutManager = LinearLayoutManager(requireContext())
        rv.adapter = adapter
        rv.addItemDecoration(
            DividerItemDecoration(requireContext(), DividerItemDecoration.VERTICAL)
        )
    }

    private fun showNumberDialog(item: SettingItem) {

        val editText = EditText(requireContext())
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        val currentValue = when (item.key) {
            "probe_feed" -> prefs.getInt(item.key, 100).toString()
            else -> prefs.getFloat(item.key, 0f).toString()
        }

        editText.setText(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->

                val value = editText.text.toString()

                prefs.edit().apply {

                    if (item.key == "probe_feed") {
                        putInt(item.key, value.toIntOrNull() ?: 100)
                    } else {
                        putFloat(item.key, value.toFloatOrNull() ?: 0f)
                    }

                    apply()
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}