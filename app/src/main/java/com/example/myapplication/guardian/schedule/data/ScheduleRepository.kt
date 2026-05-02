package com.example.myapplication.guardian.schedule.data

import com.example.myapplication.core.network.ApiClient
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.core.session.SessionManager
import com.example.myapplication.model.Schedule
import com.example.myapplication.model.Task
import org.json.JSONObject

class ScheduleRepository {

    fun getSchedules(onResult: (ApiResult<List<Schedule>>) -> Unit) {
        val path = "/schedule/${SessionManager.guardianId}/${SessionManager.userIdx}"

        ApiClient.get(path) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val json = JSONObject(result.data)
                        val arr = json.getJSONArray("schedules")
                        val list = mutableListOf<Schedule>()

                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                Schedule(
                                    scheduleId = obj.getInt("scheduleId"),
                                    taskId = obj.getInt("taskId"),
                                    taskName = obj.getString("taskName"),
                                    status = obj.getString("status"),
                                    scheduledAt = obj.getString("scheduledAt")
                                )
                            )
                        }

                        onResult(ApiResult.Success(list))
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("일정 데이터 파싱 실패: ${e.message}"))
                    }
                }

                is ApiResult.Error -> onResult(result)
            }
        }
    }

    fun getWhitelistTasks(onResult: (ApiResult<List<Task>>) -> Unit) {
        val path = "/whitelist/${SessionManager.guardianId}/${SessionManager.userIdx}"

        ApiClient.get(path) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val arr = JSONObject(result.data).getJSONArray("tasks")
                        val list = mutableListOf<Task>()

                        for (i in 0 until arr.length()) {
                            val obj = arr.getJSONObject(i)
                            list.add(
                                Task(
                                    taskId = obj.getInt("taskId"),
                                    taskName = obj.getString("taskName")
                                )
                            )
                        }

                        onResult(ApiResult.Success(list))
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("과업 데이터 파싱 실패: ${e.message}"))
                    }
                }

                is ApiResult.Error -> onResult(result)
            }
        }
    }

    fun addSchedule(
        taskId: Int,
        date: String,
        time: String,
        onResult: (ApiResult<Boolean>) -> Unit
    ) {
        val body = JSONObject().apply {
            put("guardianId", SessionManager.guardianId)
            put("idx", SessionManager.userIdx)
            put("taskId", taskId)
            put("date", date)
            put("time", time)
        }.toString()

        ApiClient.post("/schedule", body) { result ->
            when (result) {
                is ApiResult.Success -> {
                    try {
                        val json = JSONObject(result.data)
                        val conflict = json.optBoolean("conflict", false)
                        onResult(ApiResult.Success(conflict))
                    } catch (e: Exception) {
                        onResult(ApiResult.Error("일정 등록 응답 파싱 실패: ${e.message}"))
                    }
                }

                is ApiResult.Error -> onResult(result)
            }
        }
    }
}