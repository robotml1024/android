package com.example.homework.network

import com.example.homework.model.StudyTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

private const val LLM_API_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions"
private const val LLM_API_KEY = "sk-ceb7fd17109949bf9e6cc8c39e4b8d7d"
private const val LLM_MODEL_NAME = "qwen3.5-flash-2026-02-23"

suspend fun fetchAiRecommendation(
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

            OutputStreamWriter(connection.outputStream, Charsets.UTF_8).use { it.write(payload.toString()) }

            val statusCode = connection.responseCode
            val stream = if (statusCode in 200..299) connection.inputStream else connection.errorStream
            val responseText = stream?.bufferedReader()?.use { it.readText() }.orEmpty()

            if (statusCode !in 200..299) {
                throw IllegalStateException("HTTP $statusCode: ${responseText.ifBlank { "empty error body" }}")
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
