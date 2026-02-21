package com.grbl.cnc.ui.settings

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.DividerItemDecoration
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.SettingAdapter

class SettingJogFragment : Fragment(R.layout.fragment_settings) {

    private lateinit var prefs: SharedPreferences

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val list = listOf(
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging min step size for XY",
                "Current :",
                "jogging_min_XY"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging max step size for XY",
                "Current :",
                "jogging_max_XY"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging min step size for Z",
                "Current :",
                "jogging_max_Z"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging max step size for Z",
                "Current :",
                "jogging_max_Z"
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
            "jogging_min_XY" -> prefs.getInt(item.key, 100).toString()
            else -> prefs.getFloat(item.key, 0f).toString()
        }

        editText.setText(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->

                val value = editText.text.toString()

                prefs.edit().apply {

                    if (item.key == "jogging_min_XY") {
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