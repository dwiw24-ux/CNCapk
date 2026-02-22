package com.grbl.cnc.adapter

import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R

class GcodeAdapter(
    private val lines: List<String>,
    private val onClick: (Int) -> Unit
) : RecyclerView.Adapter<GcodeAdapter.VH>() {

    var activeLine = -1

    inner class VH(val tv: TextView) : RecyclerView.ViewHolder(tv) {
        init {
            tv.setOnClickListener {
                val pos = bindingAdapterPosition
                if (pos != RecyclerView.NO_POSITION) {
                    onClick(pos)
                }
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gcode, parent, false) as TextView
        return VH(tv)
    }

    override fun getItemCount() = lines.size

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.tv.text = "${position + 1}. ${lines[position]}"

        if (position == activeLine) {
            holder.tv.setBackgroundColor(Color.parseColor("#2A2A2A"))
            holder.tv.setTextColor(Color.WHITE)
        } else {
            holder.tv.setBackgroundColor(Color.TRANSPARENT)
            holder.tv.setTextColor(Color.parseColor("#BBBBBB"))
        }
    }
}