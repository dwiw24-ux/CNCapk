package com.grbl.cnc.adapter

import android.annotation.SuppressLint
import android.graphics.Color
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import kotlin.math.max
import kotlin.math.min

class AdapterGcodeEditor(
    private val lines: MutableList<String>,
    private val onLineSelected: (Int?) -> Unit,
    private val onSelectionChanged: (Int) -> Unit,
    private val onEditRequested: (Int) -> Unit
) : RecyclerView.Adapter<AdapterGcodeEditor.ViewHolder>() {

    // 🔹 Multi selection
    val selectedPositions = mutableSetOf<Int>()
    private var rangeStart: Int? = null

    class ViewHolder(val tv: TextView) : RecyclerView.ViewHolder(tv)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val tv = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_gcode_line, parent, false) as TextView
        return ViewHolder(tv)
    }

    override fun getItemCount() = lines.size

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {

        holder.tv.text = "${position + 1}  ${lines[position]}"

        // Highlight jika terseleksi
        if (selectedPositions.contains(position)) {
            holder.tv.setBackgroundColor(Color.DKGRAY)
            holder.tv.setTextColor(Color.GREEN)
        } else {
            holder.tv.setBackgroundColor(Color.TRANSPARENT)
            holder.tv.setTextColor(Color.WHITE)
        }
        holder.tv.setOnClickListener {
            handleSelection(position)
        }
        holder.tv.setOnLongClickListener {
            onEditRequested(position)
            true
        }
    }

    // 🔹 Remove multiple selected lines
    @SuppressLint("NotifyDataSetChanged")
    fun removeSelected() {
        val sorted = selectedPositions.sortedDescending()
        sorted.forEach { lines.removeAt(it) }
        selectedPositions.clear()
        rangeStart = null
        notifyDataSetChanged()
        onLineSelected(null)
        onSelectionChanged(0)
    }

    @SuppressLint("NotifyDataSetChanged")
    private fun handleSelection(position: Int) {

        // STATE 1 → Belum ada selection
        if (selectedPositions.isEmpty()) {
            rangeStart = position
            selectedPositions.add(position)
            notifyDataSetChanged()
            onLineSelected(position)
            onSelectionChanged(1)
            return
        }

        // STATE 2 → Baru 1 line dipilih
        if (rangeStart != null) {
            val start = rangeStart!!
            val minPos = min(start, position)
            val maxPos = max(start, position)

            selectedPositions.clear()
            for (i in minPos..maxPos) {
                selectedPositions.add(i)
            }
            rangeStart = null
            notifyDataSetChanged()
            onLineSelected(selectedPositions.maxOrNull())
            onSelectionChanged(selectedPositions.size)
            return
        }

        // STATE 3 → Range sudah ada
        // Klik lagi → CLEAR
        selectedPositions.clear()
        rangeStart = null
        notifyDataSetChanged()
        onLineSelected(null)
        onSelectionChanged(0)
    }

    fun addLine(position: Int, text: String) {
        lines.add(position, text)
        notifyItemInserted(position)
    }

    fun updateLine(position: Int, text: String) {
        lines[position] = text
        notifyItemChanged(position)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun clearSelection() {
        selectedPositions.clear()
        rangeStart = null
        notifyDataSetChanged()
        onLineSelected(null)
        onSelectionChanged(0)
    }

    @SuppressLint("NotifyDataSetChanged")
    fun selectSingleLine(position: Int) {
        selectedPositions.clear()
        selectedPositions.add(position)
        rangeStart = null
        notifyDataSetChanged()
        onLineSelected(position)
        onSelectionChanged(1)
    }

    fun goToLineSmart(position: Int) {
        if (position < 0 || position >= lines.size) return

        // STATE 1 → Tidak ada selection
        if (selectedPositions.isEmpty()) {
            selectSingleLine(position)
            return
        }

        // STATE 2 → Sudah 1x klik (rangeStart aktif)
        if (rangeStart != null) {
            val start = rangeStart!!
            val minPos = min(start, position)
            val maxPos = max(start, position)

            selectedPositions.clear()
            for (i in minPos..maxPos) {
                selectedPositions.add(i)
            }
            rangeStart = null
            notifyDataSetChanged()
            onLineSelected(selectedPositions.maxOrNull())
            onSelectionChanged(selectedPositions.size)
            return
        }

        // STATE 3 → Sudah range
        // Reset dan pilih single baru
        selectSingleLine(position)
    }

    fun getAllLines(): List<String> = lines
}