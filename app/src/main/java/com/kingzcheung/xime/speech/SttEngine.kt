package com.kingzcheung.xime.speech

/**
 * 语音识别引擎。三者互斥单选：
 * - [LOCAL]：本地离线模型（:asr 进程 + zipformer2）
 * - [BUILTIN_ONLINE]：内置在线识别，App 自己发请求，**不需要安装任何插件**
 * - [PLUGIN]：已安装的在线 ASR 插件
 */
enum class SttEngine(val prefValue: String) {
    LOCAL("local"),
    BUILTIN_ONLINE("builtin"),
    PLUGIN("plugin");

    companion object {
        fun fromPrefValue(value: String?): SttEngine? =
            entries.firstOrNull { it.prefValue == value }
    }
}

/** 引擎不可用的原因（用于给出可操作提示，而不是静默改道）。 */
enum class SttUnavailable {
    /** 本地模式但没有可用的离线模型。 */
    MODEL_MISSING,

    /** 内置在线模式但没有填 API Key。 */
    KEY_MISSING,

    /** 插件模式但没有启用任何语音插件。 */
    PLUGIN_MISSING,
}

/**
 * 引擎解析结果。
 *
 * [fallbackFrom] 非空表示"用户选的引擎不可用，自动降级到了 [engine]"，
 * 界面应据此提示用户；[unavailable] 非空表示无可用引擎，此时 [engine]
 * 只是用户的选择，不可直接使用。
 */
data class SttEngineResolution(
    val engine: SttEngine,
    val fallbackFrom: SttEngine? = null,
    val unavailable: SttUnavailable? = null,
) {
    val isAvailable: Boolean get() = unavailable == null

    /** 实际降级过（与用户选择不一致）。 */
    val isFallback: Boolean get() = fallbackFrom != null && fallbackFrom != engine
}

object SttEngineResolver {

    /**
     * 解析当前应使用的识别引擎。
     *
     * 回退规则（刻意保守：本地/内置不可用时**不**偷偷换到别的引擎，
     * 否则用户会"以为在跑 A，其实在跑 B"，出错时无从判断）：
     * - [SttEngine.LOCAL] 且无本地模型 → [SttUnavailable.MODEL_MISSING]
     * - [SttEngine.BUILTIN_ONLINE] 且未填 Key → [SttUnavailable.KEY_MISSING]
     * - [SttEngine.PLUGIN] 且无已启用插件 → 有内置 Key 则降级到内置在线，否则
     *   [SttUnavailable.PLUGIN_MISSING]
     */
    fun resolve(
        preferred: SttEngine?,
        localAvailable: Boolean,
        builtinConfigured: Boolean,
        enabledPluginIds: List<String>,
    ): SttEngineResolution {
        val pref = preferred ?: SttEngine.BUILTIN_ONLINE
        return when (pref) {
            SttEngine.LOCAL ->
                if (localAvailable) {
                    SttEngineResolution(SttEngine.LOCAL)
                } else {
                    SttEngineResolution(SttEngine.LOCAL, unavailable = SttUnavailable.MODEL_MISSING)
                }

            SttEngine.BUILTIN_ONLINE ->
                if (builtinConfigured) {
                    SttEngineResolution(SttEngine.BUILTIN_ONLINE)
                } else {
                    SttEngineResolution(
                        SttEngine.BUILTIN_ONLINE,
                        unavailable = SttUnavailable.KEY_MISSING,
                    )
                }

            SttEngine.PLUGIN ->
                if (enabledPluginIds.isNotEmpty()) {
                    SttEngineResolution(SttEngine.PLUGIN)
                } else if (builtinConfigured) {
                    SttEngineResolution(
                        SttEngine.BUILTIN_ONLINE,
                        fallbackFrom = SttEngine.PLUGIN,
                    )
                } else {
                    SttEngineResolution(SttEngine.PLUGIN, unavailable = SttUnavailable.PLUGIN_MISSING)
                }
        }
    }
}
