package com.kingzcheung.xime.settings

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.keyboard.GestureAction
import com.kingzcheung.xime.ui.keyboard.SymbolHintRuntime
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.Mock
import org.mockito.junit.MockitoJUnitRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.whenever

/**
 * 长按动作按符号排布风格取列表：两套风格都必须能拿到大小写/注音变体，
 * 且 Gboard 风格的符号仍在第一项。
 */
@RunWith(MockitoJUnitRunner.Silent::class)
class LetterLongPressStyleTest {

    @Mock private lateinit var mockContext: Context
    @Mock private lateinit var mockPrefs: SharedPreferences

    private fun action(value: String, label: String = value) =
        KeyAction(action = GestureAction.COMMIT, value = value, label = label)

    private fun binding(swipeUp: String, vararg values: String) = KeyBinding(
        tap = KeyAction(action = GestureAction.SEND_RIME, value = "x", label = "x"),
        longPress = LongPressAction(
            display = DisplayMode.BUBBLE,
            values = values.map { action(it) },
        ),
        swipeUp = action(swipeUp),
    )

    @Before
    fun setup() {
        whenever(mockContext.getSharedPreferences(any(), anyInt())).thenReturn(mockPrefs)
        // 兜底在前、具体键在后：Mockito 以最后匹配的 stub 为准
        whenever(mockPrefs.getString(any(), any())).thenAnswer { it.getArgument(1) }
        whenever(mockPrefs.getBoolean(any(), any<Boolean>())).thenAnswer { it.getArgument(1) }
        SymbolHintRuntime.appContext = mockContext
        KeysConfigHelper.setKeyGestureConfigForTest(
            mapOf(
                "f" to binding("-", "-", "f"),
                "q" to binding("1", "q", "Q"),
                "a" to binding("@", "a", "A"),
                "ab" to binding("*", "a", "b", "A", "B"),
            )
        )
    }

    @After
    fun tearDown() {
        SymbolHintRuntime.appContext = null
    }

    private fun useStyle(style: String) {
        whenever(mockPrefs.getString(eq(SettingsPreferences.KEY_SYMBOL_HINT_STYLE), any()))
            .thenReturn(style)
    }

    @Test
    fun `fcitx5风格_f长按首项是半角减号并带大小写`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_FCITX5)
        val lp = KeysConfigHelper.longPressForStyle("f")!!
        assertEquals("-", lp.values.first().label)
        val labels = lp.values.map { it.label }
        assertTrue(labels.contains("F"))
        assertTrue(labels.contains("f"))
    }

    @Test
    fun `fcitx5风格_首项是字母本身时换成符号`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_FCITX5)
        val q = KeysConfigHelper.longPressForStyle("q")!!
        assertEquals("1", q.values.first().label)
        assertTrue(q.values.map { it.label }.contains("Q"))
    }

    @Test
    fun `fcitx5风格_元音带注音变体`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_FCITX5)
        val labels = KeysConfigHelper.longPressForStyle("a")!!.values.map { it.label }
        assertTrue(labels.containsAll(listOf("A", "á", "ǎ", "à", "ā")))
    }

    @Test
    fun `gboard风格_符号在首位且保留大小写与注音`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_GBOARD)
        val q = KeysConfigHelper.longPressForStyle("q")!!
        assertEquals("1", q.values.first().label)
        assertTrue(q.values.map { it.label }.contains("Q"))

        val a = KeysConfigHelper.longPressForStyle("a")!!
        assertEquals("@", a.values.first().label)
        assertTrue(a.values.map { it.label }.containsAll(listOf("A", "á", "ǎ")))
    }

    @Test
    fun `合并键长按首项是上滑符号且不追加单字母变体`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_FCITX5)
        val lp = KeysConfigHelper.longPressForStyle("ab")!!
        // 首项决定中文模式的键面角标：必须与单字母键一样是符号，而不是组内字母
        assertEquals(listOf("*", "a", "b", "A", "B"), lp.values.map { it.label })
        assertFalse(lp.values.any { it.label == "á" })
    }

    @Test
    fun `gboard风格_合并键长按首项是首字母的符号`() {
        useStyle(SettingsPreferences.SYMBOL_HINT_GBOARD)
        val lp = KeysConfigHelper.longPressForStyle("ab")!!
        assertEquals("@", lp.values.first().label)
    }
}