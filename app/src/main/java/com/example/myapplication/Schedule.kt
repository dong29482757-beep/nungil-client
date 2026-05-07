package com.example.myapplication

data class Schedule(
    val id: Int,
    val title: String,
    val taskDescription: String,
    val triggerTimeMillis: Long
)
