package com.example.homework

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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.example.homework.ui.theme.HomeworkTheme

enum class Mood(val label: String, val emoji: String) {
    FOCUSED("专注", "🧠"),
    TIRED("疲惫", "😪"),
    STRESSED("压力大", "😵"),
    HAPPY("心情好", "😄")
}

data class StudyTask(
    val title: String,
    val estimatedMinutes: Int,
    val energyNeed: Float,
    var done: Boolean = false
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
    var selectedMood by remember { mutableStateOf(Mood.FOCUSED) }
    var energy by remember { mutableFloatStateOf(70f) }
    var taskInput by remember { mutableStateOf("") }
    var durationInput by remember { mutableStateOf("30") }
    val tasks = remember {
        mutableStateListOf(
            StudyTask("复习Kotlin协程", 40, 0.6f),
            StudyTask("整理移动互联网知识点", 30, 0.4f),
            StudyTask("课程大作业功能测试", 25, 0.5f)
        )
    }

    val completionRate = if (tasks.isEmpty()) 0f else tasks.count { it.done } / tasks.size.toFloat()
    val recommendation = generateRecommendation(selectedMood, energy, tasks)

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFFE7F2FF), Color(0xFFF9FBFF))
                )
            )
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
                            tasks.add(StudyTask(taskInput.trim(), minute, (minute / 180f).coerceAtLeast(0.2f)))
                            taskInput = ""
                        }
                    }
                ) { Text("加入计划") }

                Spacer(Modifier.height(12.dp))
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.height(220.dp)) {
                    items(tasks) { task ->
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (task.done) Color(0xFFDFF5E4) else Color(0xFFF5F7FF)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(task.title, fontWeight = FontWeight.SemiBold)
                                    Text("${task.estimatedMinutes} 分钟 · 任务强度 ${"%.1f".format(task.energyNeed * 10)}")
                                }
                                TextButton(onClick = { task.done = !task.done }) {
                                    Text(if (task.done) "撤销" else "完成")
                                }
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
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("创新点：")
                    Spacer(Modifier.width(6.dp))
                    Text("情绪+精力双因子动态规划 / 轻量级本地智能体逻辑")
                }
            }
        }
    }
}

fun generateRecommendation(mood: Mood, energy: Float, tasks: List<StudyTask>): String {
    val remaining = tasks.filterNot { it.done }
    if (remaining.isEmpty()) return "你已经完成全部任务！建议开始做阶段性复盘，并总结 3 个可复用经验。"

    val nextTask = when {
        energy < 35 -> remaining.minByOrNull { it.energyNeed }
        energy > 75 -> remaining.maxByOrNull { it.energyNeed }
        else -> remaining.minByOrNull { it.estimatedMinutes }
    } ?: remaining.first()

    val moodAdvice = when (mood) {
        Mood.FOCUSED -> "你处于高专注状态，建议优先处理困难任务。"
        Mood.TIRED -> "当前较疲惫，建议先做 15 分钟轻任务热身。"
        Mood.STRESSED -> "压力偏高，建议先做呼吸放松，再采用番茄钟节奏。"
        Mood.HAPPY -> "心情积极，适合推进创造性任务并记录灵感。"
    }

    return "$moodAdvice 推荐下一项：${nextTask.title}（约 ${nextTask.estimatedMinutes} 分钟）。"
}

@Preview(showBackground = true)
@Composable
fun StudyAgentPreview() {
    HomeworkTheme {
        StudyAgentApp()
    }
}
