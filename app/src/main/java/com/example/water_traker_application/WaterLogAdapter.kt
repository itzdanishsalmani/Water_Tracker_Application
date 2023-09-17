package com.example.water_traker_application

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class WaterLogAdapter(private val waterLogs: MutableList<WaterLog>) :
    RecyclerView.Adapter<WaterLogAdapter.WaterLogViewHolder>() {

    fun clearData() {
        waterLogs.clear()
        notifyDataSetChanged()
    }

    inner class WaterLogViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val timeTextView: TextView = itemView.findViewById(R.id.timeTextView)
        val amountTextView: TextView = itemView.findViewById(R.id.amountTextView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): WaterLogViewHolder {
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_water_log, parent, false)
        return WaterLogViewHolder(itemView)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: WaterLogViewHolder, position: Int) {
        val log = waterLogs[position]
        holder.timeTextView.text = log.time
        holder.amountTextView.text = "${log.amount.toInt()} ml" // Convert to Int
    }

    override fun getItemCount(): Int {
        return waterLogs.size
    }
}
