package com.grbl.cnc.pager

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.util.Locale

// ─────────────────────────────────────────────
//  Data classes / enums
// ─────────────────────────────────────────────

enum class RunMode { IDLE, RUNNING, PAUSED }

data class QueueItem(val cmd: String, val isFileLine: Boolean)

data class GcodeState(
    var x: Double? = null,
    var y: Double? = null,
    var z: Double? = null,

    var absolute: Boolean = true,
    var unitMm: Boolean = true,
    //var wcs: String = "G54",

    var spindleOn: Boolean = false,
    var spindleDir: String? = null,
    var spindleSpeed: Int? = null,

    var feedZ: Double? = null,
    var feedXY: Double? = null
)

// ─────────────────────────────────────────────
//  Streamer events  (menggantikan sentinel -1)
// ─────────────────────────────────────────────

sealed class StreamerEvent {
    data class Progress(val activeLine: Int, val percent: Int, val elapsedMs: Long) : StreamerEvent()
    object Finished : StreamerEvent()
    object AbortedByAlarm : StreamerEvent()
    /** Z belum diketahui saat runFromHere dipanggil */
    object RunFromHereZUnknown : StreamerEvent()
}

// ─────────────────────────────────────────────
//  Callback interface  (FileFragment implements)
// ─────────────────────────────────────────────

interface GcodeStreamerCallback {
    /** Kirim string ke BT (dengan \n sudah ditambahkan oleh streamer) */
    fun onSendCommand(cmd: String)

    /** Kirim byte realtime ke BT (misal soft-reset 0x18) */
    fun onSendRealtime(byte: Byte)

    /** Dipanggil setiap kali baris aktif / progress berubah */
    fun onProgress(activeLine: Int, percent: Int, elapsedMs: Long)

    /** Streaming selesai normal */
    fun onFinished()

    /** Streaming dibatalkan karena ALARM */
    fun onAbortedByAlarm()

    /**
     * Z belum diketahui saat runFromHere dipanggil — menggantikan
     * sinyal sentinel onProgress(-1, -1, -1L) yang rapuh.
     */
    fun onRunFromHereZUnknown()

    /** Diteruskan ke StreamKeepAliveService */
    fun onUpdateServiceProgress(progress: Int, fileName: String)
}

// ─────────────────────────────────────────────
//  GcodeStreamer
// ─────────────────────────────────────────────

class GcodeStreamer(
    private val context: Context,
    private val callback: GcodeStreamerCallback
) {

    // ── public state (read-only dari luar) ──
    var runMode: RunMode = RunMode.IDLE
        private set

    var current: Int = 0
        private set

    // ── internal streaming state ──
    private var bytesInFlight = 0
    private val sendQueue = ArrayDeque<QueueItem>()
    private val inFlightQueue = ArrayDeque<Pair<Int, Boolean>>()
    private var lastActiveLine = 0
    private var currentFileName = ""

    // ── dwell / spindle ──
    private var dwellInjected = false
    private var enableSpindleDwell = true
    private var spindleDelaySeconds = 0
    private var rxSafe = 64

    // ── prefs cache (di-load sekali per run) ──
    private var cachedSafeZ = 10f
    private var cachedSpindleDelay = 2

    // ── timer ──
    private var startTime = 0L
    private var pauseStart = 0L
    private var pausedDuration = 0L
    private var timerRunning = false

    private val timerHandler = Handler(Looper.getMainLooper())
    private val timerRunnable = object : Runnable {
        override fun run() {
            if (!timerRunning || runMode != RunMode.RUNNING) return
            val elapsed = System.currentTimeMillis() - startTime - pausedDuration
            callback.onProgress(lastActiveLine, -1, elapsed)
            timerHandler.postDelayed(this, 1000)
        }
    }

    // ─────────────────────────────────────────
    //  Public API
    // ─────────────────────────────────────────

    /** Mulai streaming dari awal file */
    fun startRun(lines: List<String>, fileName: String = "") {
        currentFileName = fileName
        if (lines.isEmpty()) return

        loadPrefs()

        sendQueue.clear()
        sendQueue.addAll(lines.map { QueueItem(it, true) })

        current = 0
        runMode = RunMode.RUNNING
        dwellInjected = false
        bytesInFlight = 0
        inFlightQueue.clear()
        lastActiveLine = 0

        startTimer()
        sendUntilBufferFull()
    }

    /** Mulai streaming dari baris tertentu */
    fun runFromHere(lines: List<String>, index: Int, fileName: String = "") {
        currentFileName = fileName
        if (index !in lines.indices) return

        loadPrefs()

        val st = scanStatePro(lines, index)
        if (st.z == null) {
            callback.onRunFromHereZUnknown()
            return
        }

        runMode = RunMode.IDLE
        sendQueue.clear()
        inFlightQueue.clear()
        bytesInFlight = 0
        dwellInjected = false

        Handler(Looper.getMainLooper()).postDelayed({
            sendQueue.addAll(buildRunFromHereHeader(st).map { QueueItem(it, false) })
            sendQueue.addAll(lines.subList(index, lines.size).map { QueueItem(it, true) })

            current = index
            lastActiveLine = index
            runMode = RunMode.RUNNING

            startTimer()
            sendUntilBufferFull()
        }, 400)
    }

    /** Pause – kirim "!" ke GRBL */
    fun pause() {
        if (runMode != RunMode.RUNNING) return
        callback.onSendCommand("!")
        runMode = RunMode.PAUSED
        pauseStart = System.currentTimeMillis()
        timerHandler.removeCallbacks(timerRunnable)
    }

    /** Resume dari pause – kirim "~" ke GRBL */
    fun resume() {
        if (runMode != RunMode.PAUSED) return
        callback.onSendCommand("~")
        pausedDuration += System.currentTimeMillis() - pauseStart
        runMode = RunMode.RUNNING
        timerHandler.post(timerRunnable)
        sendUntilBufferFull()
    }

    /** Stop paksa – kirim soft-reset 0x18 */
    fun stop() {
        callback.onSendRealtime(0x18.toByte())
        resetInternalState()
        callback.onUpdateServiceProgress(100, "")
    }

    /** Dipanggil saat OK diterima dari GRBL */
    fun onOkReceived(lines: List<String>) {
        // [FIX] Guard: abaikan OK sisa buffer BT setelah stop/idle
        if (runMode == RunMode.IDLE) return
        if (inFlightQueue.isEmpty()) return

        val (finishedLen, wasFileLine) = inFlightQueue.removeFirst()
        bytesInFlight -= finishedLen

        if (wasFileLine) {
            current++
            val percent = ((current.toFloat() / lines.size) * 100).toInt()
            callback.onUpdateServiceProgress(percent.coerceIn(0, 99), currentFileName)
        }

        if (current >= lines.size && sendQueue.isEmpty() && inFlightQueue.isEmpty()) {
            Handler(Looper.getMainLooper()).post {
                stopRunFinished()
            }
            return
        }

        if (runMode == RunMode.RUNNING) {
            sendUntilBufferFull()
        }
    }

    /** Dipanggil saat GRBL masuk state ALARM */
    fun onAlarmDetected() {
        if (runMode == RunMode.IDLE) return
        resetInternalState()
        callback.onUpdateServiceProgress(100, "")
        callback.onAbortedByAlarm()
    }

    /**
     * Dipanggil dari updateFromPlanner di FileFragment.
     * Mengembalikan activeLine yang dihitung, atau -1 jika tidak perlu update.
     */
    fun calculateActiveLine(lastPlannerAvailable: Int, linesSize: Int): Int {
        val totalPlanner = 16
        val usedPlanner = totalPlanner - lastPlannerAvailable
        val lineActive = current + 1
        val calculated = lineActive - usedPlanner
        val activeLine = calculated
            .coerceIn(lastActiveLine, current)
            .coerceIn(0, linesSize - 1)

        if (activeLine == lastActiveLine) return -1
        lastActiveLine = activeLine
        return activeLine
    }

    // ─────────────────────────────────────────
    //  G-code state scanner
    // ─────────────────────────────────────────

    fun scanStatePro(lines: List<String>, target: Int): GcodeState {
        val st = GcodeState()

        for (i in 0 until target) {
            val l = lines[i].uppercase()
                .replace(Regex("\\(.*?\\)"), "")
                .trim()

            if (l.contains("G90")) st.absolute = true
            if (l.contains("G91")) st.absolute = false
            //if (l.contains("G21")) st.unitMm = true
            //if (l.contains("G20")) st.unitMm = false
            //Regex("G5[4-9]").find(l)?.let { st.wcs = it.value }

            // [FIX] Cari F secara terpisah agar menangkap format "G1 F300 Z-1.5"
            // (F sebelum Z), tidak hanya "G1 Z-1.5 F300".
            val hasZ = Regex("[^A-Z]Z[-0-9.]|^Z[-0-9.]").containsMatchIn(l) ||
                    l.contains("Z")
            val hasXY = l.contains("X") || l.contains("Y")
            val fMatch = Regex("F([0-9.]+)").find(l)

            if (l.startsWith("G1") || l.contains(" G1") || l.contains("\tG1")) {
                fMatch?.let {
                    val f = it.groupValues[1].toDoubleOrNull() ?: return@let
                    if (hasZ && !hasXY) st.feedZ = f
                    else if (hasXY)     st.feedXY = f
                }
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
        // [FIX] Gunakan cachedSafeZ & cachedSpindleDelay dari loadPrefs()
        // agar tidak buka SharedPreferences dua kali per run.
        val safeZ = cachedSafeZ
        val delay = cachedSpindleDelay

        val h = mutableListOf<String>()
        //h += if (st.unitMm) "G21" else "G20"
        //h += st.wcs
        h += "G90"
        h += "G17"
        h += "G0 Z%.3f".format(Locale.US, safeZ)

        if (st.spindleOn && st.spindleDir != null) {
            h += "G0 X0.000 Y0.000 ${st.spindleDir} S${st.spindleSpeed ?: 12000}"
        }
        h += "G4 P$delay"

        if (st.x != null && st.y != null) {
            h += "G0 X${st.x} Y${st.y}"
        }
        if (st.z != null) {
            h += "G1 Z${st.z} F${st.feedZ ?: 300.0}"
        }
        st.feedXY?.let { h += "F$it" }

        return h
    }

    // ─────────────────────────────────────────
    //  Internal helpers
    // ─────────────────────────────────────────

    private fun sendUntilBufferFull() {
        if (runMode != RunMode.RUNNING) return
        if (sendQueue.isEmpty()) return

        var sent = 0
        /** percobaan 3 atau 8 */
        while (sendQueue.isNotEmpty() && sent < 5) {
            val item = sendQueue.first()
            val cmd2 = item.cmd.trim() + "\n"
            val len = cmd2.length
            if ((bytesInFlight + len) >= (rxSafe - len)) break

            sendQueue.removeFirst()
            sent++
            callback.onSendCommand(cmd2)
            bytesInFlight += len
            inFlightQueue.addLast(len to item.isFileLine)

            val cmdUpper = item.cmd.trim().uppercase()
            if (enableSpindleDwell &&
                !dwellInjected &&
                spindleDelaySeconds > 0 &&
                item.isFileLine &&
                (cmdUpper.contains("M3") || cmdUpper.contains("M4"))) {
                val dwellCmd = "G4 P$spindleDelaySeconds\n"
                val dwellLen = dwellCmd.length

                if ((bytesInFlight + dwellLen) >= (rxSafe - dwellLen)) {
                    sendQueue.addFirst(QueueItem(dwellCmd.trim(), false))
                } else {
                    callback.onSendCommand(dwellCmd)
                    bytesInFlight += dwellLen
                    inFlightQueue.addLast(dwellLen to false)
                }
                dwellInjected = true
            }
        }
    }

    private fun stopRunFinished() {
        runMode = RunMode.IDLE
        stopTimer()
        callback.onUpdateServiceProgress(100, "")
        callback.onFinished()
    }

    private fun resetInternalState() {
        runMode = RunMode.IDLE
        sendQueue.clear()
        inFlightQueue.clear()
        bytesInFlight = 0
        stopTimer()
    }

    private fun startTimer() {
        startTime = System.currentTimeMillis()
        pausedDuration = 0
        timerRunning = true
        timerHandler.removeCallbacks(timerRunnable)
        timerHandler.post(timerRunnable)
    }

    private fun stopTimer() {
        timerRunning = false
        timerHandler.removeCallbacks(timerRunnable)
    }

    private fun loadPrefs() {
        val prefs = context.getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)
        spindleDelaySeconds = prefs.getInt("spindle_delay", 2)
        rxSafe = prefs.getInt("rx_safe", 128)
        enableSpindleDwell = prefs.getBoolean("enable_spindle_dwell", true)
        cachedSafeZ         = prefs.getFloat("safe_z", 10f)
        cachedSpindleDelay  = spindleDelaySeconds
    }
}