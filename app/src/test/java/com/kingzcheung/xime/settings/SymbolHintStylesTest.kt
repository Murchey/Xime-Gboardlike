package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Gboard 符号提示表。 */
class SymbolHintStylesTest {

    @Test
    fun `gboard 表覆盖 26 个字母`() {
        val style = SettingsPreferences.SYMBOL_HINT_GBOARD
        ('a'..'z').forEach { c ->
            val hint = SymbolHintStyles.hintFor(style, c.toString())
            org.junit.Assert.assertNotNull("缺 " + c, hint)
        }
        assertEquals("1", SymbolHintStyles.hintFor(style, "q"))
        assertEquals("0", SymbolHintStyles.hintFor(style, "p"))
        assertEquals("@", SymbolHintStyles.hintFor(style, "a"))
        assertEquals("#", SymbolHintStyles.hintFor(style, "s"))
        assertEquals("$" + "", SymbolHintStyles.hintFor(style, "d"))
        assertEquals("?", SymbolHintStyles.hintFor(style, "m"))
    }

    @Test
    fun `fcitx5 风格不改动`() {
        assertNull(SymbolHintStyles.hintFor(SettingsPreferences.SYMBOL_HINT_FCITX5, "s"))
    }
}
