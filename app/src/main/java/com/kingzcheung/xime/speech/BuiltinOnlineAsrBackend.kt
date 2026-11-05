package com.kingzcheung.xime.speech

import android.content.Context
import android.util.Base64
import com.kingzcheung.xime.settings.SettingsPreferences
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.util.UUID
import java.util.concurrent.TimeUnit

/**
 * 内置在线语音识别。
 *
 * 支持两类协议，按 [AsrPreset.protocol] 分派：
 *  - DashScope 双向流式 WebSocket（阿里百炼/通义）
 *  - OpenAI 兼容 HTTP 整段转写（OpenAI / MiMo / MiniMax，及自建反代）
 *
 * 内置理由：在线识别是输入法高频基础能力，插件化会让用户多绕一步；
 * 插件通道继续保留，供其它服务商接入。
 */
class BuiltinOnlineAsrBackend(
    private val context: Context,
    private val preset: AsrPreset,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
) : AsrBackend {

    companion object {
        /** 兼容旧调用：默认服务商条目的 id。 */
        const val ID = "builtin_asr"

        fun isConfigured(context: Context): Boolean {
            val id = SettingsPreferences.getAsrProvider(context).ifBlank { AsrPresetCatalog.default.id }
            return SettingsPreferences.getAsrApiKey(context, id).isNotBlank()
        }

        fun activePreset(context: Context): AsrPreset {
            val id = SettingsPreferences.getAsrProvider(context).ifBlank { AsrPresetCatalog.default.id }
            return AsrPresetCatalog.findOrDefault(id)
        }

        fun create(context: Context): BuiltinOnlineAsrBackend? {
            val p = activePreset(context)
            val key = SettingsPreferences.getAsrApiKey(context, p.id)
            if (key.isBlank()) return null
            val model = SettingsPreferences.getAsrModel(context, p.id).ifBlank { p.defaultModel }
            val base = SettingsPreferences.getAsrBaseUrl(context, p.id).ifBlank { p.baseUrl }
            return BuiltinOnlineAsrBackend(context, p, key, model, base)
        }
    }

    override val name: String = preset.displayName

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    private var socket: WebSocket? = null
    private val taskId = UUID.randomUUID().toString().replace("-", "")
    @Volatile private var started = false
    @Volatile private var available = false

    /** OpenAI 兼容协议需要整段音频，先缓存 PCM。 */
    private val pcmBuffer = ByteArrayOutputStream()

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
        onState?.invoke(RecognitionState.LISTENING)
        return when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> runCatching {
                val request = Request.Builder()
                    .url(baseUrl)
                    .header("Authorization", "bearer $apiKey")
                    .header("X-DashScope-DataInspection", "enable")
                    .build()
                socket = client.newWebSocket(request, dashscopeListener)
                true
            }.getOrElse {
                onError?.invoke(it.message ?: "连接失败")
                false
            }
            AsrProtocol.OPENAI_HTTP -> {
                pcmBuffer.reset()
                true
            }
        }
    }

    override fun processAudioChunk(buffer: ByteArray) {
        when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> {
                if (!started) return
                val json = JSONObject().apply {
                    put("header", JSONObject().apply {
                        put("action", "result-generated")
                        put("task_id", taskId)
                        put("streaming", "duplex")
                    })
                    put("payload", JSONObject().put("input", JSONObject().apply {
                        put("audio", Base64.encodeToString(buffer, Base64.NO_WRAP))
                    }))
                }
                socket?.send(json.toString())
            }
            AsrProtocol.OPENAI_HTTP -> pcmBuffer.write(buffer)
        }
    }

    override fun stop() {
        when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> {
                socket?.send(finishMessage())
                socket?.close(1000, "done")
                socket = null
                started = false
            }
            AsrProtocol.OPENAI_HTTP -> transcribeHttp()
        }
    }

    override fun cancel() {
        socket?.close(1000, "cancel")
        socket = null
        started = false
        pcmBuffer.reset()
        onState?.invoke(RecognitionState.IDLE)
    }

    override fun release() = cancel()

    override fun isAvailable(): Boolean = available

    // ── OpenAI 兼容整段转写 ─────────────────────────────────────

    private fun transcribeHttp() {
        onState?.invoke(RecognitionState.PROCESSING)
        Thread {
            runCatching {
                val pcm = pcmBuffer.toByteArray()
                pcmBuffer.reset()
                if (pcm.isEmpty()) {
                    onError?.invoke("没有采集到音频")
                    return@Thread
                }
                val wav = pcmToWav(pcm, sampleRate = 16000)
                val endpoint = baseUrl.trimEnd('/') + "/audio/transcriptions"
                val body = MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart(
                        "file", "audio.wav",
                        wav.toRequestBody("audio/wav".toMediaType())
                    )
                    .addFormDataPart("model", model)
                    .build()
                val request = Request.Builder()
                    .url(endpoint)
                    .header("Authorization", "Bearer $apiKey")
                    .post(body)
                    .build()
                client.newCall(request).execute().use { resp ->
                    val text = resp.body?.string().orEmpty()
                    if (!resp.isSuccessful) {
                        onError?.invoke("HTTP ${resp.code}: ${text.take(200)}")
                        return@Thread
                    }
                    val transcript = runCatching {
                        JSONObject(text).optString("text", "")
                    }.getOrElse { text }
                    if (transcript.isNotBlank()) onResult?.invoke(transcript.trim())
                    else onError?.invoke("未识别到内容")
                }
            }.onFailure { onError?.invoke(it.message ?: "转写失败") }
            onState?.invoke(RecognitionState.IDLE)
        }.start()
    }

    /** PCM16 单声道 → WAV（44 字节头）。 */
    private fun pcmToWav(pcm: ByteArray, sampleRate: Int): ByteArray {
        val out = ByteArrayOutputStream()
        val dataLen = pcm.size
        val byteRate = sampleRate * 2
        fun le32(v: Int) = byteArrayOf(
            (v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte(),
            ((v shr 16) and 0xFF).toByte(), ((v shr 24) and 0xFF).toByte(),
        )
        fun le16(v: Int) = byteArrayOf((v and 0xFF).toByte(), ((v shr 8) and 0xFF).toByte())
        out.write("RIFF".toByteArray()); out.write(le32(36 + dataLen))
        out.write("WAVE".toByteArray()); out.write("fmt ".toByteArray())
        out.write(le32(16)); out.write(le16(1)); out.write(le16(1))
        out.write(le32(sampleRate)); out.write(le32(byteRate))
        out.write(le16(2)); out.write(le16(16))
        out.write("data".toByteArray()); out.write(le32(dataLen)); out.write(pcm)
        return out.toByteArray()
    }

    // ── DashScope 流式 ──────────────────────────────────────────

    private fun startMessage(): String = JSONObject().apply {
        put("header", JSONObject().apply {
            put("action", "run-task"); put("task_id", taskId); put("streaming", "duplex")
        })
        put("payload", JSONObject().apply {
            put("task_group", "audio"); put("task", "asr"); put("function", "recognition")
            put("model", model)
            put("parameters", JSONObject().apply {
                put("format", "pcm"); put("sample_rate", 16000)
            })
            put("input", JSONObject())
        })
    }.toString()

    private fun finishMessage(): String = JSONObject().apply {
        put("header", JSONObject().apply {
            put("action", "finish-task"); put("task_id", taskId); put("streaming", "duplex")
        })
        put("payload", JSONObject().put("input", JSONObject()))
    }.toString()

    private val dashscopeListener = object : WebSocketListener() {
        override fun onOpen(webSocket: WebSocket, response: Response) {
            webSocket.send(startMessage())
        }

        override fun onMessage(webSocket: WebSocket, text: String) {
            runCatching {
                val root = JSONObject(text)
                val header = root.optJSONObject("header")
                when (header?.optString("event")) {
                    "task-started" -> started = true
                    "task-failed" -> onError?.invoke(header.optString("error_message"))
                    else -> {
                        val sentence = root.optJSONObject("payload")
                            ?.optJSONObject("output")?.optJSONObject("sentence")
                            ?: return@runCatching
                        val t = sentence.optString("text", "")
                        if (t.isBlank()) return@runCatching
                        if (sentence.optBoolean("sentence_end", false)) onResult?.invoke(t)
                        else onPartial?.invoke(t)
                    }
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
