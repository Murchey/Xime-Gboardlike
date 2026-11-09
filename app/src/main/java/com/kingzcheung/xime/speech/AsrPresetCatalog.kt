package com.kingzcheung.xime.speech

/**
 * 内置在线语音识别服务商预置。
 *
 * 三种协议，覆盖当前主流在线 ASR：
 * - [AsrProtocol.DASHSCOPE_WS]：阿里百炼双向流式 WebSocket（run-task / 二进制音频帧 /
 *   result-generated）。实时出字，延迟最低。
 * - [AsrProtocol.OPENAI_TRANSCRIPTIONS]：OpenAI 风格的 multipart `/audio/transcriptions`
 *   （整段上传后返回 `{"text": ...}`）。OpenAI、MiniMax、多数自建反代都走这一种。
 * - [AsrProtocol.OPENAI_CHAT_AUDIO]：OpenAI 风格的 `/chat/completions` + `input_audio`
 *   消息内容（MiMo 等把 ASR 挂在对话接口上的服务商）。
 *
 * 所有预置的 Base URL 都允许用户改写（自建反代/区域端点/国际站），因此
 * [baseUrlEditable] 恒为 true；`custom_openai` 则是完全空白的模板。
 */
enum class AsrProtocol {
    /** 阿里百炼 DashScope 双向流式 WebSocket。 */
    DASHSCOPE_WS,

    /** OpenAI 兼容的 multipart 整段转写。 */
    OPENAI_TRANSCRIPTIONS,

    /** OpenAI 兼容的 chat/completions + input_audio。 */
    OPENAI_CHAT_AUDIO,
}

/** 认证头的写法差异（MiMo 用 `api-key`，其余用 `Authorization: Bearer`）。 */
enum class AsrAuthStyle {
    /** `Authorization: Bearer <key>` */
    BEARER,

    /** `api-key: <key>`，同时附带 `Authorization: Bearer <key>` 兼容代理。 */
    API_KEY_AND_BEARER,
}

data class AsrPreset(
    val id: String,
    val displayName: String,
    val protocol: AsrProtocol,
    /** 服务根地址（不含具体路径）。用户可改写。 */
    val baseUrl: String,
    /** 具体请求路径；[AsrProtocol.DASHSCOPE_WS] 时为完整 wss 地址（baseUrl 即终点）。 */
    val endpointPath: String,
    val defaultModel: String,
    val modelOptions: List<String> = listOf(defaultModel),
    /** Key 的获取说明，设置页 supportingText 直接展示。 */
    val keyHelp: String = "",
    val authStyle: AsrAuthStyle = AsrAuthStyle.BEARER,
    /** 是否必须有 Key（自建反代可能不需要）。 */
    val requiresKey: Boolean = true,
    /** Base URL 是否可编辑（当前全部可编辑，保留字段以便未来锁定官方端点）。 */
    val baseUrlEditable: Boolean = true,
) {
    /**
     * 完整请求地址。
     *
     * [AsrProtocol.DASHSCOPE_WS] 的 `baseUrl` 本身就是终点（含末尾斜杠），原样返回；
     * 其余服务商拼 `baseUrl + "/" + endpointPath`，并容忍用户多写的斜杠。
     */
    fun endpoint(baseUrlOverride: String = ""): String {
        val raw = baseUrlOverride.trim().ifBlank { baseUrl }.trim()
        if (endpointPath.isBlank()) return raw
        return raw.trimEnd('/') + "/" + endpointPath.trimStart('/')
    }
}

object AsrPresetCatalog {

    /** 阿里百炼（国内，实时流式，延迟最低）。 */
    val ALIYUN = AsrPreset(
        id = "aliyun_bailian",
        displayName = "阿里百炼（通义）",
        protocol = AsrProtocol.DASHSCOPE_WS,
        baseUrl = "wss://dashscope.aliyuncs.com/api-ws/v1/inference/",
        endpointPath = "",
        defaultModel = "qwen-audio-3.1-asr-flash-streaming",
        modelOptions = listOf(
            "qwen-audio-3.1-asr-flash-streaming",
            "qwen-audio-3.1-asr-flash-message",
            "qwen-audio-3.0-asr-flash-streaming",
        ),
        keyHelp = "阿里云百炼控制台 → API-KEY",
    )

    /** MiMo：ASR 挂在 chat/completions 上，模型 mimo-v2.5-asr。 */
    val MIMO = AsrPreset(
        id = "mimo",
        displayName = "MiMo",
        protocol = AsrProtocol.OPENAI_CHAT_AUDIO,
        baseUrl = "https://api.xiaomimimo.com/v1",
        endpointPath = "chat/completions",
        defaultModel = "mimo-v2.5-asr",
        modelOptions = listOf("mimo-v2.5-asr"),
        keyHelp = "MiMo 控制台 → API Key（请求头使用 api-key）",
        authStyle = AsrAuthStyle.API_KEY_AND_BEARER,
    )

    /** MiniMax 国内站：POST /v1/speech_to_text（multipart，响应 {"text": ...}）。 */
    val MINIMAX = AsrPreset(
        id = "minimax",
        displayName = "MiniMax",
        protocol = AsrProtocol.OPENAI_TRANSCRIPTIONS,
        baseUrl = "https://api.minimaxi.com",
        endpointPath = "v1/speech_to_text",
        defaultModel = "asr-1.0",
        modelOptions = listOf("asr-1.0"),
        keyHelp = "MiniMax 开放平台 → API Key（国际站请把 Base URL 改为 https://api.minimax.io）",
    )

    /** OpenAI 官方。 */
    val OPENAI = AsrPreset(
        id = "openai",
        displayName = "OpenAI",
        protocol = AsrProtocol.OPENAI_TRANSCRIPTIONS,
        baseUrl = "https://api.openai.com/v1",
        endpointPath = "audio/transcriptions",
        defaultModel = "gpt-4o-transcribe",
        modelOptions = listOf("gpt-4o-transcribe", "gpt-4o-mini-transcribe", "whisper-1"),
        keyHelp = "platform.openai.com → API keys",
    )

    /** 自建反代 / 其它 OpenAI 兼容服务：地址与模型全部由用户填写。 */
    val CUSTOM_OPENAI = AsrPreset(
        id = "custom_openai",
        displayName = "自定义（OpenAI 兼容）",
        protocol = AsrProtocol.OPENAI_TRANSCRIPTIONS,
        baseUrl = "",
        endpointPath = "audio/transcriptions",
        defaultModel = "whisper-1",
        modelOptions = emptyList(),
        keyHelp = "填写兼容 OpenAI /audio/transcriptions 的服务地址与模型名；Key 可留空",
        requiresKey = false,
    )

    val all: List<AsrPreset> = listOf(ALIYUN, MIMO, MINIMAX, OPENAI, CUSTOM_OPENAI)

    val default: AsrPreset = ALIYUN

    fun findById(id: String): AsrPreset? = all.firstOrNull { it.id == id }

    fun findOrDefault(id: String): AsrPreset = findById(id) ?: default
}
