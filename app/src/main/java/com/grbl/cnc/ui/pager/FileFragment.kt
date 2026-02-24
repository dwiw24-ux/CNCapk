package com.grbl.cnc.ui.pager

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
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
import com.grbl.cnc.ui.MainActivity
import androidx.fragment.app.activityViewModels
import com.grbl.cnc.grbl.GrblState
import com.grbl.cnc.ui.StreamKeepAliveService
import android.content.Context

class FileFragment : Fragment(R.layout.frag_file) {

    // ===== RUN STATE =====
    private var lines: List<String> = emptyList()
    private var current = 0

    // ===== UI =====
    private lateinit var progressBar: ProgressBar
    private lateinit var txtProgress: TextView
    private lateinit var txtEta: TextView
    private lateinit var edtStart: EditText
    private lateinit var txtFeedOv: TextView
    private lateinit var txtSpinOv: TextView
    private lateinit var rv: RecyclerView
    private lateinit var adapter: GcodeAdapter
    private var currentFileName = ""

    // ===== OVERRIDE =====
    private var feedOv = 100
    private var spinOv = 100

    // ===== RUN TIME TIMER =====
    private var startTime = 0L
    private var pauseStart = 0L
    private var pausedDuration = 0L
    private var timerRunning = false
    private var waitingOk = false

    enum class RunMode { IDLE, RUNNING, PAUSED }
    private var runMode = RunMode.IDLE

    data class QueueItem(val cmd: String, val isFileLine: Boolean)
    private val sendQueue = ArrayDeque<QueueItem>()
    private var lastSentItem: QueueItem? = null

    private lateinit var btnSpindle: Button
    private lateinit var btnRun: Button
    private lateinit var btnStop: Button
    private lateinit var btnPause: Button
    private lateinit var btnOpen: Button
    private lateinit var btnRunFromHere: Button
    private lateinit var btnFlood: Button
    private lateinit var btnMist: Button

    private val timerHandler = Handler(Looper.getMainLooper())
    private val viewModel: MainViewModel by activityViewModels()
    private var lastPlannerAvailable = 16
    private var currentState: GrblState = GrblState.UNKNOWN

    private var dwellInjected = false
    private var spindleDelaySeconds = 0

    private val timerRunnable = object : Runnable {
        @SuppressLint("SetTextI18n")
        override fun run() {
            if (!timerRunning) return
            val now = System.currentTimeMillis()
            val elapsed = now - startTime - pausedDuration
            txtEta.text = "Run Time: ${formatTime(elapsed)}"
            timerHandler.postDelayed(this, 1000)
        }
    }

    private val picker =
        registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            uri ?: return@registerForActivityResult
            loadFile(uri)
        }

    @SuppressLint("SetTextI18n")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        progressBar = view.findViewById(R.id.progressRun)
        txtProgress = view.findViewById(R.id.txtProgress)
        txtEta = view.findViewById(R.id.txtEta)
        txtFeedOv = view.findViewById(R.id.txtFeedOv)
        txtSpinOv = view.findViewById(R.id.txtSpinOv)
        edtStart = view.findViewById(R.id.edtStartLine)
        rv = view.findViewById(R.id.rvGcode)
        rv.layoutManager = LinearLayoutManager(requireContext())

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        btnSpindle = view.findViewById(R.id.btnSpindle)
        btnPause = view.findViewById(R.id.btnPause)
        btnStop = view.findViewById(R.id.btnStop)
        btnRun = view.findViewById(R.id.btnRun)
        btnOpen = view.findViewById(R.id.btnOpen)
        btnRunFromHere = view.findViewById(R.id.btnRunFromHere)
        btnFlood = view.findViewById(R.id.btnFlood)
        btnMist = view.findViewById(R.id.btnMist)

        viewModel.plannerAvailable.observe(viewLifecycleOwner) { planner ->
            lastPlannerAvailable = planner
            updateFromPlanner()
        }

        viewModel.grblRunMode.observe(viewLifecycleOwner) { state ->
            currentState = state
            updateButton(currentState)
        }

        viewModel.spindleRpm.observe(viewLifecycleOwner) { rpm ->
            val spindleOn = rpm > 0
            if (spindleOn) {
                btnSpindle.text = "SPINDLE ON"
                btnSpindle.setTextColor(Color.GREEN)
            } else {
                btnSpindle.text = "SPINDLE OFF"
                btnSpindle.setTextColor(Color.RED)
            }
        }

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
                btnMist.text = "FLOOD ON"
                btnMist.setTextColor(Color.GREEN)

            } else {
                btnMist.text = "FLOOD OFF"
                btnMist.setTextColor(Color.RED)
            }
        }

        edtStart.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            @SuppressLint("NotifyDataSetChanged")
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val line = s.toString().toIntOrNull() ?: return
                val index = (line -1).coerceIn(0, lines.size - 1)

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
            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        btnOpen.setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }

        btnRun.setOnClickListener {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Run File : $currentFileName")
                .setMessage(
                    "⚠ PASTIKAN:\n\n" +
                            "• Sudah Homing\n" +
                            "• Work Offset Benar\n\n" +
                            "• Lanjutkan ?")
                .setPositiveButton("Ok") { _, _ ->
                    Toast.makeText(requireContext(), "Start Streaming Gcode", Toast.LENGTH_SHORT).show()
                    startRun()
                }
                .setNegativeButton("Batal", null)
                .show()
        }

        btnPause.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            if (runMode == RunMode.RUNNING) {
                bt.send("!")
                runMode = RunMode.PAUSED
                pauseStart = System.currentTimeMillis()
            } else if (runMode == RunMode.PAUSED) {
                bt.send("~")

                pausedDuration += System.currentTimeMillis() - pauseStart
                runMode = RunMode.RUNNING

                if (!waitingOk) {
                    sendNext()
                }
            }
        }

        btnStop.setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x18.toByte())
            sendQueue.clear()
            runMode = RunMode.IDLE
            timerRunning = false
            
            (activity as? MainActivity)?.isStreaming = false

            current = lines.size
            timerHandler.removeCallbacks(timerRunnable)
            progressBar.progress = 0
            txtProgress.text = "0 %"

            feedOv = 100
            (activity as? MainActivity)?.btService?.sendRealtime(0x90.toByte())
            txtFeedOv.text = "100%"
            spinOv = 100
            (activity as? MainActivity)?.btService?.sendRealtime(0x99.toByte())
            txtSpinOv.text = "100%"
        }

        btnRunFromHere.setOnClickListener {
            val idx = edtStart.text.toString()
                .toIntOrNull()
                ?.minus(1)
                ?: return@setOnClickListener

            showRunFromHereWarning(idx)
        }

        view.findViewById<Button>(R.id.btnFeedMinus).setOnClickListener {
            if (feedOv > 10) {
                feedOv -= 10
                (activity as? MainActivity)?.btService?.sendRealtime(0x92.toByte()) // Feed -
                txtFeedOv.text = "$feedOv%"
            }
        }

        view.findViewById<Button>(R.id.btnFeedPlus).setOnClickListener {
            if (feedOv < 200) {
                feedOv += 10
                (activity as? MainActivity)?.btService?.sendRealtime(0x91.toByte()) // Feed +
                txtFeedOv.text = "$feedOv%"
            }
        }

        view.findViewById<Button>(R.id.btnFeedReset).setOnClickListener {
            feedOv = 100
            (activity as? MainActivity)?.btService?.sendRealtime(0x90.toByte()) // Feed reset
            txtFeedOv.text = "100%"
        }

        view.findViewById<Button>(R.id.btnSpinMinus).setOnClickListener {
            if (spinOv > 10) {
                spinOv -= 10
                (activity as? MainActivity)?.btService?.sendRealtime(0x9B.toByte()) // Spindle -
                txtSpinOv.text = "$spinOv%"
            }
        }

        view.findViewById<Button>(R.id.btnSpinPlus).setOnClickListener {
            if (spinOv < 200) {
                spinOv += 10
                (activity as? MainActivity)?.btService?.sendRealtime(0x9A.toByte()) // Spindle +
                txtSpinOv.text = "$spinOv%"
            }
        }

        view.findViewById<Button>(R.id.btnSpinReset).setOnClickListener {
            spinOv = 100
            (activity as? MainActivity)?.btService?.sendRealtime(0x99.toByte()) // Spindle reset
            txtSpinOv.text = "100%"
        }

        btnSpindle.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val currentRpm = viewModel.spindleRpm.value ?: 0
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0x9E.toByte())
            } else {
                if (currentRpm > 0) {
                    bt.send("M5\n")
                } else {
                    bt.send("M3 S12000\n")
                }
            }
        }

        btnFlood.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val floodStatus = viewModel.floodOn.value ?: false
            val mistStatus = viewModel.mistOn.value ?: false
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0xA0.toByte())
            } else {
                if (floodStatus) {
                    bt.send("M9\n")
                    if (mistStatus) {
                        bt.send("M7\n")
                    }
                }else {
                    bt.send("M8\n")
                }
            }
        }

        btnMist.setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            val mistStatus = viewModel.mistOn.value ?: false
            val floodStatus = viewModel.floodOn.value ?: false
            if (currentState == GrblState.HOLD) {
                bt.sendRealtime(0xA1.toByte())
            } else {
                if (mistStatus) {
                    bt.send("M9\n")
                    if (floodStatus) {
                        bt.send("M8\n")
                    }
                }else {
                    bt.send("M7\n")
                }
            }
        }

        (activity as? MainActivity)?.btService?.onOkReceived = {
            activity?.runOnUiThread {
                if (!waitingOk) return@runOnUiThread
                waitingOk = false

                if (lastSentItem?.isFileLine == true) {
                    current++
                }
                if (sendQueue.isEmpty() && current >= lines.size) {
                    stopRunFinished()
                    return@runOnUiThread
                }
                if (runMode == RunMode.RUNNING && !waitingOk) {
                    sendNext()
                }
            }
        }
    }

    @SuppressLint("SetTextI18n")
    private fun loadFile(uri: Uri) {
        currentFileName = getFileName(uri)

        val text = requireContext().contentResolver
            .openInputStream(uri)
            ?.bufferedReader()
            ?.readText() ?: return

        lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("(") &&
                    !it.startsWith(";") }

        adapter = GcodeAdapter(lines) { index ->
            if (runMode == RunMode.IDLE) {
                edtStart.setText((index + 1).toString())
                edtStart.setSelection(edtStart.text.length)
            }
        }
        rv.adapter = adapter

        current = 0
        waitingOk = false
        runMode = RunMode.IDLE

        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        view?.findViewById<TextView>(R.id.txtFileInfo)
            ?.text = "File: $currentFileName\nLines: ${lines.size}"
    }

    private fun startRun() {
        if (lines.isEmpty()) return

        sendQueue.clear()
        sendQueue.addAll(lines.map { QueueItem(it, true)})

        current = 0
        waitingOk = false
        runMode = RunMode.RUNNING
        dwellInjected = false

        (activity as? MainActivity)?.isStreaming = true

        val prefs = requireContext()
            .getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)
        spindleDelaySeconds = prefs.getInt("spindle_delay", 2)

        startTime = System.currentTimeMillis()
        pausedDuration = 0
        timerRunning = true
        timerHandler.post(timerRunnable)
        edtStart.setText("1")

        sendNext()
    }

    private fun sendNext() {
        if (runMode != RunMode.RUNNING) return
        if (waitingOk) return
        if (sendQueue.isEmpty()) return

        val item = sendQueue.removeFirst()
        lastSentItem = item

        val cmdUpper = item.cmd.trim().uppercase()

        (activity as? MainActivity)?.btService?.send(item.cmd + "\n")
        waitingOk = true

        // 🔴 Inject dwell SETELAH M3 / M4 (hanya sekali)
        if (!dwellInjected &&
            spindleDelaySeconds > 0 &&
            item.isFileLine &&
            (cmdUpper.contains("M3") || cmdUpper.contains("M4"))
        ) {

            val dwellCmd = "G4 P$spindleDelaySeconds"
            sendQueue.addFirst(QueueItem(dwellCmd, false))
            dwellInjected = true
        }
    }

    @SuppressLint("SetTextI18n")
    private fun runFromHere(index: Int) {
        if (index !in lines.indices) return
        val bt = (activity as? MainActivity)?.btService ?: return

        val st = scanStatePro(lines, index)
        if (st.z == null) {
            txtEta.text = "Run From Here dibatalkan: Z unknown"
            return
        }

        runMode = RunMode.IDLE
        waitingOk = false
        bt.sendRealtime(0x18.toByte())

        Handler(Looper.getMainLooper()).postDelayed({
            sendQueue.clear()
            dwellInjected = false
            sendQueue.addAll(buildRunFromHereHeader(st).map{ QueueItem(it,false)})
            sendQueue.addAll(lines.subList(index, lines.size).map{ QueueItem(it,true)})

            current = index
            runMode = RunMode.RUNNING

            (activity as? MainActivity)?.isStreaming = true

            startTime = System.currentTimeMillis()
            pausedDuration = 0
            timerRunning = true
            timerHandler.post(timerRunnable)

            edtStart.setText(current.toString())

            sendNext()
        }, 400)
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown.gcode"
        val cursor = requireContext().contentResolver.query(
            uri, null, null, null, null)
        cursor?.use {
            if (it.moveToFirst()) {
                val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                if (index >= 0) {
                    name = it.getString(index)
                }
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

    data class GcodeState(
        var x: Double? = null,
        var y: Double? = null,
        var z: Double? = null,

        var absolute: Boolean = true,
        var unitMm: Boolean = true,
        var wcs: String = "G54",

        var spindleOn: Boolean = false,
        var spindleDir: String? = null,
        var spindleSpeed: Int? = null,

        var feedZ: Double? = null,
        var feedXY: Double? = null
    )

    fun scanStatePro(lines: List<String>, target: Int): GcodeState {
        val st = GcodeState()

        for (i in 0 until target) {
            val l = lines[i].uppercase()
                .replace(Regex("\\(.*?\\)"), "")
                .trim()

            if (l.contains("G90")) st.absolute = true
            if (l.contains("G91")) st.absolute = false
            if (l.contains("G21")) st.unitMm = true
            if (l.contains("G20")) st.unitMm = false
            Regex("G5[4-9]").find(l)?.let { st.wcs = it.value }
            Regex("G1.*Z[-0-9.]+.*F([0-9.]+)").find(l)?.let {
                st.feedZ = it.groupValues[1].toDouble()
            }

            Regex("G1.*[XY].*F([0-9.]+)").find(l)?.let {
                st.feedXY = it.groupValues[1].toDouble()
            }

            if (l.contains("M3")) { st.spindleOn = true; st.spindleDir = "M3" }
            if (l.contains("M4")) { st.spindleOn = true; st.spindleDir = "M4" }
            if (l.contains("M5")) { st.spindleOn = false; st.spindleDir = null }
            Regex("S(\\d+)").find(l)?.let { st.spindleSpeed = it.groupValues[1].toInt() }

            fun axis(a: Char, cur: Double?): Double? {
                val r = Regex("$a([-0-9.]+)").find(l) ?: return cur
                val v = r.groupValues[1].toDouble()
                return if (st.absolute || cur == null) v else cur + v
            }

            st.x = axis('X', st.x)
            st.y = axis('Y', st.y)
            st.z = axis('Z', st.z)
        }
        return st
    }

    fun buildRunFromHereHeader(st: GcodeState): List<String> {
        val h = mutableListOf<String>()
        h += if (st.unitMm) "G21" else "G20"
        h += "G90"
        h += st.wcs
        h += "G53 G0 Z0"

        if (st.x != null && st.y != null)
            h += "G90 G0 X${st.x} Y${st.y}"

        if (st.spindleOn && st.spindleDir != null) {
            h += "${st.spindleDir} S${st.spindleSpeed ?: 12000}"
        }

        if (st.z != null)
            h += "G90 G1 Z${st.z} F${st.feedZ ?: 300.0} "

        st.feedXY?.let {
            h += "F$it"
        }
        return h
    }

    private fun showRunFromHereWarning(index: Int) {
        val lineNo = index + 1
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
                Toast.makeText(requireContext(), "Start Streaming Gcode", Toast.LENGTH_SHORT).show()
                runFromHere(index)
            }
            .setNegativeButton("BATAL", null)
            .show()
    }

    @SuppressLint("SetTextI18n")
    private fun stopRunFinished() {
        runMode = RunMode.IDLE
        timerRunning = false
        waitingOk = false
        timerHandler.removeCallbacks(timerRunnable)

        (activity as? MainActivity)?.isStreaming = false

        progressBar.progress = 0
        txtProgress.text = "0 %"

        feedOv = 100
        (activity as? MainActivity)?.btService?.sendRealtime(0x90.toByte())
        txtFeedOv.text = "100%"
        spinOv = 100
        (activity as? MainActivity)?.btService?.sendRealtime(0x99.toByte())
        txtSpinOv.text = "100%"
    }

    @SuppressLint("NotifyDataSetChanged", "SetTextI18n")
    fun updateFromPlanner() {
        val totalPlanner = 16

        if (currentState != GrblState.RUN) return

        val usedPlanner = totalPlanner - lastPlannerAvailable
        val activeLine = ( current - usedPlanner)
            .coerceIn(0, lines.size - 1)
        if (adapter.activeLine == activeLine) return

        adapter.activeLine = activeLine
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(activeLine)
        edtStart.setText((activeLine + 1).toString())

        val percent = (((activeLine + 1).toFloat() / lines.size) * 100).toInt()
        progressBar.progress = percent
        txtProgress.text = "$percent %"
        updateServiceProgress(percent)
    }

    private fun updateServiceProgress(progress: Int) {
        val intent = Intent(requireContext(), StreamKeepAliveService::class.java).apply {
            action = StreamKeepAliveService.ACTION_UPDATE_PROGRESS
            putExtra(StreamKeepAliveService.EXTRA_PROGRESS, progress)
        }
        requireContext().startService(intent)
    }

    private fun updateButton(currentState: GrblState) {
        when (currentState) {
            GrblState.IDLE -> {
                btnRun.isEnabled = true
                btnSpindle.isEnabled = true
                btnRunFromHere.isEnabled = true
                btnOpen.isEnabled = true
                btnPause.isEnabled = false
                btnStop.isEnabled = false
                btnRun.setTextColor(Color.GREEN)
                btnRunFromHere.setTextColor(Color.GREEN)
                btnPause.setTextColor(Color.GRAY)
                btnStop.setTextColor(Color.GRAY)
                btnOpen.setTextColor(Color.GREEN)
            }
            GrblState.RUN -> {
                btnRun.isEnabled = false
                btnSpindle.isEnabled = false
                btnRunFromHere.isEnabled = false
                btnOpen.isEnabled = false
                btnPause.isEnabled = true
                btnStop.isEnabled = true
                btnRun.setTextColor(Color.GRAY)
                btnRunFromHere.setTextColor(Color.GRAY)
                btnPause.setTextColor(Color.GREEN)
                btnStop.setTextColor(Color.GREEN)
                btnOpen.setTextColor(Color.GRAY)
            }
            GrblState.ALARM -> {
                btnRun.isEnabled = false
                btnSpindle.isEnabled = false
                btnRunFromHere.isEnabled = false
                btnOpen.isEnabled = false
                btnPause.isEnabled = false
                btnStop.isEnabled = false
                btnRun.setTextColor(Color.GRAY)
                btnRunFromHere.setTextColor(Color.GRAY)
                btnPause.setTextColor(Color.GRAY)
                btnStop.setTextColor(Color.GRAY)
                btnOpen.setTextColor(Color.GRAY)
            }
            GrblState.HOLD -> {
                btnRun.isEnabled = false
                btnSpindle.isEnabled = true
                btnRunFromHere.isEnabled = false
                btnOpen.isEnabled = false
                btnPause.isEnabled = true
                btnStop.isEnabled = true
                btnRun.setTextColor(Color.GRAY)
                btnRunFromHere.setTextColor(Color.GRAY)
                btnPause.setTextColor(Color.YELLOW)
                btnStop.setTextColor(Color.YELLOW)
                btnOpen.setTextColor(Color.GRAY)
            }
            else -> {
                btnRun.setTextColor(Color.GRAY)
                btnRunFromHere.setTextColor(Color.GRAY)
                btnPause.setTextColor(Color.GRAY)
                btnStop.setTextColor(Color.GRAY)
                btnOpen.setTextColor(Color.GRAY)

            }
        }
    }
}
