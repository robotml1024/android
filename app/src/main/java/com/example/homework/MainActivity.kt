package com.example.homework

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.homework.ui.theme.HomeworkTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

enum class Mood(val label: String, val emoji: String) {
    FOCUSED("专注", "🧠"),
    TIRED("疲惫", "😪"),
    STRESSED("压力大", "😵"),
    HAPPY("心情好", "😄")
}

data class StudyTask(
    val title: String,
    val estimatedMinutes: Int,
    val energyNeed: Float
)

private const val PREFS_NAME = "study_agent_prefs"
private const val KEY_TODO_TASKS = "todo_tasks"
private const val KEY_DONE_TASKS = "done_tasks"

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeworkTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = {
                        CenterAlignedTopAppBar(title = { Text("移动智能学习助手") })
                    }
                ) { innerPadding ->
                    StudyAgentApp(Modifier.padding(innerPadding))
                }
            }
        }
    }
}

@Composable
fun StudyAgentApp(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedMood by remember { mutableStateOf(Mood.FOCUSED) }
    var energy by remember { mutableFloatStateOf(70f) }
    var taskInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("30") }
    val tasks = remember { mutableStateListOf<StudyTask>() }
    val completedTasks = remember { mutableStateListOf<StudyTask>() }

    LaunchedEffect(Unit) {
        val loaded = loadTasks(context)
        tasks.clear()
        tasks.addAll(loaded.first)
        completedTasks.clear()
        completedTasks.addAll(loaded.second)
        if (tasks.isEmpty() && completedTasks.isEmpty()) {
            tasks.addAll(
                listOf(
                    StudyTask("复习Kotlin协程", 40, 0.6f),
                    StudyTask("整理移动互联网知识点", 30, 0.4f),
                    StudyTask("课程大作业功能测试", 25, 0.5f)
                )
            )
        }
    }

    val completionRate = if (tasks.isEmpty() && completedTasks.isEmpty()) 0f
    else completedTasks.size / (tasks.size + completedTasks.size).toFloat()
    val recommendation = generateRecommendation(selectedMood, energy, tasks)
    val statusAction = generateStatusAction(selectedMood, energy)

    fun persist() {
        scope.launch(Dispatchers.IO) {
            saveTasks(context, tasks, completedTasks)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE7F2FF), Color(0xFFF9FBFF))))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp)) {
                Text("1) 当前状态采集", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                Text("今天你的学习心情？")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Mood.entries.forEach { mood ->
                        FilterChip(
                            selected = selectedMood == mood,
                            onClick = { selectedMood = mood },
                            label = { Text("${mood.emoji} ${mood.label}") }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("当前精力值：${energy.toInt()}%")
                Slider(value = energy, onValueChange = { energy = it }, valueRange = 0f..100f)
                Spacer(Modifier.height(8.dp))
                Text("状态动作建议：$statusAction")
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp)) {
                Text("2) AI任务规划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = taskInput,
                    onValueChange = { taskInput = it },
                    label = { Text("新增学习任务") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = durationInput,
                    onValueChange = { durationInput = it.filter { ch -> ch.isDigit() } },
                    label = { Text("预计时长(分钟)") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {
                        val minute = durationInput.toIntOrNull()?.coerceIn(5, 180) ?: 30
                        if (taskInput.isNotBlank()) {
                            val baseNeed = (minute / 180f).coerceIn(0.2f, 1f)
                            val adjustedNeed = when (selectedMood) {
                                Mood.FOCUSED, Mood.HAPPY -> (baseNeed + 0.1f).coerceAtMost(1f)
                                Mood.TIRED, Mood.STRESSED -> (baseNeed - 0.1f).coerceAtLeast(0.2f)
                            }
                            tasks.add(StudyTask(taskInput.trim(), minute, adjustedNeed))
                            taskInput = ""
                            persist()
                        }
                    }
                ) { Text("加入计划") }

                Spacer(Modifier.height(12.dp))
                Text("待完成任务", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(180.dp)) {
                    items(tasks) { task ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F7FF))
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text("${task.estimatedMinutes} 分钟 · 任务强度 ${"%.1f".format(task.energyNeed * 10)}")
                                }
                                Row {
                                    TextButton(onClick = {
                                        tasks.remove(task)
                                        completedTasks.add(task)
                                        persist()
                                    }) { Text("完成") }
                                    TextButton(onClick = {
                                        tasks.remove(task)
                                        persist()
                                    }) { Text("撤销") }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("已完成任务", fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(6.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(140.dp)) {
                    items(completedTasks) { task ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFDFF5E4))
                        ) {
                            Row(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text("${task.estimatedMinutes} 分钟 · 任务强度 ${"%.1f".format(task.energyNeed * 10)}")
                                }
                                Text("已完成", color = Color(0xFF2E7D32))
                            }
                        }
                    }
                }
            }
        }

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3) 智能建议引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("完成率：${(completionRate * 100).toInt()}%")
                Text(recommendation)
            }
        }
    }
}

private fun generateStatusAction(mood: Mood, energy: Float): String {
    return when {
        energy < 30 -> "先安排 10~15 分钟低强度任务，避免硬扛。"
        mood == Mood.STRESSED -> "先深呼吸 2 分钟，再开始一个最短任务建立节奏。"
        mood == Mood.FOCUSED && energy > 75 -> "状态很好，优先推进高强度任务。"
        else -> "先完成一个 25~30 分钟任务，然后短休息。"
    }
}

fun generateRecommendation(mood: Mood, energy: Float, tasks: List<StudyTask>): String {
    if (tasks.isEmpty()) return "当前没有待完成任务，建议新增一个可在 30 分钟内完成的小目标。"
    val nextTask = when {
        energy < 35 -> tasks.minByOrNull { it.energyNeed }
        energy > 75 -> tasks.maxByOrNull { it.energyNeed }
        else -> tasks.minByOrNull { it.estimatedMinutes }
    } ?: tasks.first()

    val moodAdvice = when (mood) {
        Mood.FOCUSED -> "你处于高专注状态，建议优先处理困难任务。"
        Mood.TIRED -> "当前较疲惫，建议先做 15 分钟轻任务热身。"
        Mood.STRESSED -> "压力偏高，建议先做呼吸放松，再采用番茄钟节奏。"
        Mood.HAPPY -> "心情积极，适合推进创造性任务并记录灵感。"
    }

    return "$moodAdvice 推荐下一项：${nextTask.title}（约 ${nextTask.estimatedMinutes} 分钟）。"
}

private fun saveTasks(context: Context, todo: List<StudyTask>, done: List<StudyTask>) {
    val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    prefs.edit()
        .putString(KEY_TODO_TASKS, tasksToJson(todo).toString())
        .putString(KEY_DONE_TASKS, tasksToJson(done).toString())
        .apply()
}

private fun loadTasks(context: Context): Pair<List<StudyTask>, List<StudyTask>> {
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
                .put("energyNeed", task.energyNeed.toDouble())
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
                energyNeed = obj.optDouble("energyNeed", 0.4).toFloat()
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

@Preview(showBackground = true)
@Composable
fun StudyAgentPreview() {
    HomeworkTheme {
        StudyAgentApp()
    }
}
