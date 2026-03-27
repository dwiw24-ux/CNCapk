package com.grbl.cnc.pager

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.grbl.cnc.R
import com.grbl.cnc.adapter.GcodeAdapter
import com.grbl.cnc.MainActivity
import androidx.fragment.app.activityViewModels
import com.grbl.cnc.grbl.GrblState
import com.grbl.cnc.StreamKeepAliveService
import android.provider.OpenableColumns
import android.text.Editable
import android.text.TextWatcher
import android.util.Log

class FileFragment : Fragment(R.layout.frag_file) {

    // ===== FILE STATE =====
    private var lines: List<String> = emptyList()
    private var currentFileName = ""
    private var wcsBeforeRun = "G54"

    // ===== UI =====
    private lateinit var progressBar: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtEta: TextView
    private lateinit var edtStart: EditText
    private lateinit var txtRapidOv: TextView
    private lateinit var txtFeedOv: TextView
    private lateinit var txtSpinOv: TextView
    private lateinit var txtFileInfo: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: GcodeAdapter

    private lateinit var btnSpindle: Button
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var btnPause: Button
    private lateinit var btnOpen: Button
    private lateinit var btnRunFromHere: Button
    private lateinit var btnFlood: Button
    private lateinit var btnMist: Button

    // ===== VIEW MODEL =====
    private val viewModel: MainViewModel by activityViewModels()
    private var lastPlannerAvailable = 16
    private var currentState: GrblState = GrblState.UNKNOWN

    // ===== STREAMER =====
    private lateinit var streamer: GcodeStreamer

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            loadFile(uri)
        }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ── bind views ──
        progressBar    = view.findViewById(R.id.progressRun)
        txtProgress    = view.findViewById(R.id.txtProgress)
        txtEta         = view.findViewById(R.id.txtEta)
        txtRapidOv     = view.findViewById(R.id.txtRapidOv)
        txtFeedOv      = view.findViewById(R.id.txtFeedOv)
        txtSpinOv      = view.findViewById(R.id.txtSpinOv)
        txtFileInfo    = view.findViewById(R.id.txtFileInfo)
        edtStart       = view.findViewById(R.id.edtStartLine)
        rv             = view.findViewById(R.id.rvGcode)
        btnSpindle     = view.findViewById(R.id.btnSpindle)
        btnPause       = view.findViewById(R.id.btnPause)
        btnStop        = view.findViewById(R.id.btnStop)
        btnRun         = view.findViewById(R.id.btnRun)
        btnOpen        = view.findViewById(R.id.btnOpen)
        btnRunFromHere = view.findViewById(R.id.btnRunFromHere)
        btnFlood       = view.findViewById(R.id.btnFlood)
        btnMist        = view.findViewById(R.id.btnMist)

        rv.layoutManager = LinearLayoutManager(requireContext())

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        // ── init streamer ──
        streamer = GcodeStreamer(requireContext(), object : GcodeStreamerCallback {

            override fun onSendCommand(cmd: String) {
                (activity as? MainActivity)?.btService?.send(cmd)
            }

            override fun onSendRealtime(byte: Byte) {
                (activity as? MainActivity)?.btService?.sendRealtime(byte)
            }

            @SuppressLint("SetTextI18n")
            override fun onProgress(activeLine: Int, percent: Int, elapsedMs: Long) {
                // elapsedMs == -1L → hanya timer tick, jangan update progress baris
                requireActivity().runOnUiThread {
                    if (elapsedMs >= 0) {
                        txtEta.text = "Run Time: ${formatTime(elapsedMs)}"
                    }
                }
            }

            override fun onFinished() {
                requireActivity().runOnUiThread {
                    stopRunFinishedUI()
                }
            }

            override fun onAbortedByAlarm() {
                requireActivity().runOnUiThread {
                    abortStreamingByAlarmUI()
                }
            }

            // [FIX] Implementasi callback eksplisit — menggantikan sentinel -1
            // yang lama di onProgress(). Dipanggil saat runFromHere gagal
            // karena posisi Z tidak diketahui sebelum baris target.
            @SuppressLint("SetTextI18n")
            override fun onRunFromHereZUnknown() {
                requireActivity().runOnUiThread {
                    (activity as? MainActivity)?.isStreaming = false
                    txtEta.text = "Canceled: Z unknown before line"
                    Toast.makeText(
                        requireContext(),
                        "Run From Here dibatalkan: posisi Z tidak diketahui sebelum baris ini.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }

            override fun onUpdateServiceProgress(progress: Int, fileName: String) {
                updateServiceProgress(progress)
            }
        })

        // ── observers ──
        viewModel.plannerAvailable.observe(viewLifecycleOwner) { planner ->
            lastPlannerAvailable = planner
            updateFromPlanner()
        }
        viewModel.grblRunMode.observe(viewLifecycleOwner) { state ->
            currentState = state
            updateButton(currentState)
            if (state == GrblState.ALARM) {
                streamer.onAlarmDetected()
            }
        }
        viewModel.spindleRpm.observe(viewLifecycleOwner) { rpm ->
            if (rpm > 0) {
                btnSpindle.text = "SPINDLE ON"
                btnSpindle.setTextColor(Color.GREEN)
            } else {
                btnSpindle.text = "SPINDLE OFF"
                btnSpindle.setTextColor(Color.RED)
            }
        }
        viewModel.feedOverride.observe(viewLifecycleOwner) {
            txtFeedOv.text = "Feed : $it%"
        }
        viewModel.rapidOverride.observe(viewLifecycleOwner) {
            txtRapidOv.text = "Rapid : $it%"
        }
        viewModel.spindleOverride.observe(viewLifecycleOwner) {
            txtSpinOv.text = "Spindle : $it%"
        }
        /**viewModel.spindleDirection.observe(viewLifecycleOwner) { dir ->
            when (dir) {
                SpindleDirection.CW -> {
                    //btnSpindle.text = "SPINDLE CW"
                    //btnSpindle.setTextColor(Color.GREEN)
                }
                SpindleDirection.CCW -> {
                    //btnSpindle.text = "SPINDLE CCW"
                    //btnSpindle.setTextColor(Color.CYAN)
                }
                SpindleDirection.OFF -> {
                    //btnSpindle.text = "SPINDLE OFF"
                    //btnSpindle.setTextColor(Color.RED)
                }
            }
        }*/

        viewModel.floodOn.observe(viewLifecycleOwner) { onFlood ->
            if (onFlood) {
                btnFlood.text = "FLOOD ON"
                btnFlood.setTextColor(Color.GREEN)
            } else {
                btnFlood.text = "FLOOD OFF"
                btnFlood.setTextColor(Color.RED)
            }
        }

        viewModel.mistOn.observe(viewLifecycleOwner) { onMist ->
            if (onMist) {
                btnMist.text = "MIST ON"
                btnMist.setTextColor(Color.GREEN)
            } else {
                btnMist.text = "MIST OFF"
                btnMist.setTextColor(Color.RED)
            }
        }

        // ── edtStart watcher ──
        edtStart.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            @SuppressLint("NotifyDataSetChanged")
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val line = s.toString().toIntOrNull() ?: return
                val index = (line - 1).coerceIn(0, lines.size - 1)

                if (currentState != GrblState.IDLE) return
                if (!::adapter.isInitialized) return
                if (lines.isEmpty()) return
                if (adapter.activeLine == index) return

                adapter.activeLine = index
                adapter.notifyDataSetChanged()
                rv.scrollToPosition(index)

                val percent = (((index + 1).toFloat() / lines.size) * 100).toInt()
                progressBar.progress = percent
                txtProgress.text = "$percent %"
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // ── button listeners ──
        btnOpen.setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }

        btnRun.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Run File : $currentFileName")
                .setMessage(
                    "⚠ PASTIKAN:\n\n" +
                            "• Homing OK\n" +
                            "• Work Offset OK\n\n" +
                            "• Next ?"
                )
                .setPositiveButton("RUN") { _, _ ->
                    val main = activity as? MainActivity ?: return@setPositiveButton
                    wcsBeforeRun = main.currentWcs
                    Toast.makeText(requireContext(), "Start Streaming $currentFileName", Toast.LENGTH_LONG).show()
                    (activity as? MainActivity)?.isStreaming = true
                    edtStart.setText("1")
                    streamer.startRun(lines, currentFileName)
                }
                .setNegativeButton("CANCEL", null)
                .show()
        }

        btnPause.setOnClickListener {
            when (streamer.runMode) {
                RunMode.RUNNING -> {
                    streamer.pause()
                    btnPause.text = "RESUME"
                    Toast.makeText(requireContext(), "Hold Streaming $currentFileName", Toast.LENGTH_SHORT).show()
                }
                RunMode.PAUSED -> {
                    streamer.resume()
                    btnPause.text = "HOLD"
                    Toast.makeText(requireContext(), "Resume Streaming $currentFileName", Toast.LENGTH_SHORT).show()
                }
                else -> {}
            }
        }

        btnStop.setOnClickListener {
            streamer.stop()
            (activity as? MainActivity)?.isStreaming = false
            Toast.makeText(requireContext(), "Stop Streaming $currentFileName", Toast.LENGTH_LONG).show()
        }

        btnRunFromHere.setOnClickListener {
            val idx = edtStart.text.toString()
                .toIntOrNull()
                ?.minus(1)
                ?: return@setOnClickListener
            showRunFromHereWarning(idx)
        }

        // ── override buttons ──
        view.findViewById<Button>(R.id.btnFeedMinus).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x92.toByte())
        }
        view.findViewById<Button>(R.id.btnFeedPlus).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x91.toByte())
        }
        view.findViewById<Button>(R.id.btnFeedReset).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x90.toByte())
        }
        view.findViewById<Button>(R.id.btnRapidLow).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x97.toByte())
        }
        view.findViewById<Button>(R.id.btnRapidMedium).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x96.toByte())
        }
        view.findViewById<Button>(R.id.btnRapidReset).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x95.toByte())
        }
        view.findViewById<Button>(R.id.btnSpinMinus).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x9B.toByte())
        }
        view.findViewById<Button>(R.id.btnSpinPlus).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x9A.toByte())
        }
        view.findViewById<Button>(R.id.btnSpinReset).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x99.toByte())
        }

        // ── spindle / flood / mist toggle ──
        btnSpindle.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val currentRpm = viewModel.spindleRpm.value ?: 0
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0x9E.toByte())
            } else {
                if (currentRpm > 0) bt.send("M5\n") else bt.send("M3 S12000\n")
            }
        }

        btnFlood.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val floodStatus = viewModel.floodOn.value ?: false
            val mistStatus  = viewModel.mistOn.value ?: false
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0xA0.toByte())
            } else {
                if (floodStatus) {
                    bt.send("M9\n")
                    if (mistStatus) bt.send("M7\n")
                } else {
                    bt.send("M8\n")
                }
            }
        }

        btnMist.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val mistStatus  = viewModel.mistOn.value ?: false
            val floodStatus = viewModel.floodOn.value ?: false
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0xA1.toByte())
            } else {
                if (mistStatus) {
                    bt.send("M9\n")
                    if (floodStatus) bt.send("M8\n")
                } else {
                    bt.send("M7\n")
                }
            }
        }
    }

    // ─────────────────────────────────────────
    //  Lifecycle — OK hook dipasang/dilepas di sini
    // ─────────────────────────────────────────

    // Pindahkan pendaftaran onOkReceived dari onViewCreated ke onStart/onStop
    // agar hook selalu menunjuk ke instance lines yang aktif dan tidak bocor
    // saat fragment di-replace lalu di-attach kembali.
    override fun onStart() {
        super.onStart()
        (activity as? MainActivity)?.btService?.let { bt ->
            bt.onOkReceived = { streamer.onOkReceived(lines) }
        }
    }

    override fun onStop() {
        super.onStop()
        if (streamer.runMode == RunMode.IDLE) {
            (activity as? MainActivity)?.btService?.let { bt ->
                bt.onOkReceived = null
            }
        }
    }

    // ─────────────────────────────────────────
    //  File loading
    // ─────────────────────────────────────────

    @SuppressLint("SetTextI18n")
    private fun loadFile(uri: Uri) {
        currentFileName = getFileName(uri)

        val text = requireContext().contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.readText() ?: return

        lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("(") && !it.startsWith(";") }

        adapter = GcodeAdapter(lines) { index ->
            if (streamer.runMode == RunMode.IDLE) {
                edtStart.setText((index + 1).toString())
                edtStart.setSelection(edtStart.text.length)
            }
        }
        rv.adapter = adapter

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"
        txtFileInfo.text = "File: $currentFileName\nLines: ${lines.size}"
    }

    // ─────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────

    private fun getFileName(uri: Uri): String {
        var name = "unknown.gcode"
        val cursor = requireContext().contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) name = it.getString(index)
            }
        }
        return name
    }

    private fun formatTime(ms: Long): String {
        val sec = ms / 1000
        val s = sec % 60
        val m = (sec / 60) % 60
        val h = sec / 3600
        return if (h > 0) "%02d:%02d:%02d".format(h, m, s)
        else "%02d:%02d".format(m, s)
    }

    @SuppressLint("SetTextI18n")
    private fun showRunFromHereWarning(index: Int) {
        val lineNo   = index + 1
        val lineText = lines.getOrNull(index)?.take(40) ?: ""

        AlertDialog.Builder(requireContext())
            .setTitle("⚠ Run From Here")
            .setMessage(
                "Run dari baris $lineNo\n\n" +
                        "⚠ Pastikan:\n\n" +
                        "• Z Axis aman\n" +
                        "• Work Offset benar\n\n" +
                        "G-code:\n$lineText\n\n" +
                        "Lanjutkan?"
            )
            .setPositiveButton("RUN") { _, _ ->
                val main = activity as? MainActivity ?: return@setPositiveButton
                wcsBeforeRun = main.currentWcs
                Toast.makeText(requireContext(), "Start Streaming $currentFileName line $lineNo", Toast.LENGTH_LONG).show()
                (activity as? MainActivity)?.isStreaming = true
                edtStart.setText((index + 1).toString())
                streamer.runFromHere(lines, index, currentFileName)
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun stopRunFinishedUI() {
        val bt = (activity as? MainActivity)?.btService ?: return
        (activity as? MainActivity)?.isStreaming = false
        bt.send("$wcsBeforeRun\n")
        Toast.makeText(requireContext(), "Streaming $currentFileName Finished", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("SetTextI18n")
    private fun abortStreamingByAlarmUI() {
        (activity as? MainActivity)?.isStreaming = false
        Toast.makeText(requireContext(), "ALARM detected. Job cancelled.", Toast.LENGTH_LONG).show()
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    fun updateFromPlanner() {
        //if (streamer.runMode != RunMode.RUNNING) return
        if (currentState != GrblState.RUN) return
        if (lines.size <= 1) return
        if (!::adapter.isInitialized) return

        val activeLine = streamer.calculateActiveLine(lastPlannerAvailable, lines.size)
        if (activeLine < 0) return   // tidak berubah

        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", android.content.Context.MODE_PRIVATE)

        val autoScroll   = prefs.getBoolean("auto_scroll", true)
        val showProgress = prefs.getBoolean("show_progress", true)

        if (autoScroll) {
            adapter.activeLine = activeLine
            adapter.notifyDataSetChanged()
            rv.scrollToPosition(activeLine)
        }
        edtStart.setText((activeLine + 1).toString())

        val percent = (((activeLine + 1).toFloat() / lines.size) * 100).toInt()
        if (showProgress) progressBar.progress = percent
        txtProgress.text = "$percent %"
        updateServiceProgress(percent)
        Log.d("RUN", "Line = $activeLine")
    }

    /**Agar progressBar langsung hide saat OFF:
    override fun onResume() {
        super.onResume()

        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

        val showProgress = prefs.getBoolean("show_progress", true)

        progressBar.visibility =
            if (showProgress) View.VISIBLE else View.GONE

        txtProgress.visibility =
            if (showProgress) View.VISIBLE else View.GONE
    }**/

    private fun updateServiceProgress(progress: Int) {
        val intent = Intent(requireContext(), StreamKeepAliveService::class.java).apply {
            action = StreamKeepAliveService.ACTION_UPDATE_PROGRESS
            putExtra(StreamKeepAliveService.EXTRA_PROGRESS, progress)
            putExtra(StreamKeepAliveService.EXTRA_FILENAME, currentFileName)
        }
        requireContext().startService(intent)
    }

    private fun updateButton(currentState: GrblState) {
        when (currentState) {
            GrblState.IDLE -> {
                btnRun.isEnabled = true
                btnRun.alpha = 1f
                btnSpindle.isEnabled = true
                btnSpindle.alpha = 1f
                btnRunFromHere.isEnabled = true
                btnRunFromHere.alpha = 1f
                btnOpen.isEnabled = true
                btnOpen.alpha = 1f
                btnPause.isEnabled = false
                btnPause.alpha = 0.4f
                btnStop.isEnabled = true
                btnStop.alpha = 1f
            }
            GrblState.RUN -> {
                btnRun.isEnabled = false
                btnRun.alpha = 0.4f
                btnSpindle.isEnabled = false
                btnSpindle.alpha = 0.4f
                btnRunFromHere.isEnabled = false
                btnRunFromHere.alpha = 0.4f
                btnOpen.isEnabled = false
                btnOpen.alpha = 0.4f
                btnPause.isEnabled = true
                btnPause.alpha = 1f
                btnStop.isEnabled = true
                btnStop.alpha = 1f
            }
            GrblState.ALARM -> {
                btnRun.isEnabled = false
                btnRun.alpha = 0.4f
                btnSpindle.isEnabled = false
                btnSpindle.alpha = 0.4f
                btnRunFromHere.isEnabled = false
                btnRunFromHere.alpha = 0.4f
                btnOpen.isEnabled = false
                btnOpen.alpha = 0.4f
                btnPause.isEnabled = false
                btnPause.alpha = 0.4f
                btnStop.isEnabled = false
                btnStop.alpha = 0.4f
            }
            GrblState.HOLD -> {
                btnRun.isEnabled = false
                btnRun.alpha = 0.4f
                btnSpindle.isEnabled = true
                btnSpindle.alpha = 1f
                btnRunFromHere.isEnabled = false
                btnRunFromHere.alpha = 0.4f
                btnOpen.isEnabled = false
                btnOpen.alpha = 0.4f
                btnPause.isEnabled = true
                btnPause.alpha = 1f
                btnStop.isEnabled = true
                btnStop.alpha = 1f
            }
            else -> {
                btnRun.isEnabled = false
                btnRun.alpha = 0.4f
                btnSpindle.isEnabled = false
                btnSpindle.alpha = 0.4f
                btnRunFromHere.isEnabled = false
                btnRunFromHere.alpha = 0.4f
                btnOpen.isEnabled = false
                btnOpen.alpha = 0.4f
                btnPause.isEnabled = false
                btnPause.alpha = 0.4f
                btnStop.isEnabled = false
                btnStop.alpha = 0.4f
            }
        }
    }
}
