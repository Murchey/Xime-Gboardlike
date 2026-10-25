package com.kingzcheung.xime.settings

import com.kingzcheung.xime.keyboard.GestureAction
import com.kingzcheung.xime.ui.keyboard.prependAsciiLongPressSymbol
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 中文长按半角符号、符号面板斜杠与英文长按符号优先的配置回归。 */
class KeyboardSymbolConfigTest {

    private val yaml: String by lazy { File("src/main/assets/xime.yaml").readText() }

    private fun labels(section: String, key: String): List<String> =
        KeysConfigHelper.longPressDisplayItems(
            KeysConfigHelper.parseKeyboardYamlSection(yaml, section)!![key]!!.longPress!!
        )!!

    @Test
    fun `中文全键盘长按 ASCII 符号为半角`() {
        val expected = mapOf(
            "a" to "@", "s" to "*", "d" to "+", "f" to "-", "g" to "=",
            "h" to "/", "j" to "#",
        )
        for (section in listOf("qwerty", "qwerty_tablet")) {
            for ((key, symbol) in expected) {
                assertEquals("$section/$key", listOf(symbol), labels(section, key))
            }
        }
    }

    @Test
    fun `左右小括号跟随中英文模式`() {
        for (section in listOf("qwerty", "qwerty_tablet")) {
            assertEquals("$section/k", listOf("（"), labels(section, "k"))
            assertEquals("$section/l", listOf("）"), labels(section, "l"))
        }
        for (section in listOf("qwerty_en", "qwerty_en_tablet")) {
            val keys = KeysConfigHelper.parseKeyboardYamlSection(yaml, section)!!
            val left = keys["k"]!!.swipeUp!!
            val right = keys["l"]!!.swipeUp!!
            assertEquals("$section/k", "(", left.label.ifEmpty { left.value })
            assertEquals("$section/l", ")", right.label.ifEmpty { right.value })
        }
    }

    @Test
    fun `中文问号感叹号冒号波浪号使用中文标点`() {
        val expected = mapOf("x" to "：", "v" to "？", "b" to "！", "n" to "～")
        for (section in listOf("qwerty", "qwerty_tablet")) {
            for ((key, symbol) in expected) {
                assertEquals("$section/$key", listOf(symbol), labels(section, key))
            }
        }
    }

    @Test
    fun `中文长按的 ASCII 符号不再使用全角形式`() {
        val fullWidth = "＠＊＋－＝／＃"
        for (section in listOf("qwerty", "qwerty_tablet")) {
            val keys = KeysConfigHelper.parseKeyboardYamlSection(yaml, section)!!
            for (id in listOf("a", "s", "d", "f", "g", "h", "j")) {
                val values = keys[id]?.longPress?.values ?: continue
                for (item in values) {
                    val text = item.label.ifEmpty { item.value }
                    assertFalse("$section/$id 仍为全角: $text", text.length == 1 && text[0] in fullWidth)
                }
            }
        }
    }

    @Test
    fun `Z 长按为智能单引号`() {
        for (section in listOf("qwerty", "qwerty_tablet")) {
            val z = KeysConfigHelper.parseKeyboardYamlSection(yaml, section)!!["z"]!!
            val quote = z.longPress!!.values.single()
            assertEquals("‘’", quote.label)
            assertEquals(GestureAction.SMART_SINGLE_QUOTE, quote.action)
        }
    }

    @Test
    fun `符号面板斜杠与星号为半角`() {
        for (section in listOf("common_symbols", "common_symbols_tablet")) {
            val ids = KeysConfigHelper.parsePanelLayoutYamlText(yaml, section)!!.rows.flatten()
            assertTrue("$section 缺少 /", "/" in ids)
            assertFalse("$section 仍有全角 ／", "／" in ids)
            assertTrue("$section 缺少 *", "*" in ids)
            assertFalse("$section 仍有全角 ＊", "＊" in ids)
        }
    }

    @Test
    fun `英文模式长按符号插到首位并去重`() {
        assertEquals(
            listOf("1", "q", "Q"),
            prependAsciiLongPressSymbol(listOf("q", "Q"), "1"),
        )
        assertEquals(
            listOf("@", "a", "A"),
            prependAsciiLongPressSymbol(listOf("a", "@", "A"), "@"),
        )
        assertEquals(listOf("q", "Q"), prependAsciiLongPressSymbol(listOf("q", "Q"), null))
    }
}
