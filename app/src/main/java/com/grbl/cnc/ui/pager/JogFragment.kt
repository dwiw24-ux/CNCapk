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
import androidx.fragment.app.activityViewModels
import com.grbl.cnc.grbl.GrblState
import kotlin.getValue

class JogFragment : Fragment(R.layout.frag_jog) {

    private var isHolding = false
    private var downTime = 0L
    private val tapThreshold = 200L   // ms
    private val continuousInterval = 120L
    private var jogHandler = Handler(Looper.getMainLooper())
    private var jogRunnable: Runnable? = null
    private var step = 5.0
    private var feed = 1000
    private var probeStep = 0
    private var isProbing = false

    private var probeDist = 0f
    private var probePlate = 0f
    private var probeRetract = 0f
    private var probeFeedFast = 0
    private var probeFeedSlow = 0

    private val handler = Handler(Looper.getMainLooper())
    private val viewModel: MainViewModel by activityViewModels()
    private var currentState: GrblState = GrblState.UNKNOWN

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtStep = view.findViewById<TextView>(R.id.txtStep)
        val txtFeed = view.findViewById<TextView>(R.id.txtFeed)

        val seekStep = view.findViewById<SeekBar>(R.id.seekStep)
        val seekFeed = view.findViewById<SeekBar>(R.id.seekFeed)

        viewModel.grblRunMode.observe(viewLifecycleOwner) { state ->
            currentState = state

            if (!isProbing) return@observe

            if (currentState == GrblState.ALARM) {

                isProbing = false
                probeStep = 0

                handler.removeCallbacksAndMessages(null)

                return@observe
            }
            if (currentState == GrblState.IDLE) {
                handleProbeStep()
            }
        }

        seekStep.progress = 50 // default

        seekStep.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
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

        view.findViewById<ImageButton>(R.id.btnHoming).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("\$H\n")
        }
        view.findViewById<ImageButton>(R.id.btnStop).setOnClickListener {
            (activity as? MainActivity)?.btService?.sendRealtime(0x85.toByte())
        }
        view.findViewById<Button>(R.id.jogGo0).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G90\n")
            (activity as? MainActivity)?.btService?.send("G53 G0 Z0\n")
            (activity as? MainActivity)?.btService?.send("G90 G0 X0 Y0\n")
            (activity as? MainActivity)?.btService?.send("G90 G0 Z0\n")
        }
        view.findViewById<Button>(R.id.jogAll0).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G10 L20 P0 X0Y0Z0\n")
        }
        view.findViewById<Button>(R.id.jogX0).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G10 L20 P0 X0\n")
        }
        view.findViewById<Button>(R.id.jogY0).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G10 L20 P0 Y0\n")
        }
        view.findViewById<Button>(R.id.jogZ0).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G10 L20 P0 Z0\n")
        }
        view.findViewById<Button>(R.id.g54).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G54\n")
            (activity as? MainActivity)?.btService?.send("\$G\n")
        }
        view.findViewById<Button>(R.id.g55).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G55\n")
            (activity as? MainActivity)?.btService?.send("\$G\n")
        }
        view.findViewById<Button>(R.id.g56).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G56\n")
            (activity as? MainActivity)?.btService?.send("\$G\n")
        }
        view.findViewById<Button>(R.id.g57).setOnClickListener {
            (activity as? MainActivity)?.btService?.send("G57\n")
            (activity as? MainActivity)?.btService?.send("\$G\n")
        }
        view.findViewById<Button>(R.id.btnProbe).setOnClickListener {

            val prefs = requireContext().getSharedPreferences("cnc_settings", Context.MODE_PRIVATE)

            probeFeedFast = prefs.getInt("probe_feed", 100)
            probeFeedSlow = probeFeedFast / 2
            probeDist = prefs.getFloat("probe_dist", 50f)
            probePlate = prefs.getFloat("probe_plate", 1.5f)
            probeRetract = prefs.getFloat("probe_retract", 5f)

            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener

            if (!bt.isConnected) return@setOnClickListener
            if (isProbing) return@setOnClickListener

            isProbing = true
            probeStep = 1

            bt.send("G91\n")
            bt.send("G38.2 Z-$probeDist F$probeFeedFast\n")
        }
    }

    private fun sendJog(axis: String, dir: Int) {
        (activity as? MainActivity)?.isJogging = true
        val distance = step * dir
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

        btn.setOnTouchListener { v, event ->
            when (event.action) {

                MotionEvent.ACTION_DOWN -> {
                    downTime = System.currentTimeMillis()
                    isHolding = false

                    jogRunnable = Runnable {
                        isHolding = true

                        sendJog(axis, dir)
                        jogHandler.postDelayed(jogRunnable!!, continuousInterval)
                    }
                    jogHandler.postDelayed(jogRunnable!!, tapThreshold)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    jogHandler.removeCallbacks(jogRunnable!!)

                    val elapsed = System.currentTimeMillis() - downTime

                    if (!isHolding && elapsed < tapThreshold) {
                        sendJog(axis, dir)
                    }
                    stopJog()
                    v.performClick()
                }
            }
            true
        }
    }

    private fun handleProbeStep() {

        val bt = (activity as? MainActivity)?.btService ?: return

        when (probeStep) {

            // FAST PROBE SELESAI
            1 -> {
                handler.postDelayed({
                    bt.send("G0 Z$probeRetract\n")
                    probeStep = 2
                }, 400)
            }

            // RETRACT SELESAI → MULAI SLOW PROBE
            2 -> {
                bt.send("G38.2 Z-$probeDist F$probeFeedSlow\n")
                probeStep = 3
            }

            // SLOW PROBE SELESAI
            3 -> {
                handler.postDelayed({
                    bt.send("G10 L20 P0 Z$probePlate\n")
                    bt.send("G0 Z5.000\n")
                    bt.send("G90\n")

                    isProbing = false
                    probeStep = 0

                }, 300)
            }
        }
    }
}
