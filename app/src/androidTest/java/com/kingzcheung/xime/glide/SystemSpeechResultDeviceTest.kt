package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import com.kingzcheung.xime.speech.SystemSpeechAsrBackend
import com.kingzcheung.xime.speech.RecognitionState
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/** 验证系统语音引擎的完整链路：start → LISTENING → 结果回调。 */
@RunWith(AndroidJUnit4::class)
class SystemSpeechResultDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val inst = InstrumentationRegistry.getInstrumentation()

    @Test
    fun result() {
        val backend = SystemSpeechAsrBackend(context)
        if (!backend.isAvailable()) {
            android.util.Log.i("SysResult", "系统引擎不可用，跳过");
            return
        }
        val results = mutableListOf<String>()
        val states = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val gotResult = CountDownLatch(1)
        val gotListening = CountDownLatch(1)
        inst.runOnMainSync {
            backend.setCallbacks(
                onResult = { text -> results.add(text); android.util.Log.i("SysResult", "onResult: " + text); gotResult.countDown() },
                onPartialResult = { text -> android.util.Log.i("SysResult", "partial: " + text) },
                onStateChange = { s -> states.add(s.name); android.util.Log.i("SysResult", "state: " + s); if (s == RecognitionState.LISTENING) gotListening.countDown() },
                onError = { e -> errors.add(e); android.util.Log.i("SysResult", "error: " + e) },
            )
        }
        val ok = backend.start()
        android.util.Log.i("SysResult", "start=" + ok)
        val listening = gotListening.await(3, TimeUnit.SECONDS)
        android.util.Log.i("SysResult", "gotListening=" + listening)
        // 等 8 秒让系统引擎完成识别
        val hasResult = gotResult.await(8, TimeUnit.SECONDS)
        android.util.Log.i("SysResult", "hasResult=" + hasResult + " results=" + results + " states=" + states + " errors=" + errors)
        backend.cancel(); backend.release()
    }
}
