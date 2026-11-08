package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Gboard 系列主题的候选栏约定：
 * 1) 不强调第一个候选词（highlightFirstCandidate = false）；
 * 2) 候选文字按背景对比取纯黑/纯白，不用灰色；
 * 3) 其他主题不受影响。
 */
class GboardCandidateStyleTest {

    private val gboardIds = listOf("gboard_gray", "gboard_black")

    @Test
    fun `Gboard 主题不强调第一个候选词`() {
        for (id in gboardIds) {
            val theme = KeyboardThemes.getThemeById(id)
            assertFalse("$id 应关闭首候选强调", theme.highlightFirstCandidate)
        }
    }

    @Test
    fun `非 Gboard 主题仍强调第一个候选词`() {
        // 抽查几个非 Gboard 主题，确认没有误伤
        for (id in listOf("wechat", "x", "github", "material_you")) {
            val theme = KeyboardThemes.getThemeById(id)
            assertTrue("$id 应保留首候选强调", theme.highlightFirstCandidate)
        }
    }

    @Test
    fun `浅色 Gboard 候选文字为纯黑`() {
        for (id in gboardIds) {
            val theme = KeyboardThemes.getThemeById(id)
            assertEquals("$id 浅色候选文字应为纯黑", Color(0xFF000000), theme.candidateTextColorLight)
        }
    }

    @Test
    fun `深色 Gboard 候选文字为纯白`() {
        for (id in gboardIds) {
            val theme = KeyboardThemes.getThemeById(id)
            assertEquals("$id 深色候选文字应为纯白", Color(0xFFFFFFFF), theme.candidateTextColorDark)
        }
    }

    @Test
    fun `候选文字与背景对比度达到可读标准`() {
        for (id in gboardIds) {
            val theme = KeyboardThemes.getThemeById(id)
            assertTrue(
                "$id 浅色候选对比度不足",
                KeyboardThemes.contrastRatio(theme.candidateTextColorLight, theme.keyboardBgLight) >= 4.5f,
            )
            assertTrue(
                "$id 深色候选对比度不足",
                KeyboardThemes.contrastRatio(theme.candidateTextColorDark, theme.keyboardBgDark) >= 4.5f,
            )
        }
    }
}
