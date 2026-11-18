package com.kingzcheung.xime.service

import android.content.Context
import android.content.SharedPreferences
import android.os.Handler
import android.view.inputmethod.InputConnection
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.SpeechRecognitionManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.Mock
import org.mockito.Mockito.never
import org.mockito.Mockito.verify
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * 「正在识别…」阶段再次按下 → 立即收尾并恢复键盘（finishPendingNow）的状态机测试。
 *
 * 背景：在线引擎松手后要等最终结果（1~2s，超时 3s），这期间语音面板不退出、
 * 盖住整个键盘。用户紧接着说下一句的按住落不到空格键上，真机表现为"再按没反应/只能用一次"。
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class VoiceRecognitionHandlerFinishNowTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockPrefs: SharedPreferences
    @Mock private lateinit var mockInputConnection: InputConnection
    @Mock private lateinit var mockManager: SpeechRecognitionManager
    @Mock private lateinit var mockMainHandler: Handler

    private lateinit var handler: VoiceRecognitionHandler
    private var voiceCompleteCount = 0

    private lateinit var onPartial: (String) -> Unit

    @Before
    fun setup() {
        whenever(mockContext.getSharedPreferences(any(), anyInt())).thenReturn(mockPrefs)
        whenever(mockContext.applicationContext).thenReturn(mockContext)
        whenever(mockMainHandler.postDelayed(any<Runnable>(), anyLong())).thenReturn(true)
        whenever(mockPrefs.getBoolean(any(), any<Boolean>())).thenAnswer { it.getArgument(1) }
        whenever(mockPrefs.getString(any(), any())).thenAnswer { it.getArgument(1) }
        whenever(mockInputConnection.getTextBeforeCursor(anyInt(), anyInt()))
            .thenReturn("这是一次测试")

        handler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = {},
            getState = { InputUIState() },
            getInputConnection = { mockInputConnection },
            onVoiceComplete = { voiceCompleteCount++ },
            managerFactory = { mockManager },
            mainHandler = mockMainHandler
        )
        handler.initialize()

        val onPartialCaptor = argumentCaptor<(String) -> Unit>()
        verify(mockManager).setCallbacks(
            any(), onPartialCaptor.capture(), any(), any(), any(), any()
        )
        onPartial = onPartialCaptor.firstValue
    }

    @Test
    fun `没有待收尾的会话时按下不处理`() {
        assertFalse(handler.finishPendingNow())
        verify(mockManager, never()).cancelInFlightSession()
        assertEquals(0, voiceCompleteCount)
    }

    @Test
    fun `收尾等待中按下会提交已识别文本并恢复键盘`() {
        onPartial.invoke("这是一次测试")
        handler.finishRecognition()

        assertTrue(handler.finishPendingNow())

        // 已识别的部分结果按增量提交（partial 已上屏，只补标点）
        verify(mockInputConnection).finishComposingText()
        // 旧会话被关掉，避免收尾结束后又冒出一句
        verify(mockManager).cancelInFlightSession()
        // 键盘/面板恢复（宿主收到完成回调）
        assertEquals(1, voiceCompleteCount)
        // 不在这次按下里直接开新录音（避免触摸流被打断后麦克风不关）
        verify(mockManager, never()).startRecognition()
    }

    @Test
    fun `面板残留语音态时按下也能恢复`() {
        val stuckHandler = VoiceRecognitionHandler(
            context = mockContext,
            onStateChanged = {},
            getState = { InputUIState(isVoiceMode = true) },
            getInputConnection = { mockInputConnection },
            onVoiceComplete = { voiceCompleteCount++ },
            managerFactory = { mockManager },
            mainHandler = mockMainHandler,
        )
        stuckHandler.initialize()

        assertTrue(stuckHandler.finishPendingNow())
        verify(mockManager).cancelInFlightSession()
        assertEquals(1, voiceCompleteCount)
    }

    @Test
    fun `无已识别文本时按下不处理`() {
        handler.finishRecognition()
        assertFalse(handler.finishPendingNow())
    }
}