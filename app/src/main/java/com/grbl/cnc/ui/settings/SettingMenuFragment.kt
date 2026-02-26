package com.grbl.cnc.ui.settings

import android.content.Context
import android.os.Bundle
import android.view.View
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.SettingAdapter

class SettingMenuFragment : Fragment(R.layout.fragment_settings) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val prefs = requireContext().getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)
        val list = listOf(
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Setting App Name",
                "Current app name : ${
                    prefs.getString("app_name", "GRBL Bluetooth")}",
                "appName"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Probe Setting",
                "Feed:${prefs.getInt("probe_feed", 100)}mm/min|" +
                        "Dist:${prefs.getFloat("probe_dist", 50f)}mm|" +
                        "Plate:${prefs.getFloat("probe_plate", 1.5f)}mm|" +
                        "Retr:${prefs.getFloat("probe_retract", 5f)}mm",
                "probe"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jog Setting",
                "No Description",
                "jog"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Spindle Start Delay",
                "Delay: ${prefs.getInt("spindle_delay", 2)} sec",
                "spindle"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Status Polling Interval",
                "Interval: ${prefs.getInt("polling_interval", 100)} ms",
                "polling"
            )
        )

        val adapter = SettingAdapter(list) { item ->
            when (item.key) {
                "appName" -> {
                    appNameDialog()
                }
                "probe" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.settingContainer, SettingProbeFragment())
                        .addToBackStack(null)
                        .commit()
                }
                "jog" -> {
                    parentFragmentManager.beginTransaction()
                        .replace(R.id.settingContainer, SettingJogFragment())
                        .addToBackStack(null)
                        .commit()
                }
                "spindle" -> {
                    spindleDelayDialog()
                }
                "polling" -> {
                    showPollingDialog()
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

    private fun showPollingDialog() {
        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val options = arrayOf("100 ms", "150 ms", "200 ms", "250 ms","300 ms")
        val values = arrayOf(100, 150, 200, 250, 300)

        val current = prefs.getInt("polling_interval", 100)
        val selectedIndex = values.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Polling Interval")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->

                prefs.edit()
                    .putInt("polling_interval", values[which])
                    .apply()

                dialog.dismiss()

                // Refresh fragment supaya deskripsi update
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingContainer, SettingMenuFragment())
                    .commit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
    private fun spindleDelayDialog() {

        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val options = arrayOf("0 sec", "1 sec", "2 sec", "3 sec", "4 sec", "5 sec")
        val values = arrayOf(0, 1, 2, 3, 4, 5)

        val current = prefs.getInt("spindle_delay", 2)
        val selectedIndex = values.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(requireContext())
            .setTitle("Select Spindle Start Delay")
            .setSingleChoiceItems(options, selectedIndex) { dialog, which ->

                prefs.edit()
                    .putInt("spindle_delay", values[which])
                    .apply()

                dialog.dismiss()

                // Refresh fragment supaya deskripsi update
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingContainer, SettingMenuFragment())
                    .commit()
            }
            .setNegativeButton("Cancel", null)
            .show()

    }

    private fun appNameDialog() {
        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val editText = EditText(requireContext())
        editText.setText(prefs.getString("app_name", "GRBL Bluetooth"))
        editText.setSelection(editText.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Set App Name")
            .setView(editText)
            .setPositiveButton("Save") { dialog, _ ->

                val name = editText.text.toString().trim()

                if (name.isNotEmpty()) {
                    prefs.edit()
                        .putString("app_name", name)
                        .apply()
                }

                dialog.dismiss()

                // Refresh fragment supaya deskripsi update
                parentFragmentManager.beginTransaction()
                    .replace(R.id.settingContainer, SettingMenuFragment())
                    .commit()
            }
            .setNegativeButton("Cancel", null)
            .show()
    }
}