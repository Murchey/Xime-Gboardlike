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
    fun `终末地使用设计语言纸墨色系`() {
        val theme = KeyboardThemes.getThemeById("endfield")
        // 信号黄 #FFCC00 只做强调，浅色用深金保证可读
        assertEquals(Color(0xFFFFCC00), theme.accentDark)
        assertEquals(Color(0xFF947600), theme.accentLight)
        assertTrue(theme.accentLight.luminance() < theme.accentDark.luminance())
        // 中性偏绿灰，不偏咖啡色
        assertEquals(Color(0xFF0A0B0A), theme.keyboardBgDark)
        assertEquals(Color(0xFF2A2C29), theme.keyBgDark)
        assertEquals(Color(0xFF3C3A10), theme.specialKeyDark)
        assertEquals(Color(0xFFE8E8E2), theme.keyTextColorDark)
        assertEquals(Color(0xFFA8A8A0), theme.candidateTextColorDark)
        assertEquals(Color(0xFFD8D8D2), theme.keyboardBgLight)
        assertEquals(Color(0xFFFFFFFF), theme.keyBgLight)
        assertEquals(Color(0xFFF5F0A0), theme.specialKeyLight)
        assertEquals(Color(0xFF5A5A52), theme.candidateTextColorLight)
    }

    @Test
    fun `ChatGPT 主题使用品牌蓝强调`() {
        val theme = KeyboardThemes.getThemeById("chatgpt")
        assertEquals(Color(0xFF006EFE), theme.accentLight)
        assertEquals(Color(0xFF006EFE), theme.accentDark)
        assertEquals(Color(0xFF080A0C), theme.keyboardBgDark)
    }

    @Test
    fun `内置主题按键文字与键面保持高对比度`() {
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertTrue(
                    "${theme.id} 浅色按键文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.keyTextColorLight, theme.keyBgLight) >= 9f
                )
                assertTrue(
                    "${theme.id} 深色按键文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.keyTextColorDark, theme.keyBgDark) >= 9f
                )
            }
    }

    @Test
    fun `内置主题键面与键盘底色可分辨`() {
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertTrue(
                    "${theme.id} 浅色键面与底色过于接近",
                    KeyboardThemes.contrastRatio(theme.keyBgLight, theme.keyboardBgLight) >= 1.25f
                )
                assertTrue(
                    "${theme.id} 深色键面与底色过于接近",
                    KeyboardThemes.contrastRatio(theme.keyBgDark, theme.keyboardBgDark) >= 1.25f
                )
            }
    }

    @Test
    fun `强调色在键盘底色上可见`() {
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertTrue(
                    "${theme.id} 浅色强调色对比度不足",
                    KeyboardThemes.contrastRatio(theme.accentLight, theme.keyboardBgLight) >= 3f
                )
                assertTrue(
                    "${theme.id} 深色强调色对比度不足",
                    KeyboardThemes.contrastRatio(theme.accentDark, theme.keyboardBgDark) >= 3f
                )
            }
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
