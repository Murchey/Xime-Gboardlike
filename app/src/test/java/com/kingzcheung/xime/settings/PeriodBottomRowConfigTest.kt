package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File

/** 手机底行新增句号键、句号多符号气泡与逗号长按入口的配置回归。 */
class PeriodBottomRowConfigTest {

    private val yaml: String by lazy { File("src/main/assets/xime.yaml").readText() }

    private val chineseSymbols = listOf(
        "^_^", ":)", ":(", ":D", ":P", "&", "$", "^",
        "%", "_", "：", "；", "、", "！", "……", "？",
    )

    private val englishSymbols = listOf(
        "&", "%", "+", "\"", "-", ":", "'", "@",
        ";", "/", "(", ")", "#", "!", ",", "?",
    )

    private fun row(section: String): List<String> =
        KeysConfigHelper.parseKeyboardLayoutYamlText(yaml, section)!!.last()

    private fun keys(section: String): Map<String, KeyBinding> =
        KeysConfigHelper.parseKeyboardYamlSection(yaml, section)!!

    private fun longPressLabels(binding: KeyBinding): List<String> =
        binding.longPress!!.values.map { it.label.ifEmpty { it.value } }

    @Test
    fun `中英文手机底行都是 问号123 逗号 地球 空格 句号 回车`() {
        val expected = listOf("mode_change", "comma", "earth", "space", "period", "enter")
        assertEquals(expected, row("qwerty"))
        assertEquals(expected, row("qwerty_en"))
    }

    @Test
    fun `中文句号长按是两行八个加八个符号`() {
        assertEquals(chineseSymbols, longPressLabels(keys("qwerty")["period"]!!))
        assertEquals(16, chineseSymbols.size)
    }

    @Test
    fun `中文句号长按默认选中第二行倒数第二个`() {
        for (section in listOf("qwerty", "qwerty_14", "qwerty_17", "qwerty_18")) {
            val lp = keys(section)["period"]!!.longPress!!
            assertEquals("$section default_index", 14, lp.defaultIndex)
            assertEquals("$section default value", "……", longPressLabels(keys(section)["period"]!!)[14])
        }
    }

    @Test
    fun `英文句号长按是两行八个加八个符号`() {
        assertEquals(englishSymbols, longPressLabels(keys("qwerty_en")["period"]!!))
        assertEquals(16, englishSymbols.size)
    }

    @Test
    fun `逗号长按依次是表情 悬浮模式 设置`() {
        for (section in listOf("qwerty", "qwerty_en")) {
            val values = keys(section)["comma"]!!.longPress!!.values
            assertNotNull(values)
            assertEquals(listOf("表情", "悬浮模式", "设置"), values.map { it.label })
            assertEquals(
                listOf(GestureAction.SWITCH_ROUTE, GestureAction.TOGGLE_FLOATING, GestureAction.OPEN_SETTINGS),
                values.map { it.action },
            )
            assertEquals("emoji", values[0].value)
        }
    }

    @Test
    fun `地球长按直接弹输入法选择器且不显示气泡`() {
        for (section in listOf("qwerty", "qwerty_en")) {
            val values = keys(section)["earth"]!!.longPress!!.values
            assertEquals(1, values.size)
            assertEquals(GestureAction.COMMAND, values[0].action)
            assertEquals("show_ime_picker", values[0].value)
            assertFalse("地球长按不应再显示文字气泡", values[0].bubble)
        }
    }

    @Test
    fun `英文逗号与句号分开`() {
        val comma = keys("qwerty_en")["comma"]!!.tap!!
        val period = keys("qwerty_en")["period"]!!.tap!!
        assertEquals(",", comma.label)
        assertEquals(",", comma.value)
        assertEquals(".", period.label)
        assertEquals(".", period.value)
    }
}
