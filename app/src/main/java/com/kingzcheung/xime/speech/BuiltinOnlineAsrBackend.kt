package com.kingzcheung.xime.speech

import android.content.Context
import com.kingzcheung.xime.settings.SettingsPreferences
import java.io.ByteArrayOutputStream
import java.util.concurrent.TimeUnit

/**
 * 内置在线语音识别后端 —— **不需要安装任何插件**。
 *
 * 用户只需在设置里选服务商并填 API Key（[AsrKeyStore] 加密保存），App 自己发请求。
 * 具体协议由 [AsrPreset.protocol] 分派：
 * - [AsrProtocol.DASHSCOPE_WS]：实时流式（[DashScopeAsrSession]），边说边出字；
 * - [AsrProtocol.OPENAI_TRANSCRIPTIONS] / [AsrProtocol.OPENAI_CHAT_AUDIO]：
 *   整段上传后一次性返回（[HttpTranscribeClient]），说话期间只累积 PCM。
 *
 * 插件通道（[PluginAsrBackendAdapter]）保持独立，两者并列可选。
 */
class BuiltinOnlineAsrBackend private constructor(
    private val context: Context,
    private val preset: AsrPreset,
    private val apiKey: String,
    private val model: String,
    private val baseUrl: String,
    private val language: String,
) : AsrBackend {

    companion object {
        private const val TAG = "BuiltinOnlineAsrBackend"

        /** 本后端在"服务商"列表里的保留 id（仅用于偏好与界面展示）。 */
        const val ID = "builtin_asr"

        /** 当前选中的服务商预置。 */
        fun activePreset(context: Context): AsrPreset {
            val id = SettingsPreferences.getAsrProvider(context)
            return AsrPresetCatalog.findOrDefault(id)
        }

        /** 是否已配置可用（选中的服务商填了 Key，或该服务商允许无 Key）。 */
        fun isConfigured(context: Context): Boolean {
            val preset = activePreset(context)
            val key = AsrKeyStore.of(context).get(preset.id)
            return key.isNotBlank() || !preset.requiresKey
        }

        /** 后端构造：返回 null 表示未配置（调用方据此给出"未填 Key"提示）。 */
        fun create(context: Context): BuiltinOnlineAsrBackend? {
            val preset = activePreset(context)
            val key = AsrKeyStore.of(context).get(preset.id)
            if (key.isBlank() && preset.requiresKey) return null
            val model = SettingsPreferences.getAsrModel(context, preset.id)
                .ifBlank { preset.defaultModel }
            val base = SettingsPreferences.getAsrBaseUrl(context, preset.id)
            val language = SettingsPreferences.getAsrLanguage(context, preset.id)
            return BuiltinOnlineAsrBackend(
                context = context.applicationContext,
                preset = preset,
                apiKey = key,
                model = model,
                baseUrl = base,
                language = language,
            )
        }
    }

    override val name: String = preset.displayName

    private val client = okhttp3.OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(0, TimeUnit.MILLISECONDS)
        .build()

    /** 整段模式下的 PCM 累积。 */
    private val pcmBuffer = ByteArrayOutputStream()

    private var session: DashScopeAsrSession? = null

    @Volatile
    private var available = false

    @Volatile
    private var cancelled = false

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
        available = apiKey.isNotBlank() || !preset.requiresKey
        return available
    }

    override fun start(): Boolean {
        if (!available) {
            onError?.invoke("未配置 API Key")
            return false
        }
        cancelled = false
        pcmBuffer.reset()
        onState?.invoke(RecognitionState.LISTENING)
        return when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> startStreaming()
            else -> true
        }
    }

    private fun startStreaming(): Boolean {
        val endpoint = preset.endpoint(baseUrl)
        if (endpoint.isBlank()) {
            onError?.invoke("未填写请求地址")
            return false
        }
        val created = DashScopeAsrSession(
            endpoint = endpoint,
            apiKey = apiKey,
            model = model,
            client = client,
            callbacks = object : DashScopeAsrSession.Callbacks {
                override fun onReady() = Unit

                override fun onPartial(text: String) {
                    if (cancelled) return
                    onPartial?.invoke(text)
                }

                override fun onFinal(text: String) {
                    if (cancelled) return
                    onResult?.invoke(text)
                }

                override fun onError(message: String) {
                    if (cancelled) return
                    onError?.invoke(message)
                }
            },
        )
        session = created
        return created.connect()
    }

    override fun processAudioChunk(buffer: ByteArray) {
        when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> session?.feed(buffer)
            else -> pcmBuffer.write(buffer)
        }
    }

    override fun stop() {
        when (preset.protocol) {
            AsrProtocol.DASHSCOPE_WS -> {
                session?.finish()
            }

            else -> transcribeBuffered()
        }
    }

    private fun transcribeBuffered() {
        onState?.invoke(RecognitionState.PROCESSING)
        val pcm = pcmBuffer.toByteArray()
        pcmBuffer.reset()
        if (pcm.isEmpty()) {
            onError?.invoke("没有采集到音频")
            onState?.invoke(RecognitionState.IDLE)
            return
        }
        Thread {
            // 整段转写用独立 client：流式那个设置了无限读超时，整段请求需要有限超时
            val result = HttpTranscribeClient().transcribe(
                preset = preset,
                pcm = pcm,
                model = model,
                apiKey = apiKey,
                baseUrlOverride = baseUrl,
                language = language,
            )
            if (cancelled) return@Thread
            when (result) {
                is HttpTranscribeClient.Result.Success -> onResult?.invoke(result.text)
                is HttpTranscribeClient.Result.Failure -> onError?.invoke(result.message)
            }
            onState?.invoke(RecognitionState.IDLE)
        }.start()
    }

    override fun cancel() {
        cancelled = true
        session?.close()
        session = null
        pcmBuffer.reset()
        onState?.invoke(RecognitionState.IDLE)
    }

    override fun release() {
        cancel()
    }

    override fun isAvailable(): Boolean = available
}
