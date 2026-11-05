package com.kingzcheung.xime.speech

/**
 * 内置在线语音识别服务商预置。
 *
 * 各家协议分两类：
 *  - [Protocol.DASHSCOPE_WS]：阿里百炼/通义实时流式（run-task / result-generated）
 *  - [Protocol.OPENAI_HTTP]：OpenAI 兼容的 /v1/audio/transcriptions（整段上传）
 *
 * MiMo、MiniMax 均提供 OpenAI 兼容的音频转写端点，因此复用同一实现；
 * 用户只需在设置里选服务商、填 Base URL 与 API Key。
 */
enum class AsrProtocol {
    /** 阿里百炼 DashScope 双向流式 WebSocket。 */
    DASHSCOPE_WS,

    /** OpenAI 兼容的 HTTP 整段转写（multipart/form-data）。 */
    OPENAI_HTTP,
}

data class AsrPreset(
    val id: String,
    val displayName: String,
    val protocol: AsrProtocol,
    val baseUrl: String,
    val defaultModel: String,
    val modelOptions: List<String> = listOf(defaultModel),
    val keyHelp: String = "",
) {
    /** 该预置是否要求用户填写 Key（自建反代可留空）。 */
    val requiresKey: Boolean get() = true
}

object AsrPresetCatalog {

    /** 阿里百炼（国内，实时流式，效果与延迟最佳）。 */
    val ALIYUN = AsrPreset(
        id = "aliyun_bailian",
        displayName = "阿里百炼（通义）",
        protocol = AsrProtocol.DASHSCOPE_WS,
        baseUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/",
        defaultModel = "qwen-audio-3.1-asr-flash-streaming",
        modelOptions = listOf(
            "qwen-audio-3.1-asr-flash-streaming",
            "qwen-audio-3.1-asr-flash-message",
            "qwen-audio-3.0-asr-flash-streaming",
        ),
        keyHelp = "阿里云百炼控制台 → API-KEY",
    )

    /** MiMo（OpenAI 兼容端点）。 */
    val MIMO = AsrPreset(
        id = "mimo",
        displayName = "MiMo",
        protocol = AsrProtocol.OPENAI_HTTP,
        baseUrl = "https://api.xiaomimimo.com/v1",
        defaultModel = "mimo-v2-flash",
        modelOptions = listOf("mimo-v2-flash"),
        keyHelp = "在 MiMo 控制台创建 API Key",
    )

    /** MiniMax（OpenAI 兼容端点）。 */
    val MINIMAX = AsrPreset(
        id = "minimax",
        displayName = "MiniMax",
        protocol = AsrProtocol.OPENAI_HTTP,
        baseUrl = "https://api.minimax.chat/v1",
        defaultModel = "audio-01",
        modelOptions = listOf("audio-01"),
        keyHelp = "在 MiniMax 开放平台创建 API Key",
    )

    /** OpenAI（国际通用，gpt-4o-transcribe / whisper-1）。 */
    val OPENAI = AsrPreset(
        id = "openai",
        displayName = "OpenAI",
        protocol = AsrProtocol.OPENAI_HTTP,
        baseUrl = "https://api.openai.com/v1",
        defaultModel = "gpt-4o-transcribe",
        modelOptions = listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1"),
        keyHelp = "platform.openai.com → API keys",
    )

    val all: List<AsrPreset> = listOf(ALIYUN, MIMO, MINIMAX, OPENAI)

    val default: AsrPreset = ALIYUN

    fun findById(id: String): AsrPreset? = all.firstOrNull { it.id == id }

    fun findOrDefault(id: String): AsrPreset = findById(id) ?: default
}
