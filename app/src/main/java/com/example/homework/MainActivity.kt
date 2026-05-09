package com.example.homework

import androidx.activity.ComponentActivity
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.homework.data.loadTasks
import com.example.homework.data.saveTasks
import com.example.homework.model.StudyTask
import com.example.homework.network.fetchAiRecommendation
import com.example.homework.ui.components.PlannerCard
import com.example.homework.ui.theme.HomeworkTheme
import com.example.homework.util.getCurrentTimeContext
import com.example.homework.util.getNetworkContext
import com.example.homework.util.parseDeadline
import com.halilibo.richtext.markdown.Markdown
import com.halilibo.richtext.ui.material3.RichText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            HomeworkTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    topBar = { CenterAlignedTopAppBar(title = { Text("智能学习助手") }) }
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
    var deadlineInput by remember { mutableStateOf("无") }
    var recommendation by remember { mutableStateOf("智能建议生成中...") }
    var recommendationFailed by remember { mutableStateOf(false) }

    val tasks = remember { mutableStateListOf<StudyTask>() }
    val completedTasks = remember { mutableStateListOf<StudyTask>() }

    fun refreshRecommendation() {
        scope.launch {
            recommendationFailed = false
            recommendation = "智能建议生成中..."

            val result = fetchAiRecommendation(
                tasks = tasks.sortedBy { parseDeadline(it.deadline) },
                timeContext = getCurrentTimeContext(),
                networkContext = getNetworkContext(context)
            )

            recommendation = result
            recommendationFailed = result.startsWith("智能建议获取失败")
        }
    }

    LaunchedEffect(Unit) {
        val loaded = loadTasks(context)
        tasks.clear(); tasks.addAll(loaded.first)
        completedTasks.clear(); completedTasks.addAll(loaded.second)
    }

    LaunchedEffect(tasks.toList()) { refreshRecommendation() }

    fun persist() {
        scope.launch(Dispatchers.IO) {
            saveTasks(context, tasks, completedTasks)
        }
    }

    val completionRate = if (tasks.isEmpty() && completedTasks.isEmpty()) 0f
    else completedTasks.size / (tasks.size + completedTasks.size).toFloat()

    Column(
        modifier = modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFE7F2FF), Color(0xFFF9FBFF)))).verticalScroll(rememberScrollState()).padding(16.dp),
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
            tasks = tasks.sortedBy { parseDeadline(it.deadline) },
            completedTasks = completedTasks,
            onAddTask = {
                val minute = durationInput.toIntOrNull()?.coerceIn(5, 180) ?: 30
                if (taskInput.isNotBlank()) {
                    tasks.add(StudyTask(taskInput.trim(), minute, categoryInput.ifBlank { "未分类" }, deadlineInput.ifBlank { "无" }))
                    taskInput = ""
                    deadlineInput = "无"
                    persist()
                }
            },
            onCompleteTask = { task -> tasks.remove(task); completedTasks.add(task); persist() },
            onDeleteTask = { task -> tasks.remove(task); persist() },
            onClearCompletedTasks = { completedTasks.clear(); persist() }
        )

        Card(colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("2) 智能建议引擎", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("完成率：${(completionRate * 100).toInt()}%")
                RichText { Markdown(recommendation) }
                if (recommendationFailed) {
                    TextButton(onClick = { refreshRecommendation() }) { Text("重试") }
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun StudyAgentPreview() {
    HomeworkTheme { StudyAgentApp() }
}
