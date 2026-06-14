package com.rd.applauncher.model

data class TimeSlot(
    val startHour: Int,
    val startMinute: Int
)

data class Schedule(
    val packageName: String,
    val appName: String,
    val slot1: TimeSlot,
    val slot2: TimeSlot,
    val enabled: Boolean
)
