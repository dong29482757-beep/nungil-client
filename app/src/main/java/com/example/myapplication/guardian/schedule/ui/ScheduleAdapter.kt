package com.example.myapplication.guardian.schedule.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.core.session.Schedule

class ScheduleAdapter(
    private val list: MutableList<Schedule>
) : RecyclerView.Adapter<ScheduleAdapter.ViewHolder>() {

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val tvTaskName: TextView = view.findViewById(R.id.tvTaskName)
        val tvTime: TextView = view.findViewById(R.id.tvTime)
        val tvStatus: TextView = view.findViewById(R.id.tvStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        ViewHolder(LayoutInflater.from(parent.context).inflate(R.layout.item_schedule, parent, false))

    override fun getItemCount() = list.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val s = list[position]
        holder.tvTaskName.text = s.taskName
        holder.tvTime.text = s.scheduledAt.replace("T", " ").substring(0, 16)
        holder.tvStatus.text = when (s.status) {
            "pending" -> "예정"
            "completed" -> "완료"
            "in_progress" -> "진행중"
            else -> s.status
        }
        holder.tvStatus.setBackgroundResource(
            if (s.status == "completed") R.drawable.bg_bubble_mine else R.drawable.bg_suggestion_chip
        )
    }
}
