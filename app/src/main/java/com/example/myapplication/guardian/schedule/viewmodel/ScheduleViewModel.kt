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

    fun loadSchedules() {
        repository.getSchedules { result ->
            when (result) {
                is ApiResult.Success -> _schedules.postValue(result.data)
                is ApiResult.Error -> _error.postValue(result.message)
            }
        }
    }
}