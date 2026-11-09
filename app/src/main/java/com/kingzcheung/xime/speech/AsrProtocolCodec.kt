package com.kingzcheung.xime.speech

import org.json.JSONObject

/**
 * 在线 ASR 协议的报文编解码（纯函数，便于单测锁定字段）。
 *
 * 组装与解析集中在这里，后端只负责 IO，避免"字段写错了但只有真机联网才能发现"。
 */
object AsrProtocolCodec {

    /** 服务端事件。 */
    sealed interface DashScopeEvent {
        /** task-started：可以开始发送二进制音频帧。 */
        data object Started : DashScopeEvent

        /** 中间结果（sentence_end=false）。 */
        data class Partial(val text: String) : DashScopeEvent

        /** 最终结果（sentence_end=true）。 */
        data class Final(val text: String) : DashScopeEvent

        /** task-finished：任务结束，可以关闭连接。 */
        data object Finished : DashScopeEvent

        /** task-failed：带服务端错误码与原文。 */
        data class Failed(val code: String, val message: String) : DashScopeEvent

        /** 心跳或未知事件，忽略。 */
        data object Unknown : DashScopeEvent
    }

    // ── DashScope 请求 ────────────────────────────────────────

    /** run-task：声明任务与模型；音频随后以二进制帧发送。 */
    fun runTaskJson(model: String, taskId: String, sampleRate: Int = 16000): String =
        JSONObject().apply {
            put(
                "header",
                JSONObject().apply {
                    put("action", "run-task")
                    put("task_id", taskId)
                    put("streaming", "duplex")
                },
            )
            put(
                "payload",
                JSONObject().apply {
                    put("task_group", "audio")
                    put("task", "asr")
                    put("function", "recognition")
                    put("model", model)
                    put(
                        "parameters",
                        JSONObject().apply {
                            put("format", "pcm")
                            put("sample_rate", sampleRate)
                        },
                    )
                    put("input", JSONObject())
                },
            )
        }.toString()

    /** finish-task：通知服务端音频发送完毕，等待剩余结果。 */
    fun finishTaskJson(taskId: String): String = JSONObject().apply {
        put(
            "header",
            JSONObject().apply {
                put("action", "finish-task")
                put("task_id", taskId)
                put("streaming", "duplex")
            },
        )
        put("payload", JSONObject().put("input", JSONObject()))
    }.toString()

    // ── DashScope 响应 ────────────────────────────────────────

    /** 解析服务端下发的一条文本消息。非法 JSON 视为 [DashScopeEvent.Unknown]。 */
    fun parseDashScopeEvent(text: String): DashScopeEvent {
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return DashScopeEvent.Unknown
        val header = root.optJSONObject("header") ?: return DashScopeEvent.Unknown
        return when (header.optString("event")) {
            "task-started" -> DashScopeEvent.Started
            "task-finished" -> DashScopeEvent.Finished
            "task-failed" -> DashScopeEvent.Failed(
                code = header.optString("error_code").ifBlank { "UNKNOWN" },
                message = header.optString("error_message").ifBlank { "识别失败" },
            )
            "result-generated" -> parseSentence(root)
            else -> DashScopeEvent.Unknown
        }
    }

    private fun parseSentence(root: JSONObject): DashScopeEvent {
        val sentence = root.optJSONObject("payload")
            ?.optJSONObject("output")
            ?.optJSONObject("sentence")
            ?: return DashScopeEvent.Unknown
        if (sentence.optBoolean("heartbeat", false)) return DashScopeEvent.Unknown
        val result = sentence.optString("text", "")
        if (result.isBlank()) return DashScopeEvent.Unknown
        return if (sentence.optBoolean("sentence_end", false)) {
            DashScopeEvent.Final(result)
        } else {
            DashScopeEvent.Partial(result)
        }
    }

    // ── 整段转写响应 ──────────────────────────────────────────

    /**
     * 从整段转写响应里取文本。
     *
     * - [AsrProtocol.OPENAI_TRANSCRIPTIONS]：顶层 `text`（OpenAI / MiniMax 均如此）
     * - [AsrProtocol.OPENAI_CHAT_AUDIO]：`choices[0].message.content`，同时兜底顶层 `text`
     *
     * 解析不出来返回 null，由调用方决定怎么报错（不要静默返回空串，否则失败像成功）。
     */
    fun parseTranscript(body: String, protocol: AsrProtocol): String? {
        val root = runCatching { JSONObject(body) }.getOrNull() ?: return null
        root.optString("text").takeIf { it.isNotBlank() }?.let { return it.trim() }
        if (protocol == AsrProtocol.OPENAI_CHAT_AUDIO) {
            val content = root.optJSONArray("choices")
                ?.optJSONObject(0)
                ?.optJSONObject("message")
                ?.optString("content")
            if (!content.isNullOrBlank()) return content.trim()
        }
        return null
    }

    // ── 错误文案 ──────────────────────────────────────────────

    /**
     * HTTP 失败时的可自诊断文案：状态码 + 常见含义 + 服务端原文片段。
     *
     * 用户常常没有代理环境、也没法抓包，错误串本身就得说清该改哪里。
     */
    fun describeHttpError(code: Int, body: String): String {
        val hint = when (code) {
            400 -> "请求被拒绝，检查模型名与音频格式"
            401, 403 -> "API Key 无效或无权限"
            404 -> "地址或模型不存在，检查 Base URL"
            408, 504 -> "服务端超时，稍后重试"
            413 -> "音频过大"
            429 -> "请求过于频繁或额度不足"
            in 500..599 -> "服务端错误，稍后重试"
            else -> "请求失败"
        }
        val detail = body.replace(Regex("\\s+"), " ").trim().take(200)
        return if (detail.isEmpty()) "HTTP $code · $hint" else "HTTP $code · $hint：$detail"
    }

    // ── 认证头 ────────────────────────────────────────────────

    /** 按服务商风格生成认证头。 */
    fun authHeaders(preset: AsrPreset, apiKey: String): Map<String, String> {
        if (apiKey.isBlank()) return emptyMap()
        return when (preset.authStyle) {
            AsrAuthStyle.BEARER -> mapOf("Authorization" to "Bearer $apiKey")
            AsrAuthStyle.API_KEY_AND_BEARER -> mapOf(
                "api-key" to apiKey,
                "Authorization" to "Bearer $apiKey",
            )
        }
    }
}
