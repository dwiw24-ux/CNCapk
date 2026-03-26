package com.grbl.cnc.settings

import android.annotation.SuppressLint
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
                "Current : ${prefs.getFloat("jogging_min_XY", 0f)} mm",
                "jogging_min_XY"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging max step size for XY",
                "Current : ${prefs.getFloat("jogging_max_XY", 0f)} mm",
                "jogging_max_XY"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging min step size for Z",
                "Current : ${prefs.getFloat("jogging_min_Z", 0f)} mm",
                "jogging_min_Z"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jogging max step size for Z",
                "Current : ${prefs.getFloat("jogging_max_Z", 0f)} mm",
                "jogging_max_Z"
            ),
            SettingItem(
                R.drawable.ic_settings_applications_black_24dp,
                "Jog Hold Distance",
                "Current : ${prefs.getFloat("hold_distance", 1f)} mm ",
                "hold_distance"
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

    @SuppressLint("UseKtx")
    private fun showNumberDialog(item: SettingItem) {

        val editText = EditText(requireContext())
        editText.inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL

        val currentValue =  prefs.getFloat(item.key, 0f).toString()

        editText.setText(currentValue)

        AlertDialog.Builder(requireContext())
            .setTitle(item.title)
            .setView(editText)
            .setPositiveButton("Save") { _, _ ->
                val value = editText.text.toString().toFloatOrNull() ?: 0f

                prefs.edit()
                    .putFloat(item.key, value)
                    .apply()
                }
            .setNegativeButton("Cancel", null)
            .show()
    }
}