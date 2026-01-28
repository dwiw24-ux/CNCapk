package com.grbl.cnc.ui.pager

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.SeekBar
import androidx.fragment.app.Fragment
import com.grbl.cnc.R
import com.grbl.cnc.ui.MainActivity
import android.view.MotionEvent
import android.widget.TextView

class JogFragment : Fragment(R.layout.frag_jog) {

    private var isHolding = false
    private var downTime = 0L

    private val TAP_THRESHOLD = 200L   // ms
    private val CONTINUOUS_INTERVAL = 120L
    private var jogHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var jogRunnable: Runnable? = null
    private var step = 1.0
    private var feed = 500

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val txtStep = view.findViewById<TextView>(R.id.txtStep)
        val txtFeed = view.findViewById<TextView>(R.id.txtFeed)

        val seekStep = view.findViewById<SeekBar>(R.id.seekStep)
        val seekFeed = view.findViewById<SeekBar>(R.id.seekFeed)


        seekStep.progress = 25 // default

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

        view.findViewById<ImageButton>(R.id.btnHoming)
            .setOnClickListener {
                (activity as? MainActivity)?.btService?.send("\$H\n")
            }

        view.findViewById<ImageButton>(R.id.btnStop)
            .setOnClickListener {
                (activity as? MainActivity)?.btService?.sendRealtime(0x85.toByte())
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
                        jogHandler.postDelayed(jogRunnable!!, CONTINUOUS_INTERVAL)
                    }

                    jogHandler.postDelayed(jogRunnable!!, TAP_THRESHOLD)
                }

                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {

                    jogHandler.removeCallbacks(jogRunnable!!)

                    val elapsed = System.currentTimeMillis() - downTime

                    if (!isHolding && elapsed < TAP_THRESHOLD) {
                        // 👉 TAP
                        sendJog(axis, dir)
                    }

                    stopJog()
                    v.performClick()
                }
            }
            true
        }
    }

}
