package com.grbl.cnc.ui

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.AdapterGcodeEditor

class GcodeEditorFragment : Fragment(R.layout.gcode_editor) {
    private lateinit var recycler: RecyclerView
    private lateinit var adapter: AdapterGcodeEditor
    private lateinit var etLineNumber: EditText
    private lateinit var tvSelectionInfo: TextView
    private var currentFileName: String = "No File"
    private var fileUri: Uri? = null
    private var isDirty = false

    // ===============================
    // OPEN FILE
    // ===============================
    private val openFileLauncher =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri?.let {
                fileUri = it
                requireContext().contentResolver.takePersistableUriPermission(
                    it,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
                loadFile(it)
            }
        }

    // ===============================
    // SAVE AS
    // ===============================
    private val createFileLauncher =
        registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
            uri?.let { saveToUri(it) }
        }

    // ===============================
    // VIEW CREATED
    // ===============================
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        recycler = view.findViewById(R.id.recyclerLines)
        recycler.layoutManager = LinearLayoutManager(requireContext())

        etLineNumber = view.findViewById(R.id.etLineNumber)
        tvSelectionInfo = view.findViewById(R.id.tvSelectionInfo)

        setupAdapter(mutableListOf())

        // OPEN
        view.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            openFileLauncher.launch(arrayOf("*/*"))
        }
        // ADD
        view.findViewById<Button>(R.id.btnAdd).setOnClickListener {
            val insertPos = getInsertPosition()
            adapter.addLine(insertPos, "G0 X0 Y0")
            adapter.clearSelection()
            isDirty = true
        }
        // CUT MULTI
        view.findViewById<Button>(R.id.btnCut).setOnClickListener {
            if (adapter.selectedPositions.isNotEmpty()) {
                adapter.removeSelected()
                isDirty = true
            }
        }
        // SAVE
        view.findViewById<Button>(R.id.btnSave).setOnClickListener {
            showSaveDialog()
        }

        view.findViewById<Button>(R.id.btnGo).setOnClickListener {

            val input = etLineNumber.text.toString()
            if (input.isEmpty()) return@setOnClickListener

            val lineNumber = input.toIntOrNull() ?: return@setOnClickListener

            goToLine(lineNumber - 1)
        }
    }

    @SuppressLint("SetTextI18n")
    private fun setupAdapter(lines: MutableList<String>) {

        adapter = AdapterGcodeEditor(
            lines,
            onLineSelected = { position ->
                position?.let {
                    etLineNumber.setText((it + 1).toString())
                    recycler.scrollToPosition(it)
                }
            },
            onSelectionChanged = { count ->
                updateSelectionInfo(count)
            },
            onEditRequested = { position ->
                showEditDialog(position)
            }
        )
        recycler.adapter = adapter
        updateSelectionInfo(0)
    }

    // ===============================
    // LOAD FILE
    // ===============================
    private fun loadFile(uri: Uri) {
        currentFileName = getFileName(uri) ?: "Unnamed"
        val lines = requireContext()
            .contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.readLines()
            ?.toMutableList()
            ?: mutableListOf()

        setupAdapter(lines)
        isDirty = false
    }

    // ===============================
    // SAVE DIALOG
    // ===============================
    private fun showSaveDialog() {
        val options = arrayOf("Overwrite File", "Save As New File")

        AlertDialog.Builder(requireContext())
            .setTitle("Save Options")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> confirmOverwrite()
                    1 -> {
                        val defaultName = getFileName(fileUri) ?: "edited_file.gcode"
                        createFileLauncher.launch(defaultName)
                    }
                }
            }
            .show()
    }

    private fun confirmOverwrite() {
        if (fileUri == null) {
            Toast.makeText(requireContext(), "No file loaded", Toast.LENGTH_SHORT).show()
            return
        }
        AlertDialog.Builder(requireContext())
            .setTitle("Confirm Overwrite")
            .setMessage("Replace existing file?")
            .setPositiveButton("Yes") { _, _ ->
                saveToUri(fileUri!!)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===============================
    // SAVE CORE
    // ===============================
    private fun saveToUri(uri: Uri) {
        requireContext().contentResolver
            .openOutputStream(uri, "wt")
            ?.bufferedWriter()
            ?.use { writer ->
                adapter.getAllLines().forEach {
                    writer.write(it)
                    writer.newLine()
                }
            }
        currentFileName = getFileName(uri) ?: currentFileName
        updateSelectionInfo(adapter.selectedPositions.size)

        Toast.makeText(requireContext(), "File saved", Toast.LENGTH_SHORT).show()
        isDirty = false
    }

    // ===============================
    // EDIT LINE
    // ===============================

    private fun showEditDialog(position: Int) {
        val edit = EditText(requireContext())
        edit.setText(adapter.getAllLines()[position])
        edit.setSelection(edit.text.length)

        AlertDialog.Builder(requireContext())
            .setTitle("Edit Line ${position + 1}")
            .setView(edit)
            .setPositiveButton("OK") { _, _ ->
                adapter.updateLine(position, edit.text.toString())
                isDirty = true
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    // ===============================
    // HELPER
    // ===============================
    private fun getInsertPosition(): Int {
        return if (adapter.selectedPositions.isNotEmpty()) {
            adapter.selectedPositions.maxOrNull()!! + 1
        } else {
            adapter.itemCount
        }
    }

    private fun getFileName(uri: Uri?): String? {
        uri ?: return null
        val cursor = requireContext().contentResolver
            .query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex("_display_name")
            if (it.moveToFirst() && nameIndex >= 0) {
                return it.getString(nameIndex)
            }
        }
        return null
    }

    private fun goToLine(position: Int) {
        if (position < 0 || position >= adapter.itemCount) {
            Toast.makeText(requireContext(), "Invalid line number", Toast.LENGTH_SHORT).show()
            return
        }
        adapter.goToLineSmart(position)
    }

    private fun updateSelectionInfo(selectedCount: Int) {
        val total = adapter.itemCount
        tvSelectionInfo.text =
            if (selectedCount > 0) {
                "File: $currentFileName | Selected: $selectedCount Line / $total Lines"
            } else {
                "File: $currentFileName | Total: $total Lines"
            }
    }
}