package com.example.myapplication.core.session

data class Schedule(
    val scheduleId: Int,
    val taskId: Int,
    val taskName: String,
    val status: String,
    val scheduledAt: String
)