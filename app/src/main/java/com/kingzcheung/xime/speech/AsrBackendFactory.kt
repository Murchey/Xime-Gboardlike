package com.kingzcheung.xime.speech

import android.content.Context

/**
 * ASR 后端工厂：本地离线、内置在线、插件三条通道的统一入口。
 *
 * 引擎选择由 [SttEngineResolver] 决定，因此这里的三个 create 方法只负责
 * "按引擎造后端"，不做"不可用时换一个"的隐式回退（回退集中在解析器里，
 * 便于单测与日志说明"到底在跑哪个引擎"）。
 */
object AsrBackendFactory {
    /** 本地 zipformer2 离线后端。 */
    fun create(context: Context): AsrBackend? = AsrSupport.create(context)

    /** 内置在线后端（免插件）。未配置 Key 时返回 null。 */
    fun createBuiltinOnline(context: Context): AsrBackend? = BuiltinOnlineAsrBackend.create(context)

    /** 本地引擎显示名（当前生效模型名；未下载任何模型时为 null）。 */
    fun getLocalName(context: Context): String? = AsrSupport.getLocalName(context)

    /** 设备是否有可用的系统语音引擎（android.speech.SpeechRecognizer）。 */
    fun isSystemAvailable(context: Context): Boolean =
        SystemSpeechAvailability.check(context).available

    /** 系统语音引擎显示名（系统识别服务的应用名）；探测不到时返回 null。 */
    fun getSystemName(context: Context): String? = SystemSpeechAvailability.serviceLabel(context)

    /** 系统引擎后端。系统引擎自己录音，不接收外部音频块。 */
    fun createSystem(context: Context): AsrBackend = SystemSpeechAsrBackend(context)

    /** 内置在线是否已配置（选中的服务商填了 Key，或该服务商允许无 Key）。 */
    fun isBuiltinConfigured(context: Context): Boolean = BuiltinOnlineAsrBackend.isConfigured(context)

    /** 内置在线显示名，如「内置 · 阿里百炼（通义）」。 */
    fun getBuiltinName(context: Context): String = BuiltinOnlineAsrBackend.activePreset(context).displayName

    /** 预热本地模型并保持常驻（打开"本地识别"开关时调用）。 */
    fun warmup(context: Context) = AsrSupport.warmup(context)

    /** 卸载常驻模型（关闭"本地识别"开关时调用）。 */
    fun releaseModel() = AsrSupport.releaseModel()
}
