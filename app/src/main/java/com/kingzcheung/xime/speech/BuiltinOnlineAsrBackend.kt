package com.kingzcheung.xime.speech

import android.content.Context
import android.util.Base64
import com.kingzcheung.xime.settings.SettingsPreferences
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 内置在线语音识别（阿里百炼 DashScope 实时流式）。
 *
 * 与插件版 FunASR 协议一致，但作为 App 内置能力提供：用户只需在
 * 「设置 → 语音转文本」里填 API Key 即可使用，无需安装插件。
 *
 * 内置理由：在线识别是输入法高频基础能力，插件化会让用户多绕一步；
 * 插件通道继续保留，供第三方服务商接入。
 */
class BuiltinOnlineAsrBackend(
    private val context: Context,
    private val apiKey: String,
    private val model: String = DEFAULT_MODEL,
) : AsrBackend {

    companion object {
        const val ID = "builtin_dashscope_asr"
        const val NAME = "内置在线识别（阿里百炼）"
        const val DEFAULT_MODEL = "qwen-audio-3.1-asr-flash-streaming"
        private const val WS_URL = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/"

        fun isConfigured(context: Context): Boolean =
            SettingsPreferences.getBuiltinAsrApiKey(context).isNotBlank()

        fun create(context: Context): BuiltinOnlineAsrBackend? {
            val key = SettingsPreferences.getBuiltinAsrApiKey(context)
            if (key.isBlank()) return null
            return BuiltinOnlineAsrBackend(
                context, key, SettingsPreferences.getBuiltinAsrModel(context)
            )
        }
    }

    override val name: String = NAME

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private val taskId = UUID.randomUUID().toString().replace("-", "")
    @Volatile private var started = false
    @Volatile private var available = false

    private var onResult: ((String) -> Unit)? = null
    private var onPartial: ((String) -> Unit)? = null
    private var onState: ((RecognitionState) -> Unit)? = null
    private var onError: ((String) -> Unit)? = null

    override fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)?,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit,
    ) {
        this.onResult = onResult
        this.onPartial = onPartialResult
        this.onState = onStateChange
        this.onError = onError
    }

    override fun initialize(): Boolean {
        available = apiKey.isNotBlank()
        return available
    }

    override fun start(): Boolean {
        if (!available) return false
        return try {
            val request = Request.Builder()
                .url(WS_URL)
                .header("Authorization", "bearer $apiKey")
                .header("X-DashScope-DataInspection", "enable")
                .build()
            socket = client.newWebSocket(request, listener)
            true
        } catch (e: Exception) {
            onError?.invoke(e.message ?: "连接失败")
            false
        }
    }

    override fun processAudioChunk(buffer: ByteArray) {
        if (!started) return
        val b64 = Base64.encodeToString(buffer, Base64.NO_WRAP)
        val json = JSONObject().apply {
            put("header", JSONObject().apply {
                put("action", "result-generated")
                put("task_id", taskId)
                put("streaming", "duplex")
            })
            put("payload", JSONObject().put("input", JSONObject().put("audio", b64)))
        }
        socket?.send(json.toString())
    }

    override fun stop() {
        socket?.send(finishMessage())
        socket?.close(1000, "done")
        socket = null
        started = false
    }

    override fun cancel() {
        socket?.close(1000, "cancel")
        socket = null
        started = false
    }

    override fun release() {
        cancel()
    }

    override fun isAvailable(): Boolean = available

    private fun startMessage(): String = JSONObject().apply {
        put("header", JSONObject().apply {
            put("action", "run-task")
            put("task_id", taskId)
            put("streaming", "duplex")
        })
        put("payload", JSONObject().apply {
            put("task_group", "audio")
            put("task", "asr")
            put("function", "recognition")
            put("model", model)
            put("parameters", JSONObject().apply {
                put("format", "pcm")
                put("sample_rate", 16000)
            })
            put("input", JSONObject())
        })
    }.toString()

    private fun finishMessage(): String = JSONObject().apply {
        put("header", JSONObject().apply {
            put("action", "finish-task")
            put("task_id", taskId)
            put("streaming", "duplex")
        })
        put("payload", JSONObject().put("input", JSONObject()))
    }.toString()

    private val listener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(startMessage())
            onState?.invoke(RecognitionState.LISTENING)
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val root = JSONObject(text)
                val header = root.optJSONObject("header")
                val event = header?.optString("event").orEmpty()
                if (event == "task-started") {
                    started = true
                    return@runCatching
                }
                if (event == "task-failed") {
                    onError?.invoke(header?.optString("error_message") ?: "识别失败")
                    return@runCatching
                }
                val sentence = root.optJSONObject("payload")
                    ?.optJSONObject("output")?.optJSONObject("sentence")
                    ?: return@runCatching
                val text = sentence.optString("text", "")
                if (text.isBlank()) return@runCatching
                if (sentence.optBoolean("sentence_end", false)) {
                    onResult?.invoke(text)
                } else {
                    onPartial?.invoke(text)
                }
            }
        }

        override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
            started = false
            onError?.invoke(t.message ?: "连接中断")
        }

        override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
            started = false
        }
    }
}
