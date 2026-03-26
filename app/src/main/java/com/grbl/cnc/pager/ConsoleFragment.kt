package com.grbl.cnc.pager

import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import com.google.android.material.switchmaterial.SwitchMaterial
import com.grbl.cnc.R
import com.grbl.cnc.MainActivity


class ConsoleFragment : Fragment(R.layout.frag_console) {

    private var isStartupDone = false
    private var verboseEnabled = false
    private var receivingSettings = false
    private var waitingCommandOk = false

    private lateinit var txtConsole: TextView
    private lateinit var scroll: ScrollView
    private lateinit var edtCommand: EditText

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        txtConsole = view.findViewById(R.id.txtConsole)
        scroll = view.findViewById(R.id.scrollConsole)
        edtCommand = view.findViewById(R.id.edtCommand)
        val btnSend = view.findViewById<ImageButton>(R.id.btnSend)

        // SEND COMMAND
        btnSend.setOnClickListener {
            val cmd = edtCommand.text.toString().trim()
            if (cmd.isNotEmpty()) {
                sendCommand(cmd)
                edtCommand.setText("")
            }
        }

        val switchVerbose = view.findViewById<SwitchMaterial>(R.id.switchVerbose)
        switchVerbose.isChecked = false // DEFAULT OFF
        switchVerbose.setOnCheckedChangeListener { _, isChecked ->
            verboseEnabled = isChecked
            appendRx(
                if (isChecked)
                    "Verbose ON – show all GRBL output"
                else
                    "Verbose OFF – filtered output"
            )
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val bottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom
            v.setPadding(0, 0, 0, bottom)
            insets
        }
    }

    // ─────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────

    override fun onStart() {
        super.onStart()
        val main = activity as? MainActivity ?: return
        main.btService.addRawListener(consoleListener)

        if (main.pendingGrblConnect) {
            main.pendingGrblConnect = false
            receivingSettings = true
            showStartupHeader()
        }
    }

    override fun onStop() {
        super.onStop()
        (activity as? MainActivity)
            ?.btService
            ?.removeRawListener(consoleListener)
    }

    // ─────────────────────────────────────────
    //  UI helpers
    // ─────────────────────────────────────────

    private fun appendRx(text: String) {
        txtConsole.append("<< $text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun appendSystem(text: String) {
        txtConsole.append("$text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun appendSetting(text: String) {
        txtConsole.append("⚙ $text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    // ─────────────────────────────────────────
    //  Command sender
    // ─────────────────────────────────────────

    private fun sendCommand(cmd: String) {
        val fixed = if (cmd.endsWith("\n")) cmd else "$cmd\n"
        waitingCommandOk = true
        txtConsole.append(">> $cmd\n")
        (activity as? MainActivity)?.btService?.send(fixed)
    }

    // ─────────────────────────────────────────
    //  Startup
    // ─────────────────────────────────────────

    private fun showStartupHeader() {
        if (isStartupDone) return
        isStartupDone = true

        // [FIX] Simpan referensi btService sekali agar tidak memanggil
        // (activity as? MainActivity)?.btService berulang kali — menghindari
        // potensi NPE jika activity di-detach di antara pengiriman.
        val bt = (activity as? MainActivity)?.btService ?: return
        bt.send("$\n")
        bt.send("\$I\n")
        bt.send("$$\n")
        bt.send("$#\n")
        bt.send("\$G\n")
    }

    // ─────────────────────────────────────────
    //  BT raw listener
    // ─────────────────────────────────────────

    private val consoleListener: (String) -> Unit = { data ->
        activity?.runOnUiThread {
            data.lines().forEach { line ->
                val text = line.trim()
                if (text.isEmpty()) return@forEach

                // ── OK handler ──
                if (text.equals("ok", ignoreCase = true)) {
                    if (waitingCommandOk) waitingCommandOk = false
                    // [FIX] Jika sedang receivingSettings, "ok" menandai akhir
                    // sesi settings — matikan flag di sini juga, bukan hanya
                    // dicek di blok receivingSettings di bawah (yang kini
                    // tidak akan tercapai karena return@forEach di atas).
                    if (receivingSettings) receivingSettings = false
                    return@forEach
                }

                // ── Settings dump ──
                if (receivingSettings) {
                    if (text.startsWith("$")) {
                        appendSetting(text)
                        return@forEach
                    }
                    // [FIX] Hapus pengecekan text == "ok" di sini karena
                    // sudah ditangani di blok OK handler di atas dan tidak
                    // akan pernah tercapai (return@forEach sudah dilakukan).
                    if (text.startsWith("<")) {
                        receivingSettings = false
                        return@forEach
                    }
                }

                // ── Display filter ──
                if (verboseEnabled) {
                    appendRx(text)
                } else {
                    // [FIX] Perbaiki startsWith("$", true) — parameter kedua
                    // pada String.startsWith() adalah ignoreCase (Boolean),
                    // bukan offset. "$" tidak punya huruf besar/kecil sehingga
                    // tidak salah secara fungsional, tapi membingungkan.
                    // Gunakan overload tanpa ignoreCase untuk karakter non-huruf.
                    val show = text.startsWith("$") ||
                            text.startsWith("G", ignoreCase = true) ||
                            text.startsWith("[") ||
                            text.startsWith("Grbl", ignoreCase = true)

                    if (show) {
                        if (text.startsWith("Grbl", ignoreCase = true)) {
                            // [FIX] Reset isStartupDone agar showStartupHeader()
                            // dapat berjalan ulang saat GRBL melakukan soft-reset
                            // atau reconnect di tengah sesi.
                            isStartupDone = false
                            appendRx(text)
                            showStartupHeader()
                        } else {
                            appendRx(text)
                        }
                    }
                }
            }
        }
    }

    // ─────────────────────────────────────────
    //  Public API (dipanggil dari luar fragment)
    // ─────────────────────────────────────────

    fun append(raw: String) {
        activity?.runOnUiThread {
            raw.lines().forEach { line ->
                val text = line.trim()
                if (text.isNotEmpty()) appendRx(text)
            }
        }
    }
}