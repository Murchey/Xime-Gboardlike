package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ThemeCatalogTest {

    @Test
    fun `内置主题包含现代品牌主题`() {
        val expected = listOf(
            "gboard_gray", "gboard_black", "wechat", "qq", "endfield",
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

    @Test
    fun `候选文字与选中文字对背景保持可读对比度`() {
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertTrue(
                    "${theme.id} 浅色候选文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.candidateTextColorLight, theme.keyboardBgLight) >= 4.5f
                )
                assertTrue(
                    "${theme.id} 深色候选文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.candidateTextColorDark, theme.keyboardBgDark) >= 4.5f
                )
                assertTrue(
                    "${theme.id} 浅色选中文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.candidateSelectedTextColorLight, theme.keyboardBgLight) >= 4.5f
                )
                assertTrue(
                    "${theme.id} 深色选中文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.candidateSelectedTextColorDark, theme.keyboardBgDark) >= 4.5f
                )
            }
    }

    @Test
    fun `终末地使用精调深浅色`() {
        val theme = KeyboardThemes.getThemeById("endfield")
        assertEquals(Color(0xFFFFCC00), theme.accentDark)
        assertEquals(Color(0xFFC2A325), theme.accentLight)
        assertTrue(theme.accentLight.luminance() < theme.accentDark.luminance())
        assertEquals(Color(0xFF000000), theme.keyboardBgDark)
        assertEquals(Color(0xFF1E1D17), theme.keyBgDark)
        assertEquals(Color(0xFFBF9C00), theme.specialKeyDark)
        assertEquals(Color(0xFFFFFCEC), theme.keyTextColorDark)
        assertEquals(Color(0xFFBBFBEB), theme.candidateTextColorDark)
        assertEquals(Color(0xFFFFFFFF), theme.keyboardBgLight)
        assertEquals(Color(0xFFE7E8EA), theme.keyBgLight)
        assertEquals(Color(0xFFF0D957), theme.specialKeyLight)
        assertEquals(Color(0xFF474A4F), theme.candidateTextColorLight)
    }

    @Test
    fun `ChatGPT 主题为深蓝与白色`() {
        val theme = KeyboardThemes.getThemeById("chatgpt")
        assertEquals(Color(0xFF0B2E59), theme.accentLight)
        assertEquals(Color(0xFFFFFFFF), theme.accentDark)
        assertEquals(Color(0xFF0A1220), theme.keyboardBgDark)
    }

    @Test
    fun `内置主题功能键取主题色调而不是统一灰`() {
        val wechat = KeyboardThemes.getThemeById("wechat")
        assertNotEquals(Color(0xFFE8EAED), wechat.specialKeyLight)
        assertNotEquals(Color(0xFFE8EAED), wechat.specialKeyDark)
        val gboard = KeyboardThemes.getThemeById("gboard_gray")
        assertNotEquals(Color(0xFFE8EAED), gboard.specialKeyLight)
    }
}
