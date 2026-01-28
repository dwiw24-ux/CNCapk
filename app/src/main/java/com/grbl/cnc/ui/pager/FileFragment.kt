package com.grbl.cnc.ui.pager

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

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
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

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        progressBar = view.findViewById(R.id.progressRun)
        txtProgress = view.findViewById(R.id.txtProgress)
        txtEta = view.findViewById(R.id.txtEta)
        edtStart = view.findViewById(R.id.edtStartLine)

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        rv = view.findViewById(R.id.rvGcode)
        rv.layoutManager = LinearLayoutManager(requireContext())

        view.findViewById<Button>(R.id.btnOpen).setOnClickListener {
            picker.launch(arrayOf("*/*"))
        }
        view.findViewById<Button>(R.id.btnRun).setOnClickListener {
            startRun()
        }
        view.findViewById<Button>(R.id.btnPause).setOnClickListener {
            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener
            paused = !paused
            if (paused) {
                bt.send("!")
                pauseStart = System.currentTimeMillis()
                txtEta.text = "Run Time: ⏸ Paused"
            } else {
                bt.send("~")
                pausedDuration += System.currentTimeMillis() - pauseStart
            }
        }

        view.findViewById<Button>(R.id.btnStop).setOnClickListener {
            stopRun()
            progressBar.progress = 0
        }
        view.findViewById<Button>(R.id.btnRunFromHere).setOnClickListener {
            runFromHere()
        }

        // ===== FEED OVERRIDE =====
        val txtFeedOv = view.findViewById<TextView>(R.id.txtFeedOv)
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

        val txtSpinOv = view.findViewById<TextView>(R.id.txtSpinOv)

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

        // ===== OK CALLBACK =====
        (activity as? MainActivity)?.btService?.onOkReceived = {
            activity?.runOnUiThread {
                if (!isRunning || paused) return@runOnUiThread
                sendNext()
            }
        }
    }

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
            runFromHere(index)
        }
        rv.adapter = adapter

        current = 0
        paused = true
        isRunning = false

        progressBar.progress = 0
        txtProgress.text = "0 %"
        txtEta.text = "Run Time: 00:00"

        view?.findViewById<TextView>(R.id.txtFileInfo)
            ?.text = "File: $currentFileName\nLines: ${lines.size}"
    }

    private fun startRun() {
        if (lines.isEmpty()) return

        isRunning = true
        paused = false
        current = 0

        // ===== TIMER START =====
        startTime = System.currentTimeMillis()
        pausedDuration = 0L
        timerRunning = true
        timerHandler.post(timerRunnable)

        sendNext()
    }

    private fun stopRun() {
        paused = true
        isRunning = false
        current = lines.size

        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)

        (activity as? MainActivity)?.btService?.sendRealtime(0x18.toByte())

        txtEta.text = "Run Time:■ Stopped"
    }

    private fun sendNext() {
        if (paused || current >= lines.size) {
            if (current >= lines.size) {
                progressBar.progress = 100
                txtProgress.text = "100 %"

                timerRunning = false
                timerHandler.removeCallbacks(timerRunnable)
                txtEta.text = "Run Time: ✔ Done"
            }
            return
        }

        val line = lines[current]

        edtStart.setText((current + 1).toString())

        adapter.activeLine = current
        adapter.notifyItemChanged(current)
        rv.scrollToPosition(current)

        val percent = ((current.toFloat() / lines.size) * 100).toInt()
        progressBar.progress = percent
        txtProgress.text = "$percent %"

        // kirim ke GRBL
        (activity as? MainActivity)?.btService?.send(line)
        current++
    }

    private fun runFromHere(index: Int) {
        if (lines.isEmpty()) return
        if (index !in lines.indices) return


        // ⚠️ SAFETY RESET
        val bt = (activity as? MainActivity)?.btService ?: return
        bt.sendRealtime(0x18.toByte()) // soft reset
        Thread.sleep(300)

        current = index
        paused = false
        isRunning = true

        // ===== RESET TIMER =====
        startTime = System.currentTimeMillis()
        pausedDuration = 0L
        timerRunning = true
        timerHandler.post(timerRunnable)

        adapter.activeLine = current
        adapter.notifyDataSetChanged()
        rv.scrollToPosition(current)

        sendNext()
    }

    private fun runFromHere() {
        val idx = edtStart.text.toString().toIntOrNull()?.minus(1) ?: return
        runFromHere(idx)
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

}
