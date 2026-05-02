package com.example.myapplication.core.session

data class Task(
    val taskId: Int,
    val taskName: String
)

data class TaskStep(
    val order: Int,
    val description: String
)
