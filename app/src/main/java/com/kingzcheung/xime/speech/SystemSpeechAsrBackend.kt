package com.kingzcheung.xime.speech

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.util.FileLogger

/**
 * 设备自带语音引擎后端（android.speech.SpeechRecognizer）。
 *
 * **与其他后端的根本差异**：系统引擎自己持有麦克风并经 [RecognitionListener] 回调，
 * 无法接收 App 采集的音频块。因此：
 * - [processAudioChunk] 是空实现（不消费外部音频），录音由系统完成；
 * - [SpeechRecognitionManager] 在本后端生效时**不启动** AudioRecord 录音线程；
 * - 系统引擎不提供原始音频，因此没有振幅/频谱回调，面板需降级显示。
 *
 * 只在 AOSP 标准 [RecognitionListener] 回调上做假设，不依赖任何厂商 extra。
 */
class SystemSpeechAsrBackend(private val context: Context) : AsrBackend {

    companion object {
        private const val TAG = "SystemSpeechAsr"

        /**
         * 识别语言。系统引擎的语言支持因机型而异，这里固定简体中文；
         * 服务不支持时会走 [SpeechRecognizer.ERROR_SERVER] / [SpeechRecognizer.ERROR_NO_MATCH]，
         * 已在错误映射里给出可读提示。
         */
        private const val RECOGNITION_LANGUAGE = "zh-CN"

        /**
         * stopListening 后等待最终结果的兜底时长：超时就销毁 recognizer 关闭系统对话框。
         * 取 3.2s，略大于收尾超时（FINISH_TIMEOUT_MS=3s），保证最终结果有机会先送达。
         */
        private const val SYSTEM_STOP_FALLBACK_MS = 3_200L
    }

    /** 系统引擎自己持有麦克风，故为 true：录音线程会跳过 AudioRecord。 */
    override val capturesAudioItself: Boolean = true

    override val name: String
        get() {
            val title = tr(R.string.s_xitongyuyin)
            val label = SystemSpeechAvailability.serviceLabel(context)
            // 服务应用名常与标题同名（如小米的「系统语音引擎」），避免拼成重复串。
            return if (label.isNullOrBlank() || label == title) title
            else tr(R.string.s_xitongyuyin_shi, label)
        }

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var running = false

    private var resultCallback: ((String) -> Unit)? = null
    private var partialResultCallback: ((String) -> Unit)? = null
    private var stateCallback: ((RecognitionState) -> Unit)? = null
    private var errorCallback: ((String) -> Unit)? = null

    override fun setCallbacks(
        onResult: (String) -> Unit,
        onPartialResult: ((String) -> Unit)?,
        onStateChange: (RecognitionState) -> Unit,
        onError: (String) -> Unit,
    ) {
        resultCallback = onResult
        partialResultCallback = onPartialResult
        stateCallback = onStateChange
        errorCallback = onError
    }

    override fun initialize(): Boolean = isAvailable()

    // 轻量探测：不创建 SpeechRecognizer（它在非主线程会抛异常），任意线程安全。
    override fun isAvailable(): Boolean = SystemSpeechAvailability.quickCheck(context).available

    /**
     * 开始识别。SpeechRecognizer 有主线程约束，统一切到主线程创建与调用；
     * 每次 [start] 都重建实例，避免复用已 destroy 的 recognizer（部分厂商会抛异常）。
     */
    override fun start(): Boolean {
        if (running) return true
        if (!isAvailable()) {
            errorCallback?.invoke(tr(R.string.s_xitongyuyin_bukeyong))
            return false
        }
        running = true
        main.post {
            if (!running) return@post
            releaseRecognizer()
            val rec = try {
                SpeechRecognizer.createSpeechRecognizer(context)
            } catch (e: Throwable) {
                null
            }
            if (rec == null) {
                running = false
                stateCallback?.invoke(RecognitionState.ERROR)
                errorCallback?.invoke(tr(R.string.s_xitongyuyin_chuangjian) )
                return@post
            }
            recognizer = rec
            rec.setRecognitionListener(listenerFor(rec))
            stateCallback?.invoke(RecognitionState.LISTENING)
            try {
                rec.startListening(buildIntent())
            } catch (e: Throwable) {
                running = false
                stateCallback?.invoke(RecognitionState.ERROR)
                errorCallback?.invoke(describeThrowable(e))
            }
        }
        return true
    }

    private fun buildIntent(): Intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
        putExtra(
            RecognizerIntent.EXTRA_LANGUAGE_MODEL,
            RecognizerIntent.LANGUAGE_MODEL_FREE_FORM,
        )
        putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
        putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        putExtra(RecognizerIntent.EXTRA_LANGUAGE, RECOGNITION_LANGUAGE)
    }

    /**
     * 为单次会话创建监听器，并带身份守卫：旧 recognizer 被 destroy/cancel 时仍可能
     * 回调 onError(ERROR_CLIENT)，若不忽略会把「客户端出错」弹给用户，且状态被旧会话污染。
     */
    private fun listenerFor(rec: SpeechRecognizer): RecognitionListener = object : RecognitionListener {
        private fun alive(): Boolean = recognizer === rec

        override fun onReadyForSpeech(params: Bundle?) {
            if (alive()) stateCallback?.invoke(RecognitionState.LISTENING)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) {
            // 系统引擎不提供原始音频，面板的波纹/振幅由调用方降级处理。
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            if (alive()) stateCallback?.invoke(RecognitionState.PROCESSING)
        }

        override fun onError(error: Int) {
            if (!alive()) {
                Log.d(TAG, "ignore stale onError(" + error + ")")
                return
            }
            running = false
            releaseRecognizer()
            if (error == SpeechRecognizer.ERROR_CLIENT) {
                // 会话刚结束就收到 CLIENT 错误属于生命周期噪音（destroy/cancel 竞态），
                // 回 IDLE 即可，不把「客户端出错」弹给用户。
                Log.d(TAG, "ERROR_CLIENT ignored as lifecycle noise")
                stateCallback?.invoke(RecognitionState.IDLE)
                return
            }
            stateCallback?.invoke(RecognitionState.ERROR)
            errorCallback?.invoke(describeError(error))
        }

        override fun onResults(results: Bundle?) {
            if (!alive()) return
            running = false
            releaseRecognizer()
            val text = firstText(results)
            if (text.isEmpty()) {
                // 无识别结果不算错误：用户可能只是没说话或环境太吵。
                stateCallback?.invoke(RecognitionState.IDLE)
            } else {
                stateCallback?.invoke(RecognitionState.IDLE)
                resultCallback?.invoke(text)
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (!alive()) return
            val text = firstText(partialResults)
            if (text.isNotEmpty()) partialResultCallback?.invoke(text)
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun firstText(bundle: Bundle?): String =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
            ?.firstOrNull()?.trim().orEmpty()

    /**
     * 系统引擎的音频由它自己采集，外部块一律忽略。
     * 保留方法只为满足 [AsrBackend] 契约，避免调用方特判。
     */
    override fun processAudioChunk(buffer: ByteArray) = Unit

    override fun stop() {
        running = false
        main.post {
            val rec = recognizer ?: return@post
            runCatching { rec.stopListening() }
            // 兜底：部分系统引擎 stopListening 后不给 onResults（或对话框不消失），
            // 延迟销毁 recognizer 关闭对话框，否则它会一直盖在键盘上，让下一次长按落空。
            main.postDelayed({
                if (recognizer === rec) {
                    releaseRecognizer()
                    stateCallback?.invoke(RecognitionState.IDLE)
                }
            }, SYSTEM_STOP_FALLBACK_MS)
        }
    }

    override fun cancel() {
        running = false
        main.post {
            runCatching { recognizer?.cancel() }
            releaseRecognizer()
            stateCallback?.invoke(RecognitionState.IDLE)
        }
    }

    override fun release() {
        running = false
        main.post { releaseRecognizer() }
    }

    private fun releaseRecognizer() {
        val rec = recognizer
        recognizer = null
        runCatching { rec?.destroy() }
    }

    /** AOSP 错误码 → 可读中文；[SpeechRecognizer.ERROR_NO_MATCH] 不当作错误吓用户。 */
    private fun describeError(error: Int): String = when (error) {
        SpeechRecognizer.ERROR_AUDIO -> tr(R.string.s_yuyin_audioerror)
        SpeechRecognizer.ERROR_CLIENT -> tr(R.string.s_yuyin_clienterror)
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> tr(R.string.s_yuyin_quanxian)
        SpeechRecognizer.ERROR_NETWORK -> tr(R.string.s_yuyin_wangluo)
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT -> tr(R.string.s_yuyin_wangluochaoshi)
        SpeechRecognizer.ERROR_NO_MATCH -> tr(R.string.s_yuyin_weishibie)
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> tr(R.string.s_yuyin_mang)
        SpeechRecognizer.ERROR_SERVER -> tr(R.string.s_yuyin_fuwuyichang)
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> tr(R.string.s_yuyin_meiyoushuohua)
        else -> tr(R.string.s_yuyin_shibai)
    }

    private fun describeThrowable(e: Throwable): String {
        FileLogger.w(TAG, "startListening failed: " + e.message)
        return tr(R.string.s_yuyin_shibai)
    }
}