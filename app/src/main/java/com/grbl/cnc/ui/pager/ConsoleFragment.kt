package com.grbl.cnc.ui.pager

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
import com.grbl.cnc.ui.MainActivity


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

    private fun appendRx(text: String) {
        txtConsole.append("<< $text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun sendCommand(cmd: String) {
        val fixed = if (cmd.endsWith("\n")) cmd else "$cmd\n"

        waitingCommandOk = true
        txtConsole.append(">> $cmd\n")

        (activity as? MainActivity)?.btService?.send(fixed)
    }

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

    private fun showStartupHeader() {
        if (isStartupDone) return
        isStartupDone = true

        (activity as? MainActivity)?.btService?.send("$\n")
        (activity as? MainActivity)?.btService?.send("\$I\n")
        (activity as? MainActivity)?.btService?.send("$$\n")
        (activity as? MainActivity)?.btService?.send("$#\n")
        (activity as? MainActivity)?.btService?.send("\$G\n")
    }

    private fun appendSystem(text: String) {
        txtConsole.append("$text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private fun appendSetting(text: String) {
        txtConsole.append("⚙ $text\n")
        scroll.post { scroll.fullScroll(View.FOCUS_DOWN) }
    }

    private val consoleListener: (String) -> Unit = { data ->
        activity?.runOnUiThread {
            data.lines().forEach { line ->
                val text = line.trim()
                if (text.isEmpty()) return@forEach

// ===== OK HANDLER (LOGIC ONLY, NO UI) =====
                if (text.equals("ok", true)) {
                    if (waitingCommandOk) {
                        waitingCommandOk = false
                    }
                    return@forEach
                }

                if (receivingSettings) {
                    if (text.startsWith("$")) {
                        appendSetting(text)
                        return@forEach
                    }
                    if (text == "ok" || text.startsWith("<")) {
                        receivingSettings = false
                        return@forEach
                    }
                }

                if (verboseEnabled) {
                    appendRx(text)
                } else {
                    if (         // realtime status
                        text.startsWith("$",true) ||
                        text.startsWith("G", true) ||
                        text.startsWith("[", true) ||
                        text.startsWith("Grbl", true)
                    ) {
                        if (text.startsWith("Grbl")) {
                            appendRx(text)
                            showStartupHeader()
                            return@forEach
                        }
                        appendRx(text)
                    }
                }
            }
        }
    }

    fun append(raw: String) {
        activity?.runOnUiThread {
            raw.lines().forEach { line ->
                val text = line.trim()
                if (text.isNotEmpty()) {
                    appendRx(text)
                }
            }
        }
    }

}
