package com.example.myapplication.guardian.schedule.ui

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.Session
import com.example.myapplication.core.session.Schedule
import com.google.android.material.floatingactionbutton.FloatingActionButton
import org.json.JSONObject

class ScheduleFragment : Fragment() {

    private val scheduleList = mutableListOf<Schedule>()
    private lateinit var adapter: ScheduleAdapter

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View =
        inflater.inflate(R.layout.fragment_schedule, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val rv = view.findViewById<RecyclerView>(R.id.rvSchedule)
        adapter = ScheduleAdapter(scheduleList)
        rv.layoutManager = LinearLayoutManager(context)
        rv.adapter = adapter

        view.findViewById<FloatingActionButton>(R.id.fabAdd).setOnClickListener {
            startActivity(Intent(context, ScheduleAddActivity::class.java))
        }

        loadSchedules()
    }

    override fun onResume() {
        super.onResume()
        loadSchedules()
    }

    private fun loadSchedules() {
        ApiClient.get("/schedule/${Session.guardianId}/${Session.userIdx}") { response ->
            if (response == null) {
                activity?.runOnUiThread {
                    Toast.makeText(context, "일정을 불러오지 못했어요.", Toast.LENGTH_SHORT).show()
                }
                return@get
            }
            val json = JSONObject(response)
            val arr = json.getJSONArray("schedules")
            scheduleList.clear()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                scheduleList.add(
                    Schedule(
                        scheduleId = obj.getInt("scheduleId"),
                        taskId = obj.getInt("taskId"),
                        taskName = obj.getString("taskName"),
                        status = obj.getString("status"),
                        scheduledAt = obj.getString("scheduledAt")
                    )
                )
            }
            activity?.runOnUiThread { adapter.notifyDataSetChanged() }
        }
    }
}