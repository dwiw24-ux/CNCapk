package com.grbl.cnc.ui.pager

import android.annotation.SuppressLint
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.Button
import android.widget.EditText
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.adapter.GcodeAdapter
import com.grbl.cnc.ui.MainActivity

class FileFragment : Fragment(R.layout.frag_file) {

    // ===== RUN STATE =====
    private var lines: List<String> = emptyList()
    private var current = 0
    private var isRunning = false
    private var paused = false

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

    private val sendQueue = ArrayDeque<String>()

    private val timerHandler = Handler(Looper.getMainLooper())

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
        edtStart = view.findViewById(R.id.edtStartLine)

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        edtStart.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            @SuppressLint("NotifyDataSetChanged")
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val line = s.toString().toIntOrNull() ?: return
                val index = line - 1

                if (::adapter.isInitialized && index in lines.indices) {
                    adapter.activeLine = index
                    adapter.notifyDataSetChanged()
                    rv.scrollToPosition(index)
                }
            }

            override fun afterTextChanged(s: android.text.Editable?) {}
        })

        rv = view.findViewById(R.id.rvGcode)
        rv.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener {
            if (runMode != RunMode.IDLE) return@setOnClickListener
            startRun() // run normal dari baris 0
        }
        view.findViewById<Button>(R.id.btnPause).setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            if (runMode == RunMode.RUNNING) {
                bt.send("!")
                paused = true
                runMode = RunMode.PAUSED
                pauseStart = System.currentTimeMillis()
            } else if (runMode == RunMode.PAUSED) {
                bt.send("~")
                bt.send("G90\n")
                pausedDuration += System.currentTimeMillis() - pauseStart
                paused = false
                runMode = RunMode.RUNNING
            }
        }

        view.findViewById<Button>(R.id.btnStop).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x18.toByte())
            sendQueue.clear()
            runMode = RunMode.IDLE
            paused = true
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
        view.findViewById<Button>(R.id.btnRunFromHere).setOnClickListener {
            if (runMode != RunMode.IDLE) return@setOnClickListener

            val idx = edtStart.text.toString()
                .toIntOrNull()
                ?.minus(1)
                ?: return@setOnClickListener

            showRunFromHereWarning(idx)
        }

        // ===== FEED OVERRIDE =====
        txtFeedOv = view.findViewById(R.id.txtFeedOv)
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

        txtSpinOv = view.findViewById(R.id.txtSpinOv)

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

        (activity as? MainActivity)?.btService?.onOkReceived = {
            activity?.runOnUiThread {
                if (!waitingOk) return@runOnUiThread

                waitingOk = false
                //current++

                // ==== JIKA FILE SUDAH SELESAI ====
                if (sendQueue.isEmpty() && current >= lines.size) {
                    stopRunFinished()
                    return@runOnUiThread
                }

                if (runMode == RunMode.RUNNING) {
                    sendNext()
                }

                val percent = ((current.toFloat() / lines.size) * 100).toInt()
                progressBar.progress = percent
                txtProgress.text = "$percent %"

                adapter.activeLine = current
                adapter.notifyItemChanged(current)
                rv.scrollToPosition(current)

                edtStart.setText(current.toString())
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
        paused = true
        isRunning = false
        waitingOk = false

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
        sendQueue.addAll(lines)

        current = 0
        paused = false
        waitingOk = false
        runMode = RunMode.RUNNING

        (activity as? MainActivity)?.isStreaming = true

        startTime = System.currentTimeMillis()
        pausedDuration = 0
        timerRunning = true
        timerHandler.post(timerRunnable)

        edtStart.setText("1")

        sendNext()
    }

    private fun sendNext() {
        if (runMode != RunMode.RUNNING) return
        if (sendQueue.isEmpty()) return

        waitingOk = true
        val cmd = sendQueue.removeFirst()
        (activity as? MainActivity)?.btService?.send(cmd + "\n")

        current++
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

        paused = true
        runMode = RunMode.IDLE
        waitingOk = false
        bt.sendRealtime(0x18.toByte())

        Handler(Looper.getMainLooper()).postDelayed({
            sendQueue.clear()
            sendQueue.addAll(buildRunFromHereHeader(st))
            sendQueue.addAll(lines.subList(index, lines.size))

            current = index
            paused = false
            runMode = RunMode.RUNNING

            (activity as? MainActivity)?.isStreaming = true

            startTime = System.currentTimeMillis()
            pausedDuration = 0
            timerRunning = true
            timerHandler.post(timerRunnable)

            //adapter.activeLine = current
            //adapter.notifyItemChanged(current)
            //rv.scrollToPosition(current)

            edtStart.setText(current.toString())

            sendNext()
        }, 400)
    }

    private fun getFileName(uri: Uri): String {
        var name = "unknown.gcode"
        val cursor = requireContext().contentResolver.query(
            uri, null, null, null, null
        )
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

        //txtEta.text = "Run Time: ✔ Done"
        progressBar.progress = 0
        txtProgress.text = "0 %"

        feedOv = 100
        (activity as? MainActivity)?.btService?.sendRealtime(0x90.toByte())
        txtFeedOv.text = "100%"
        spinOv = 100
        (activity as? MainActivity)?.btService?.sendRealtime(0x99.toByte())
        txtSpinOv.text = "100%"
    }

}
