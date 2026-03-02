package com.grbl.cnc.ui.pager

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.grbl.cnc.R
import com.grbl.cnc.ui.MainActivity
import android.view.MotionEvent
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.activityViewModels
import com.grbl.cnc.grbl.GrblState
import kotlin.getValue
import androidx.appcompat.app.AlertDialog

class JogFragment : Fragment(R.layout.frag_jog) {

    private var isHolding = false
    private var downTime = 0L
    private val tapThreshold = 500L   // ms
    private val continuousInterval = 100L
    private var jogHandler = Handler(Looper.getMainLooper())
    private var jogRunnable: Runnable? = null
    private var step = 5.0
    private var feed = 1000

    private var probeDist = 0f
    private var probePlate = 0f
    private var probeRetract = 0f
    private var probeFeedFast = 0
    private var probeFeedSlow = 0

    private val viewModel: MainViewModel by activityViewModels()
    private var currentState: GrblState = GrblState.UNKNOWN
    private val jogButtons = mutableListOf<ImageButton>()
    private lateinit var btnHoming : ImageButton
    private lateinit var btnStop: ImageButton
    private lateinit var jogGo0: Button
    private lateinit var jogAll0: Button
    private lateinit var jogX0: Button
    private lateinit var jogY0: Button
    private lateinit var jogZ0: Button
    private lateinit var g54: Button
    private lateinit var g55: Button
    private lateinit var g56: Button
    private lateinit var g57: Button
    private lateinit var g58: Button
    private lateinit var g59: Button
    private lateinit var btnProbe: Button

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtStep = view.findViewById<TextView>(R.id.txtStep)
        val txtFeed = view.findViewById<TextView>(R.id.txtFeed)

        val seekStep = view.findViewById<SeekBar>(R.id.seekStep)
        val seekFeed = view.findViewById<SeekBar>(R.id.seekFeed)

        btnHoming = view.findViewById(R.id.btnHoming)
        btnStop = view.findViewById(R.id.btnStop)
        jogGo0 = view.findViewById(R.id.jogGo0)
        jogAll0 = view.findViewById(R.id.jogAll0)
        jogX0 = view.findViewById(R.id.jogX0)
        jogY0 = view.findViewById(R.id.jogY0)
        jogZ0 = view.findViewById(R.id.jogZ0)
        g54 = view.findViewById(R.id.g54)
        g55 = view.findViewById(R.id.g55)
        g56 = view.findViewById(R.id.g56)
        g57 = view.findViewById(R.id.g57)
        g58 = view.findViewById(R.id.g58)
        g59 = view.findViewById(R.id.g59)
        btnProbe = view.findViewById(R.id.btnProbe)

        viewModel.grblRunMode.observe(viewLifecycleOwner) { state ->
            currentState = state

            val enableJog = state == GrblState.IDLE || state == GrblState.JOG
            jogButtons.forEach {
                it.isEnabled = enableJog
                it.alpha = if (enableJog) 1f else 0.4f
            }
            if (!enableJog) {
                stopJog()
            }

            val idleOnly = (state == GrblState.IDLE)
            btnHoming.isEnabled = idleOnly
            jogGo0.isEnabled = idleOnly
            jogAll0.isEnabled = idleOnly
            jogX0.isEnabled = idleOnly
            jogY0.isEnabled = idleOnly
            jogZ0.isEnabled = idleOnly
            g54.isEnabled = idleOnly
            g55.isEnabled = idleOnly
            g56.isEnabled = idleOnly
            g57.isEnabled = idleOnly
            g58.isEnabled = idleOnly
            g59.isEnabled = idleOnly
            btnProbe.isEnabled = idleOnly

            val alpha = if (idleOnly) 1f else 0.4f
            btnHoming.alpha = alpha
            jogGo0.alpha = alpha
            jogAll0.alpha = alpha
            jogX0.alpha = alpha
            jogY0.alpha = alpha
            jogZ0.alpha = alpha
            g54.alpha = alpha
            g55.alpha = alpha
            g56.alpha = alpha
            g57.alpha = alpha
            g58.alpha = alpha
            g59.alpha = alpha
            btnProbe.alpha = alpha
        }

        seekStep.progress = 50 // default
        seekStep.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                step = when {
                    p < 5 -> 0.1
                    p < 25 -> 1.0
                    p < 50 -> 5.0
                    p < 75 -> 10.0
                    else -> 100.0
                }
                txtStep.text = "Step: $step mm"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        seekFeed.progress = feed
        seekFeed.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            @SuppressLint("SetTextI18n")
            override fun onProgressChanged(sb: SeekBar, p: Int, fromUser: Boolean) {
                feed = maxOf(100, p)
                txtFeed.text = "Feed: $feed mm/menit"
            }
            override fun onStartTrackingTouch(sb: SeekBar) {}
            override fun onStopTrackingTouch(sb: SeekBar) {}
        })

        bindJogButton(view, R.id.btnXPlus, "X", 1)
        bindJogButton(view, R.id.btnXMinus, "X", -1)
        bindJogButton(view, R.id.btnYPlus, "Y", 1)
        bindJogButton(view, R.id.btnYMinus, "Y", -1)
        bindJogButton(view, R.id.btnZPlus, "Z", 1)
        bindJogButton(view, R.id.btnZMinus, "Z", -1)

        btnHoming.setOnClickListener {
            showConfirmDialog(
                "HOMONG",
                "Run homing cycle?"
            ) {
                sendCommand("\$H\n")
            }
        }
        btnStop.setOnClickListener {
                (activity as? MainActivity)?.btService?.sendRealtime(0x85.toByte())
        }
        jogGo0.setOnClickListener {
            showConfirmDialog(
                "GO TO ZERO",
                "Move to zero work coordinate?"
            ) {
                sendCommand("G90\n")
                sendCommand("G53 G0 Z0\n")
                sendCommand("G90 G0 X0 Y0\n")
                sendCommand("G90 G0 Z0\n")
            }
        }
        jogAll0.setOnClickListener {
            showConfirmDialog(
                "ZERO ALL",
                "Set zero all axiz?"
            ) {
                sendCommand("G10 L20 P0 X0Y0Z0\n")
            }
        }
        jogX0.setOnClickListener {
            sendCommand("G10 L20 P0 X0\n")
        }
        jogY0.setOnClickListener {
            sendCommand("G10 L20 P0 Y0\n")
        }
        jogZ0.setOnClickListener {
            sendCommand("G10 L20 P0 Z0\n")
        }
        g54.setOnClickListener {
            sendCommand("G54\n")
            sendCommand("\$G\n")
        }
        g55.setOnClickListener {
            sendCommand("G55\n")
            sendCommand("\$G\n")
        }
        g56.setOnClickListener {
            sendCommand("G56\n")
            sendCommand("\$G\n")
        }
        g57.setOnClickListener {
            sendCommand("G57\n")
            sendCommand("\$G\n")
        }
        g58.setOnClickListener {
            sendCommand("G58\n")
            sendCommand("\$G\n")
        }
        g59.setOnClickListener {
            sendCommand("G59\n")
            sendCommand("\$G\n")
        }
        btnProbe.setOnClickListener {

            val prefs = requireContext().getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

            probeFeedFast = prefs.getInt("probe_feed", 100)
            probeFeedSlow = probeFeedFast / 2
            probeDist = prefs.getFloat("probe_dist", 50f)
            probePlate = prefs.getFloat("probe_plate", 1.5f)
            probeRetract = prefs.getFloat("probe_retract", 5f)

            showConfirmDialog(
                "PROBE",
                "Run Probe Z axiz ?"
            ) {
                sendCommand("G4 P2\n")
                sendCommand("G91\n")
                sendCommand("G38.2 Z-$probeDist F$probeFeedFast\n")
                sendCommand("G10 L20 P0 Z$probePlate\n")

                sendCommand("G4 P1\n")
                sendCommand("G0 Z$probeRetract\n")
                sendCommand("G4 P1\n")
                sendCommand("G38.2 Z-$probeDist F$probeFeedSlow\n")
                sendCommand("G10 L20 P0 Z1.500\n")

                sendCommand("G4 P1\n")
                sendCommand("G0 Z$probeRetract\n")
                sendCommand("G90\n")
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        jogRunnable?.let {
            jogHandler.removeCallbacks(it)
        }
    }

    private fun sendCommand(cmd: String) {
        val blu = (activity as? MainActivity)?.btService ?: return
        blu.send(cmd)
    }

    private fun sendJog(axis: String, dir: Int, isContinous: Boolean = false) {
        (activity as? MainActivity)?.isJogging = true
        val distance = if (isContinous) {
            0.5 * dir
        } else {
            step * dir
        }
        val cmd = "\$J=G91 G21 $axis$distance F$feed\n"
        (activity as? MainActivity)?.btService?.send(cmd)
    }

    private fun stopJog() {
        jogRunnable?.let { jogHandler.removeCallbacks(it) }
        isHolding = false

        val act = activity as? MainActivity
        act?.isJogging = false
        act?.btService?.sendRealtime(0x85.toByte())
    }


    @SuppressLint("ClickableViewAccessibility")
    private fun bindJogButton(
        root: View,
        id: Int,
        axis: String,
        dir: Int
    ) {
        val btn = root.findViewById<ImageButton>(id)

        jogButtons.add(btn)

        btn.setOnTouchListener { v, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    isHolding = false

                    jogRunnable = Runnable {
                        isHolding = true

                        sendJog(axis, dir, true)
                        jogHandler.postDelayed(jogRunnable!!, continuousInterval)
                    }
                    jogHandler.postDelayed(jogRunnable!!, tapThreshold)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    //jogHandler.removeCallbacks(jogRunnable!!)
                    jogRunnable?.let { jogHandler.removeCallbacks(it) }
                    val elapsed = System.currentTimeMillis() - downTime

                    if (!isHolding && elapsed < tapThreshold) {
                        sendJog(axis, dir, false)
                    } else {
                        stopJog()
                    }
                    v.performClick()
                }
            }
            true
        }
    }

    private fun showConfirmDialog(
        title: String,
        message: String,
        onConfirm: () -> Unit
    ) {
        AlertDialog.Builder(requireContext())
            .setTitle(title)
            .setMessage(message)
            .setPositiveButton("YES") { _, _ ->
                onConfirm()
            }
            .setNegativeButton("CANCEL", null)
            .show()
    }
}
