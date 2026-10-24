package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {

    @Test
    fun `内置主题包含现代品牌主题`() {
        val expected = listOf(
            "gboard_gray", "gboard_black", "wechat", "qq", "telegram",
            "x", "github", "chatgpt", "claude",
        )
        val ids = KeyboardThemes.themes.map { it.id }
        assertTrue(ids.containsAll(expected))
        assertEquals(ids.size, ids.toSet().size)
    }

    @Test
    fun `每套主题都提供不同的明暗背景`() {
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertNotEquals(theme.keyboardBgLight, theme.keyboardBgDark)
            }
    }
}
