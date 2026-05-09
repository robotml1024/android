package com.example.homework.data

import android.content.Context
import com.example.homework.model.StudyTask
import org.json.JSONArray
import org.json.JSONObject

private const val PREFS_NAME = "study_agent_prefs"
private const val KEY_TODO_TASKS = "todo_tasks"
private const val KEY_DONE_TASKS = "done_tasks"

fun saveTasks(context: Context, todo: List<StudyTask>, done: List<StudyTask>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_TODO_TASKS, tasksToJson(todo).toString())
        .putString(KEY_DONE_TASKS, tasksToJson(done).toString())
        .apply()
}

fun loadTasks(context: Context): Pair<List<StudyTask>, List<StudyTask>> {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    val todoJson = prefs.getString(KEY_TODO_TASKS, null)
    val doneJson = prefs.getString(KEY_DONE_TASKS, null)
    return Pair(jsonToTasks(todoJson), jsonToTasks(doneJson))
}

private fun tasksToJson(tasks: List<StudyTask>): JSONArray {
    val arr = JSONArray()
    tasks.forEach { task ->
        arr.put(
            JSONObject()
                .put("title", task.title)
                .put("estimatedMinutes", task.estimatedMinutes)
                .put("category", task.category)
                .put("deadline", task.deadline)
        )
    }
    return arr
}

private fun jsonToTasks(raw: String?): List<StudyTask> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            StudyTask(
                title = obj.optString("title"),
                estimatedMinutes = obj.optInt("estimatedMinutes", 30),
                category = obj.optString("category", "未分类"),
                deadline = obj.optString("deadline", "无")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}
