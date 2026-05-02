package com.example.myapplication.guardian.schedule.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.example.myapplication.R
import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.Session
import com.example.myapplication.core.session.Task
import org.json.JSONObject
import java.util.Calendar

// SC-001, SC-002, SC-003 통합
class ScheduleAddActivity : AppCompatActivity() {

    private val taskList = mutableListOf<Task>()
    private var selectedTaskId = -1
    private var selectedDate = ""
    private var selectedTime = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_schedule_add)

        val spinnerTask = findViewById<Spinner>(R.id.spinnerTask)
        val tvDate = findViewById<TextView>(R.id.tvDate)
        val tvTime = findViewById<TextView>(R.id.tvTime)
        val btnSave = findViewById<Button>(R.id.btnSave)

        // SC-002: 화이트리스트에서 과업 불러오기
        ApiClient.get("/whitelist/${Session.guardianId}/${Session.userIdx}") { response ->
            if (response == null) return@get
            val arr = JSONObject(response).getJSONArray("tasks")
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                taskList.add(Task(obj.getInt("taskId"), obj.getString("taskName")))
            }
            runOnUiThread {
                val names = taskList.map { it.taskName }
                spinnerTask.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, names)
                    .also { it.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item) }
                spinnerTask.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
                    override fun onItemSelected(p: AdapterView<*>?, v: View?, pos: Int, id: Long) {
                        selectedTaskId = taskList[pos].taskId
                    }
                    override fun onNothingSelected(p: AdapterView<*>?) {}
                }
            }
        }

        // SC-003: 날짜 선택
        tvDate.setOnClickListener {
            val cal = Calendar.getInstance()
            DatePickerDialog(this, { _, y, m, d ->
                selectedDate = "%04d-%02d-%02d".format(y, m + 1, d)
                tvDate.text = selectedDate
            }, cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.get(Calendar.DAY_OF_MONTH)).show()
        }

        // SC-003: 시간 선택
        tvTime.setOnClickListener {
            val cal = Calendar.getInstance()
            TimePickerDialog(this, { _, h, min ->
                selectedTime = "%02d:%02d".format(h, min)
                tvTime.text = selectedTime
            }, cal.get(Calendar.HOUR_OF_DAY), cal.get(Calendar.MINUTE), true).show()
        }

        // SC-001: 일정 등록
        btnSave.setOnClickListener {
            if (selectedTaskId == -1 || selectedDate.isEmpty() || selectedTime.isEmpty()) {
                Toast.makeText(this, "과업, 날짜, 시간을 모두 선택해주세요.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val body = JSONObject().apply {
                put("guardianId", Session.guardianId)
                put("idx", Session.userIdx)
                put("taskId", selectedTaskId)
                put("date", selectedDate)
                put("time", selectedTime)
            }.toString()

            ApiClient.post("/schedule", body) { response ->
                if (response == null) {
                    runOnUiThread { Toast.makeText(this, "등록 실패. 다시 시도해주세요.", Toast.LENGTH_SHORT).show() }
                    return@post
                }
                val json = JSONObject(response)
                val conflict = json.optBoolean("conflict", false)
                runOnUiThread {
                    if (conflict) {
                        Toast.makeText(this, "⚠️ 같은 시간에 이미 일정이 있어요!", Toast.LENGTH_LONG).show()
                    } else {
                        Toast.makeText(this, "일정이 등록됐어요!", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
            }
        }
    }
}
