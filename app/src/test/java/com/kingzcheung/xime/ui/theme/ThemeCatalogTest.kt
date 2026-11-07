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
    fun `终末地使用白底与信号黄设计语言`() {
        val theme = KeyboardThemes.getThemeById("endfield")
        // 浅色：纯白键盘底 + 中性浅灰键面，信号黄只用于功能键
        assertEquals(Color(0xFFFFFFFF), theme.keyboardBgLight)
        assertEquals(Color(0xFFE7E8EA), theme.keyBgLight)
        assertEquals(Color(0xFFEED001), theme.specialKeyLight)
        // 深色：纯黑底 + 信号黄强调/功能键
        assertEquals(Color(0xFF000000), theme.keyboardBgDark)
        assertEquals(Color(0xFF38393B), theme.keyBgDark)
        assertEquals(Color(0xFFFFCC00), theme.accentDark)
        assertEquals(Color(0xFFBF9C00), theme.specialKeyDark)
        // 浅色强调保持深色可读，深色强调提亮
        assertTrue(theme.accentLight.luminance() < theme.accentDark.luminance())
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
                // 7:1 而非 9:1：Gboard 黑主题的实测键面为 #5C5C5E（白字约 6.8），
                // 为对齐 Gboard 观感放宽一档，仍高于 WCAG AA 的大字号标准。
                assertTrue(
                    "${theme.id} 浅色按键文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.keyTextColorLight, theme.keyBgLight) >= 7f
                )
                assertTrue(
                    "${theme.id} 深色按键文字对比度不足",
                    KeyboardThemes.contrastRatio(theme.keyTextColorDark, theme.keyBgDark) >= 7f
                )
            }
    }

    @Test
    fun `内置主题键面与键盘底色可分辨`() {
        // 阈值 1.10：白底纯净系主题（Gboard 黑/X/终末地，见 4033f1e6）刻意用近白键面，
        // 按键边界仅隐约可辨；其余主题实际 ≥1.25，此线只防"完全融在一起"。
        KeyboardThemes.themes
            .filterNot { it.isDynamic }
            .forEach { theme ->
                assertTrue(
                    "${theme.id} 浅色键面与底色过于接近",
                    KeyboardThemes.contrastRatio(theme.keyBgLight, theme.keyboardBgLight) >= 1.10f
                )
                assertTrue(
                    "${theme.id} 深色键面与底色过于接近",
                    KeyboardThemes.contrastRatio(theme.keyBgDark, theme.keyboardBgDark) >= 1.10f
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
