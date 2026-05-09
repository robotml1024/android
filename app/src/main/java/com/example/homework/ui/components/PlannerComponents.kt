package com.example.homework.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.homework.model.StudyTask
import com.example.homework.util.buildDdlOptions
import com.example.homework.util.isDeadlineNear

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlannerCard(
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
            OutlinedTextField(value = durationInput, onValueChange = { onDurationInputChange(it.filter { ch -> ch.isDigit() }) }, label = { Text("预计时长(分钟)") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(value = categoryInput, onValueChange = onCategoryInputChange, label = { Text("任务类型（如 编程 / 阅读 / 背诵）") }, modifier = Modifier.fillMaxWidth())
            Spacer(Modifier.height(8.dp))
            val ddlOptions = buildDdlOptions()
            var deadlineExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(expanded = deadlineExpanded, onExpandedChange = { deadlineExpanded = !deadlineExpanded }) {
                OutlinedTextField(
                    value = deadlineInput,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("截止时间") },
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = deadlineExpanded) },
                    modifier = Modifier.menuAnchor().fillMaxWidth()
                )

                DropdownMenu(expanded = deadlineExpanded, onDismissRequest = { deadlineExpanded = false }) {
                    ddlOptions.forEach { option ->
                        DropdownMenuItem(text = { Text(option) }, onClick = {
                            onDeadlineInputChange(option)
                            deadlineExpanded = false
                        })
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
            Button(onClick = onAddTask) { Text("加入计划") }

            TaskSection("待完成任务", tasks, 3, Color(0xFFF5F7FF), null, onCompleteTask, "完成", onDeleteTask, "撤销")

            Spacer(Modifier.height(10.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("已完成任务", fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (completedTasks.isNotEmpty()) {
                    TextButton(onClick = onClearCompletedTasks) { Text("清空已完成") }
                }
            }

            TaskSection("", completedTasks, 3, Color(0xFFDFF5E4), "已完成", null, null, null, null)
        }
    }
}

@Composable
private fun TaskSection(title: String, tasks: List<StudyTask>, maxVisibleItems: Int, cardColor: Color, statusText: String?, onPrimaryAction: ((StudyTask) -> Unit)?, primaryActionText: String?, onSecondaryAction: ((StudyTask) -> Unit)?, secondaryActionText: String?) {
    Spacer(Modifier.height(12.dp))
    if (title.isNotBlank()) {
        Text(title, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(6.dp))
    }
    val maxHeight = (maxVisibleItems * 72).dp
    LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.heightIn(max = maxHeight)) {
        items(tasks) { task ->
            val actualColor = if (statusText == null && isDeadlineNear(task.deadline)) Color(0xFFFFE5E5) else cardColor
            TaskItemCard(task, actualColor, statusText, onPrimaryAction, primaryActionText, onSecondaryAction, secondaryActionText)
        }
    }
}

@Composable
private fun TaskItemCard(task: StudyTask, cardColor: Color, statusText: String?, onPrimaryAction: ((StudyTask) -> Unit)?, primaryActionText: String?, onSecondaryAction: ((StudyTask) -> Unit)?, secondaryActionText: String?) {
    Card(shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = cardColor)) {
        Row(modifier = Modifier.fillMaxWidth().padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(task.title, fontWeight = FontWeight.SemiBold)
                Text("${task.estimatedMinutes} 分钟")
                Text("类型：${task.category}")
                Text(if (isDeadlineNear(task.deadline)) "DDL：${task.deadline}（即将截止）" else "DDL：${task.deadline}")
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
