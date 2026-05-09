package com.example.homework.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

fun parseDeadline(deadline: String): Long {
    if (deadline.isBlank() || deadline == "无") {
        return Long.MAX_VALUE
    }

    return runCatching {
        val date = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(deadline)
            ?: return Long.MAX_VALUE

        Calendar.getInstance().apply {
            time = date
            set(Calendar.HOUR_OF_DAY, 23)
            set(Calendar.MINUTE, 59)
            set(Calendar.SECOND, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }.getOrDefault(Long.MAX_VALUE)
}

fun isDeadlineNear(deadline: String): Boolean {
    val deadlineTime = parseDeadline(deadline)
    if (deadlineTime == Long.MAX_VALUE) return false

    val now = System.currentTimeMillis()
    val remain = deadlineTime - now

    return remain in 0..(24 * 60 * 60 * 1000L)
}

fun buildDdlOptions(): List<String> {
    val formatter = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    val calendar = Calendar.getInstance()

    return buildList {
        add("无")
        repeat(30) {
            add(formatter.format(calendar.time))
            calendar.add(Calendar.DAY_OF_YEAR, 1)
        }
    }
}

fun getCurrentTimeContext(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "上午"
        in 12..17 -> "下午"
        in 18..22 -> "晚上"
        else -> "深夜"
    }
}
