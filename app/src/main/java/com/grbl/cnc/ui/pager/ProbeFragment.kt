package com.grbl.cnc.ui.pager

import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.fragment.app.Fragment
import com.grbl.cnc.R
import com.grbl.cnc.ui.MainActivity

class ProbeFragment : Fragment(R.layout.frag_probe) {

    private lateinit var txtResult: TextView

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {

        val edtFeed = view.findViewById<EditText>(R.id.edtProbeFeed)
        val edtDist = view.findViewById<EditText>(R.id.edtProbeDist)
        val edtPlate = view.findViewById<EditText>(R.id.edtPlate)
        val txtResult = view.findViewById<TextView>(R.id.txtProbeResult)

        val bt = (activity as? MainActivity)?.btService

        view.findViewById<Button>(R.id.btnProbeZ).setOnClickListener {

            val feed = edtFeed.text.toString().toIntOrNull() ?: 100
            val dist = edtDist.text.toString().toDoubleOrNull() ?: 20.0

            val cmd = "G38.2 Z-${dist} F$feed"
            bt?.send(cmd)

            txtResult.text = "Probing Z..."
        }

        view.findViewById<Button>(R.id.btnSetZ).setOnClickListener {
            val plate = edtPlate.text.toString().toDoubleOrNull() ?: 0.0
            bt?.send("G10 L20 P1 Z$plate")
            txtResult.text = "Z set to $plate"
        }

        view.findViewById<Button>(R.id.btnSafeZ).setOnClickListener {
            bt?.send("G0 Z10")
        }

        view.findViewById<Button>(R.id.btnSetXY).setOnClickListener {
            bt?.send("G10 L20 P1 X0 Y0")
        }

        view.findViewById<Button>(R.id.btnGoZero).setOnClickListener {
            bt?.send("G0 X0 Y0")
        }

        view.findViewById<Button>(R.id.btnAutoZ).setOnClickListener {

            val feedFast = edtFeed.text.toString().toIntOrNull() ?: 200
            val feedSlow = feedFast / 4
            val dist = edtDist.text.toString().toDoubleOrNull() ?: 20.0
            val plate = edtPlate.text.toString().toDoubleOrNull() ?: 0.0
            val retract = view.findViewById<EditText>(R.id.edtRetract)
                .text.toString().toDoubleOrNull() ?: 5.0

            val bt = (activity as? MainActivity)?.btService ?: return@setOnClickListener

            txtResult.text = "Auto Z (2x touch)..."

            Thread {
                bt.send("G91")
                Thread.sleep(120)

                bt.send("G38.2 Z-${dist} F$feedFast")
                Thread.sleep(300)

                bt.send("G0 Z2")
                Thread.sleep(120)

                bt.send("G38.2 Z-5 F$feedSlow")
                Thread.sleep(400)

                bt.send("G10 L20 P1 Z$plate")
                Thread.sleep(120)

                bt.send("G0 Z$retract")
                Thread.sleep(120)

                bt.send("G90")

                activity?.runOnUiThread {
                    txtResult.text = "✅ Auto Z OK (2x)"
                }
            }.start()
        }
    }
}

