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
        assertEquals(Color(0xFFB08D00), theme.accentLight)
        assertTrue(theme.accentLight.luminance() < theme.accentDark.luminance())
        assertEquals(Color(0xFF000000), theme.keyboardBgDark)
        assertEquals(Color(0xFF201E18), theme.keyBgDark)
        // 功能键用暗金容器，避免整键高饱和明黄在暗光下发刺
        assertEquals(Color(0xFF4A3C12), theme.specialKeyDark)
        assertEquals(Color(0xFFFFECA8), theme.keyTextColorDark)
        assertEquals(Color(0xFFA9A392), theme.candidateTextColorDark)
        assertEquals(Color(0xFFFFFFFF), theme.keyboardBgLight)
        assertEquals(Color(0xFFE9EAEC), theme.keyBgLight)
        assertEquals(Color(0xFFF2DE63), theme.specialKeyLight)
        assertEquals(Color(0xFF45484D), theme.candidateTextColorLight)
    }

    @Test
    fun `ChatGPT 主题为深蓝与浅蓝`() {
        val theme = KeyboardThemes.getThemeById("chatgpt")
        assertEquals(Color(0xFF0B2E59), theme.accentLight)
        assertEquals(Color(0xFFC8D8EE), theme.accentDark)
        assertEquals(Color(0xFF0A1220), theme.keyboardBgDark)
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
                    KeyboardThemes.contrastRatio(theme.keyBgLight, theme.keyboardBgLight) >= 1.11f
                )
                assertTrue(
                    "${theme.id} 深色键面与底色过于接近",
                    KeyboardThemes.contrastRatio(theme.keyBgDark, theme.keyboardBgDark) >= 1.2f
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
