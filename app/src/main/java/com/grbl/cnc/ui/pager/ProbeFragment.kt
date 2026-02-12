package com.grbl.cnc.ui.pager

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.grbl.cnc.R
import com.grbl.cnc.ui.MainActivity

class ProbeFragment : Fragment(R.layout.frag_probe) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val edtFeed = view.findViewById<EditText>(R.id.edtProbeFeed)
        val edtDist = view.findViewById<EditText>(R.id.edtProbeDist)
        val edtPlate = view.findViewById<EditText>(R.id.edtProbePlate)
        val edtRetract = view.findViewById<EditText>(R.id.edtProbeRetract)
        val txtResult = view.findViewById<TextView>(R.id.txtProbeResult)

        val bt = (activity as? MainActivity)?.btService

        view.findViewById<Button>(R.id.btnSetZ).setOnClickListener {
            val plate = edtPlate.text.toString().toDoubleOrNull() ?: 0.0
            bt?.send("G10 L20 P0 Z$plate\n")
            txtResult.text = "Z set to $plate\n"
        }

        view.findViewById<Button>(R.id.btnSafeZ).setOnClickListener {
            bt?.send("G90 G0 Z10\n")
        }

        view.findViewById<Button>(R.id.btnSetXY).setOnClickListener {
            bt?.send("G10 L20 P0 X0 Y0\n")
        }

        view.findViewById<Button>(R.id.btnGoZero).setOnClickListener {
            bt?.send("G90 G0 X0 Y0\n")
        }

        view.findViewById<Button>(R.id.btnProbeZ).setOnClickListener {

            val feedFast = edtFeed.text.toString().toIntOrNull() ?: 100
            val feedSlow = feedFast / 2
            val dist = edtDist.text.toString().toDoubleOrNull() ?: 50.0
            val plate = edtPlate.text.toString().toDoubleOrNull() ?: 1.5
            val retract = edtRetract.text.toString().toDoubleOrNull() ?: 5.0

            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener

            txtResult.text = "Auto Z Probe"

            Thread {
                bt.send("G91\n")
                Thread.sleep(2000)

                bt.send("G38.2 Z-${dist} F$feedFast\n")
                bt.send("G10 L20 P0 Z$plate\n")
                Thread.sleep(3000)

                bt.send("G0 Z$retract\n")
                Thread.sleep(2000)

                bt.send("G38.2 Z-${dist} F$feedSlow\n")
                bt.send("G10 L20 P0 Z$plate\n")
                Thread.sleep(4000)

                bt.send("G0 Z$retract\n")
                bt.send("G90\n")

                activity?.runOnUiThread {
                    txtResult.text = "✅ Auto Z OK"
                }
            }.start()
        }
    }
}

