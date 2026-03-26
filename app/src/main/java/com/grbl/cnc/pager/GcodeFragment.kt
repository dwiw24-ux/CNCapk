package com.grbl.cnc.pager

import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import com.grbl.cnc.R
import com.grbl.cnc.gcode.PocketGcodeCreator

class GcodeFragment : Fragment(R.layout.frag_gcode) {

    private var gcodeToSave: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val edtWidth = view.findViewById<EditText>(R.id.edtWidth)
        val edtHeight = view.findViewById<EditText>(R.id.edtHeight)
        val edtDepth = view.findViewById<EditText>(R.id.edtDepth)
        val edtTool = view.findViewById<EditText>(R.id.edtTool)
        val edtStepDown = view.findViewById<EditText>(R.id.edtStepDown)
        val edtFeed = view.findViewById<EditText>(R.id.edtFeed)
        val edtPlunge = view.findViewById<EditText>(R.id.edtPlunge)
        val btnSave = view.findViewById<Button>(R.id.btnSavePocket)

        btnSave.setOnClickListener {

            val creator = PocketGcodeCreator()

            val lines = creator.rectPocket(
                x = 0.000,
                y = 0.000,
                width = edtWidth.text.toString().toDouble(),
                height = edtHeight.text.toString().toDouble(),
                depth = edtDepth.text.toString().toDouble(),
                tool = edtTool.text.toString().toDouble(),
                stepDown = edtStepDown.text.toString().toDouble(),
                rampLength = 20.0,
                feedrate = edtFeed.text.toString().toInt(),
                plungerate = edtPlunge.text.toString().toInt()
            )

            gcodeToSave = lines.joinToString("\n")

            showExportDialog()
        }
    }

    private val saveLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("*/*")) { uri ->
            uri?.let { saveToUri(it, gcodeToSave) }
        }

    private fun saveToUri(uri: Uri, text: String) {
        try {
            requireContext().contentResolver.openOutputStream(uri)?.use {
                it.write(text.toByteArray())
            }
            Toast.makeText(requireContext(), "G-code saved", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Save failed", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExportDialog() {
        val options = arrayOf("GCODE (.gcode)", "NC (.nc)", "TAP (.tap)")

        AlertDialog.Builder(requireContext())
            .setTitle("Export format")
            .setItems(options) { _, which ->
                val fileName = when (which) {
                    1 -> "pocket.nc"
                    2 -> "pocket.tap"
                    else -> "pocket.gcode"
                }
                saveLauncher.launch(fileName)
            }
            .show()
    }

}