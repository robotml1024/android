package com.example.homework

import android.Manifest
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
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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

import kotlinx.coroutines.withContext
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import androidx.annotation.RequiresPermission
import java.util.Calendar

data class StudyTask(
    val title: String,
    val estimatedMinutes: Int,
    val category: String,
    val deadline: String
)

private const val PREFS_NAME = "study_agent_prefs"
private const val KEY_TODO_TASKS = "todo_tasks"
private const val KEY_DONE_TASKS = "done_tasks"
private const val LLM_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
private const val LLM_API_KEY = "sk-ceb7fd17109949bf9e6cc8c39e4b8d7d"
private const val LLM_MODEL_NAME = "qwen3.5-flash-2026-02-23"

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

    var taskInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("30") }
    var categoryInput by remember { mutableStateOf("编程") }
    var deadlineInput by remember { mutableStateOf("") }
    var recommendation by remember { mutableStateOf("智能建议生成中...") }
    var recommendationFailed by remember { mutableStateOf(false) }

    val tasks = remember { mutableStateListOf<StudyTask>() }
    val completedTasks = remember { mutableStateListOf<StudyTask>() }

    fun refreshRecommendation() {
        scope.launch @androidx.annotation.RequiresPermission(android.Manifest.permission.ACCESS_NETWORK_STATE) {
            recommendationFailed = false
            recommendation = "智能建议生成中..."

            val timeContext = getCurrentTimeContext()
            val networkContext = getNetworkContext(context)

            val result = fetchAiRecommendation(
                tasks = tasks,
                timeContext = timeContext,
                networkContext = networkContext
            )

            recommendation = result
            recommendationFailed = result.startsWith("智能建议获取失败")
        }
    }

    LaunchedEffect(Unit) {
        val loaded = loadTasks(context)
        tasks.clear()
        tasks.addAll(loaded.first)
        completedTasks.clear()
        completedTasks.addAll(loaded.second)
    }

    LaunchedEffect(tasks.toList()) {
        refreshRecommendation()
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
        PlannerCard(
            taskInput = taskInput,
            onTaskInputChange = { taskInput = it },
            durationInput = durationInput,
            onDurationInputChange = { durationInput = it },
            categoryInput = categoryInput,
            onCategoryInputChange = { categoryInput = it },
            deadlineInput = deadlineInput,
            onDeadlineInputChange = { deadlineInput = it },
            tasks = tasks,
            completedTasks = completedTasks,
            onAddTask = {
                val minute = durationInput.toIntOrNull()?.coerceIn(5, 180) ?: 30
                if (taskInput.isNotBlank()) {
                    tasks.add(
                        StudyTask(
                            title = taskInput.trim(),
                            estimatedMinutes = minute,
                            category = categoryInput.ifBlank { "未分类" },
                            deadline = deadlineInput.ifBlank { "无" }
                        )
                    )
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
            Column(
                Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    "2) 智能建议引擎",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )

                Text("完成率：${(completionRate * 100).toInt()}%")

                RichText {
                    Markdown(recommendation)
                }

                if (recommendationFailed) {
                    TextButton(
                        onClick = { refreshRecommendation() }
                    ) {
                        Text("重试")
                    }
                }
            }
        }
    }
}

@Composable
private fun PlannerCard(
    taskInput: String,
    onTaskInputChange: (String) -> Unit,
    durationInput: String,
    onDurationInputChange: (String) -> Unit,
    categoryInput: String,
    onCategoryInputChange: (String) -> Unit,
    deadlineInput: String,
    onDeadlineInputChange: (String) -> Unit,
    tasks: List<StudyTask>,
    completedTasks: List<StudyTask>,
    onAddTask: () -> Unit,
    onCompleteTask: (StudyTask) -> Unit,
    onDeleteTask: (StudyTask) -> Unit,
    onClearCompletedTasks: () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(14.dp)) {
            Text("1) AI任务规划", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
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
            OutlinedTextField(
                value = categoryInput,
                onValueChange = onCategoryInputChange,
                label = { Text("任务类型（如 编程 / 阅读 / 背诵）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = deadlineInput,
                onValueChange = onDeadlineInputChange,
                label = { Text("截止时间（如 今天 / 明晚 / 2026-05-10）") },
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
                Text("${task.estimatedMinutes} 分钟")
                Text("类型：${task.category}")
                Text("DDL：${task.deadline}")
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

private suspend fun fetchAiRecommendation(
    tasks: List<StudyTask>,
    timeContext: String,
    networkContext: String
): String {
    if (tasks.isEmpty()) {
        return "当前没有待完成任务，建议新增一个可在 30 分钟内完成的小目标。"
    }

    return withContext(Dispatchers.IO) {
        runCatching {
            val connection = URL(LLM_API_URL).openConnection() as HttpURLConnection

            connection.requestMethod = "POST"
            connection.setRequestProperty("Content-Type", "application/json; charset=utf-8")
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Authorization", "Bearer $LLM_API_KEY")
            connection.doOutput = true
            connection.connectTimeout = 20000
            connection.readTimeout = 25000

            val systemPrompt =
                """
                你是一名学习规划助手。
                请结合当前时间、网络状态、任务时长、难度、类型和截止时间，
                给出最合理的学习建议。
                请使用 Markdown 表格输出，内容简洁明确。
                """.trimIndent()

            val userPayload = JSONObject()
                .put("timeContext", timeContext)
                .put("networkContext", networkContext)
                .put("todoTasks", JSONArray().apply {
                    tasks.forEach {
                        put(
                            JSONObject()
                                .put("title", it.title)
                                .put("estimatedMinutes", it.estimatedMinutes)
                                .put("category", it.category)
                                .put("deadline", it.deadline)
                        )
                    }
                })

            val payload = JSONObject()
                .put("model", LLM_MODEL_NAME)
                .put("messages", JSONArray().apply {
                    put(JSONObject().put("role", "system").put("content", systemPrompt))
                    put(JSONObject().put("role", "user").put("content", userPayload.toString()))
                })

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use {
                it.write(payload.toString())
            }

            val statusCode = connection.responseCode
            val stream =
                if (statusCode in 200..299) connection.inputStream
                else connection.errorStream

            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException(
                    "HTTP $statusCode: ${responseText.ifBlank { "empty error body" }}"
                )
            }

            parseRecommendationFromResponse(responseText)
        }.getOrElse { error ->
            "智能建议获取失败：${error.message ?: error.javaClass.simpleName}"
        }
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

private fun getCurrentTimeContext(): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)

    return when (hour) {
        in 5..11 -> "上午"
        in 12..17 -> "下午"
        in 18..22 -> "晚上"
        else -> "深夜"
    }
}

@RequiresPermission(Manifest.permission.ACCESS_NETWORK_STATE)
private fun getNetworkContext(context: Context): String {
    val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

    val network = connectivityManager.activeNetwork ?: return "无网络"

    val capabilities =
        connectivityManager.getNetworkCapabilities(network) ?: return "无网络"

    return when {
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) -> "Wi-Fi"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) -> "蜂窝网络"
        capabilities.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) -> "有线网络"
        else -> "未知网络"
    }
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

@Preview(showBackground = true)
@Composable
fun StudyAgentPreview() {
    HomeworkTheme {
        StudyAgentApp()
    }
}