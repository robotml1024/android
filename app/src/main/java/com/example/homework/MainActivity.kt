package com.example.homework

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

data class StudyTask(
    val title: String,
    val estimatedMinutes: Int,
    val energyNeed: Float
)

private const val PREFS_NAME = "study_agent_prefs"
private const val KEY_TODO_TASKS = "todo_tasks"
private const val KEY_DONE_TASKS = "done_tasks"
private const val LLM_API_URL = "" // TODO: 替换为你的大模型接口
private const val LLM_API_KEY = "" // TODO: 替换为你的 API Key

private val DEFAULT_TASKS = listOf(
    StudyTask("复习Kotlin协程", 40, 0.6f),
    StudyTask("整理移动互联网知识点", 30, 0.4f),
    StudyTask("课程大作业功能测试", 25, 0.5f)
)

@OptIn(ExperimentalMaterial3Api::class)
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeworkTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { CenterAlignedTopAppBar(title = { Text("移动智能学习助手") }) }
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

    var energy by remember { mutableFloatStateOf(70f) }
    var taskInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("30") }
    var recommendation by remember { mutableStateOf("正在生成建议...") }

    val tasks = remember { mutableStateListOf<StudyTask>() }
    val completedTasks = remember { mutableStateListOf<StudyTask>() }

    LaunchedEffect(Unit) {
        val loaded = loadTasks(context)
        tasks.clear()
        tasks.addAll(loaded.first)
        completedTasks.clear()
        completedTasks.addAll(loaded.second)
        if (tasks.isEmpty() && completedTasks.isEmpty()) {
            tasks.addAll(DEFAULT_TASKS)
        }
    }

    LaunchedEffect(tasks.size, energy.toInt()) {
        recommendation = fetchAiRecommendation(tasks, energy)
    }

    fun persist() {
        scope.launch(Dispatchers.IO) {
            saveTasks(context, tasks, completedTasks)
        }
    }

    val completionRate = if (tasks.isEmpty() && completedTasks.isEmpty()) 0f
    else completedTasks.size / (tasks.size + completedTasks.size).toFloat()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(listOf(Color(0xFFE7F2FF), Color(0xFFF9FBFF))))
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        StatusCard(energy = energy, onEnergyChange = { energy = it })

        PlannerCard(
            taskInput = taskInput,
            onTaskInputChange = { taskInput = it },
            durationInput = durationInput,
            onDurationInputChange = { durationInput = it },
            tasks = tasks,
            completedTasks = completedTasks,
            onAddTask = {
                val minute = durationInput.toIntOrNull()?.coerceIn(5, 180) ?: 30
                if (taskInput.isNotBlank()) {
                    val baseNeed = (minute / 180f).coerceIn(0.2f, 1f)
                    val adjustedNeed = adjustEnergyNeedByCurrentEnergy(baseNeed, energy)
                    tasks.add(StudyTask(taskInput.trim(), minute, adjustedNeed))
                    taskInput = ""
                    persist()
                }
            },
            onCompleteTask = { task ->
                tasks.remove(task)
                completedTasks.add(task)
                persist()
            },
            onDeleteTask = { task ->
                tasks.remove(task)
                persist()
            },
            onClearCompletedTasks = {
                completedTasks.clear()
                persist()
            }
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("3) 智能建议引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("完成率：${(completionRate * 100).toInt()}%")
                Text(recommendation)
            }
        }
    }
}

@Composable
private fun StatusCard(energy: Float, onEnergyChange: (Float) -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text("1) 当前状态采集", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("当前精力值：${energy.toInt()}%")
            Slider(value = energy, onValueChange = onEnergyChange, valueRange = 0f..100f)
            Spacer(Modifier.height(8.dp))
            Text("状态动作建议：${generateStatusAction(energy)}")
        }
    }
}

@Composable
private fun PlannerCard(
    taskInput: String,
    onTaskInputChange: (String) -> Unit,
    durationInput: String,
    onDurationInputChange: (String) -> Unit,
    tasks: List<StudyTask>,
    completedTasks: List<StudyTask>,
    onAddTask: () -> Unit,
    onCompleteTask: (StudyTask) -> Unit,
    onDeleteTask: (StudyTask) -> Unit,
    onClearCompletedTasks: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text("2) AI任务规划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = taskInput, onValueChange = onTaskInputChange, label = { Text("新增学习任务") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = durationInput,
                onValueChange = { onDurationInputChange(it.filter { ch -> ch.isDigit() }) },
                label = { Text("预计时长(分钟)") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddTask) { Text("加入计划") }

            TaskSection(
                title = "待完成任务",
                tasks = tasks,
                maxVisibleItems = 3,
                cardColor = Color(0xFFF5F7FF),
                statusText = null,
                onPrimaryAction = onCompleteTask,
                primaryActionText = "完成",
                onSecondaryAction = onDeleteTask,
                secondaryActionText = "撤销"
            )

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("已完成任务", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (completedTasks.isNotEmpty()) {
                    TextButton(onClick = onClearCompletedTasks) { Text("清空已完成") }
                }
            }

            TaskSection(
                title = "",
                tasks = completedTasks,
                maxVisibleItems = 3,
                cardColor = Color(0xFFDFF5E4),
                statusText = "已完成",
                onPrimaryAction = null,
                primaryActionText = null,
                onSecondaryAction = null,
                secondaryActionText = null
            )
        }
    }
}

@Composable
private fun TaskSection(
    title: String,
    tasks: List<StudyTask>,
    maxVisibleItems: Int,
    cardColor: Color,
    statusText: String?,
    onPrimaryAction: ((StudyTask) -> Unit)?,
    primaryActionText: String?,
    onSecondaryAction: ((StudyTask) -> Unit)?,
    secondaryActionText: String?
) {
    Spacer(Modifier.height(12.dp))
    if (title.isNotBlank()) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
    }
    val maxHeight = (maxVisibleItems * 72).dp
    LazyColumn(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.heightIn(max = maxHeight)
    ) {
        items(tasks) { task ->
            TaskItemCard(
                task = task,
                cardColor = cardColor,
                statusText = statusText,
                onPrimaryAction = onPrimaryAction,
                primaryActionText = primaryActionText,
                onSecondaryAction = onSecondaryAction,
                secondaryActionText = secondaryActionText
            )
        }
    }
}

@Composable
private fun TaskItemCard(
    task: StudyTask,
    cardColor: Color,
    statusText: String?,
    onPrimaryAction: ((StudyTask) -> Unit)?,
    primaryActionText: String?,
    onSecondaryAction: ((StudyTask) -> Unit)?,
    secondaryActionText: String?
) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text("${task.estimatedMinutes} 分钟 · 任务强度 ${"%.1f".format(task.energyNeed * 10)}")
            }
            when {
                statusText != null -> Text(statusText, color = Color(0xFF2E7D32))
                onPrimaryAction != null && primaryActionText != null && onSecondaryAction != null && secondaryActionText != null -> {
                    Row {
                        TextButton(onClick = { onPrimaryAction(task) }) { Text(primaryActionText) }
                        TextButton(onClick = { onSecondaryAction(task) }) { Text(secondaryActionText) }
                    }
                }
            }
        }
    }
}

private fun generateStatusAction(energy: Float): String {
    return when {
        energy < 30 -> "先安排 10~15 分钟低强度任务，避免硬扛。"
        energy > 75 -> "状态很好，优先推进高强度任务。"
        else -> "先完成一个 25~30 分钟任务，然后短休息。"
    }
}

private fun adjustEnergyNeedByCurrentEnergy(baseNeed: Float, energy: Float): Float {
    return when {
        energy > 75 -> (baseNeed + 0.1f).coerceAtMost(1f)
        energy < 35 -> (baseNeed - 0.1f).coerceAtLeast(0.2f)
        else -> baseNeed
    }
}

private suspend fun fetchAiRecommendation(tasks: List<StudyTask>, energy: Float): String {
    if (tasks.isEmpty()) return "当前没有待完成任务，建议新增一个可在 30 分钟内完成的小目标。"
    if (LLM_API_URL.isBlank()) {
        return "（待接入大模型 API）当前有 ${tasks.size} 个待完成任务，精力值 ${energy.toInt()}%，建议先完成最短任务：${tasks.minByOrNull { it.estimatedMinutes }?.title ?: "当前任务"}。"
    }

    return runCatching {
        val connection = URL(LLM_API_URL).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        if (LLM_API_KEY.isNotBlank()) {
            connection.setRequestProperty("Authorization", "Bearer $LLM_API_KEY")
        }
        connection.doOutput = true
        connection.connectTimeout = 15000
        connection.readTimeout = 15000

        val systemPrompt = "你是一名学习规划助手。请基于用户待完成任务与当前精力值，输出中文建议：先做哪项任务、原因、执行时长建议（简短明确）。"
        val userPayload = JSONObject()
            .put("energy", energy.toInt())
            .put("todoTasks", JSONArray().apply {
                tasks.forEach {
                    put(JSONObject().put("title", it.title).put("estimatedMinutes", it.estimatedMinutes).put("energyNeed", it.energyNeed))
                }
            })

        val payload = JSONObject()
            .put("system", systemPrompt)
            .put("messages", JSONArray().apply {
                put(JSONObject().put("role", "system").put("content", systemPrompt))
                put(JSONObject().put("role", "user").put("content", userPayload.toString()))
            })
            .put("energy", energy.toInt())
            .put("todoTasks", userPayload.getJSONArray("todoTasks"))

        OutputStreamWriter(connection.outputStream).use { it.write(payload.toString()) }

        val statusCode = connection.responseCode
        val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
        val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (statusCode !in 200..299) {
            throw IllegalStateException("HTTP $statusCode: ${responseText.ifBlank { "empty error body" }}")
        }

        parseRecommendationFromResponse(responseText)
    }.getOrElse { error ->
        "智能建议获取失败：${error.message ?: "未知错误"}"
    }
}

private fun parseRecommendationFromResponse(responseText: String): String {
    if (responseText.isBlank()) return "模型返回为空，请检查接口输出格式。"

    val json = runCatching { JSONObject(responseText) }.getOrNull()
        ?: return "模型返回不是合法 JSON：${responseText.take(120)}"

    json.optString("recommendation").takeIf { it.isNotBlank() }?.let { return it }
    json.optString("output").takeIf { it.isNotBlank() }?.let { return it }

    val choices = json.optJSONArray("choices")
    if (choices != null && choices.length() > 0) {
        val first = choices.optJSONObject(0)
        first?.optString("text")?.takeIf { it.isNotBlank() }?.let { return it }
        first?.optJSONObject("message")?.optString("content")?.takeIf { it.isNotBlank() }?.let { return it }
    }

    return "模型返回缺少 recommendation 字段，请按约定返回。"
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
        arr.put(JSONObject().put("title", task.title).put("estimatedMinutes", task.estimatedMinutes).put("energyNeed", task.energyNeed.toDouble()))
    }
    return arr
}

private fun jsonToTasks(raw: String?): List<StudyTask> {
    if (raw.isNullOrBlank()) return emptyList()
    return try {
        val arr = JSONArray(raw)
        List(arr.length()) { index ->
            val obj = arr.getJSONObject(index)
            StudyTask(obj.optString("title"), obj.optInt("estimatedMinutes", 30), obj.optDouble("energyNeed", 0.4).toFloat())
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
