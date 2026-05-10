package com.example.myapplication.guardian.schedule.viewmodel

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.myapplication.core.network.ApiResult
import com.example.myapplication.guardian.schedule.data.ScheduleRepository
import com.example.myapplication.model.Schedule

class ScheduleViewModel : ViewModel() {

    private val repository = ScheduleRepository()

    private val _schedules = MutableLiveData<List<Schedule>>()
    val schedules: LiveData<List<Schedule>> = _schedules

    private val _error = MutableLiveData<String>()
    val error: LiveData<String> = _error

    private val _deleteSuccess = MutableLiveData<Boolean>()
    val deleteSuccess: LiveData<Boolean> = _deleteSuccess

    fun loadSchedules() {
        repository.getSchedules { result ->
            when (result) {
                is ApiResult.Success -> _schedules.postValue(result.data)
                is ApiResult.Error   -> _error.postValue(result.message)
            }
        }
    }

    fun deleteSchedule(scheduleId: Int) {
        repository.deleteSchedule(scheduleId) { result ->
            when (result) {
                is ApiResult.Success -> {
                    _deleteSuccess.postValue(true)
                    loadSchedules() // 삭제 후 목록 갱신
                }
                is ApiResult.Error -> {
                    _error.postValue("삭제 실패: ${result.message}")
                    _deleteSuccess.postValue(false)
                }
            }
        }
    }
}
