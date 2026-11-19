package com.kingzcheung.xime.speech

interface AsrBackend {
    val name: String

    /**
     * 该后端是否**自己采集音频**（自行持有麦克风并驱动识别）。
     *
     * 目前只有系统语音引擎（android.speech.SpeechRecognizer）为 true：
     * 它是黑盒，自己录音并经 RecognitionListener 回调，无法接收外部音频块。
     * 为 true 时 [SpeechRecognitionManager] **不启动** AudioRecord 录音线程，
     * 也不调用 [processAudioChunk]；其余后端一律 false，走现有流式路径。
     */
    val capturesAudioItself: Boolean get() = false

    
    fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)? = null,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit
    )
    
    /**
     * 引擎级可恢复失败（如系统语音引擎连不上厂商服务）回调：宿主可借此临时切到
     * 本地/内置在线引擎，返回替代引擎的显示名（null = 无可用替代）。
     * 默认 null，只有需要降级提示的后端实现它。
     */
    var onRecoverableEngineFailure: (() -> String?)?
        get() = null
        set(_) {}

    fun initialize(): Boolean
    fun start(): Boolean
    fun processAudioChunk(buffer: ByteArray)
    fun stop()
    fun cancel()
    fun release()
    fun isAvailable(): Boolean
}
