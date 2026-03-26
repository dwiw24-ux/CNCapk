package com.grbl.cnc.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.grbl.cnc.R
import com.grbl.cnc.settings.SettingItem

class SettingAdapter(
    private val list: List<SettingItem>,
    private val onClick: (SettingItem) -> Unit
) : RecyclerView.Adapter<SettingAdapter.ViewHolder>() {

    inner class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val icon: ImageView = view.findViewById(R.id.imgIcon)
        val title: TextView = view.findViewById(R.id.txtTitle)
        val desc: TextView = view.findViewById(R.id.txtDesc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_setting, parent, false)
        return ViewHolder(view)
    }

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = list[position]

        holder.icon.setImageResource(item.icon)
        holder.title.text = item.title
        holder.desc.text = item.description

        holder.itemView.setOnClickListener {
            onClick(item)
        }
    }
}