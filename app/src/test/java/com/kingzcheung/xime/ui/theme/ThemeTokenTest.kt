package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import com.kingzcheung.xime.ui.keyboard.keyPressedColor
import com.kingzcheung.xime.ui.keyboard.pressedKeyColor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 主题可选 token 的默认行为：
 * 1) 旧主题未配置时返回 null（渲染层回退到既有推导，零影响）；
 * 2) Pixel / AMOLED 两套主题在明暗模式下都提供完整 token；
 * 3) 按压高亮以"叠加层"方式合成到键帽本色上，而不是直接替换。
 */
class ThemeTokenTest {

    private val legacyIds = listOf("gboard_gray", "gboard_black", "wechat")

    @Test
    fun `旧主题未配置可选 token 时返回 null`() {
        for (id in legacyIds) {
            for (isDark in listOf(false, true)) {
                assertNull(KeyboardThemes.getKeyPressHighlightColor(id, isDark))
                assertNull(KeyboardThemes.getAccentKeyBackground(id, isDark))
                assertNull(KeyboardThemes.getAccentKeyTextColor(id, isDark))
                assertNull(KeyboardThemes.getPopupBackground(id, isDark))
                assertNull(KeyboardThemes.getPopupTextColor(id, isDark))
                assertNull(KeyboardThemes.getCandidateCommentColor(id, isDark))
            }
        }
    }

    @Test
    fun `Pixel 与 AMOLED 提供完整可选 token`() {
        for (id in listOf("pixel", "amoled")) {
            for (isDark in listOf(false, true)) {
                assertNotNull("$id 按压高亮缺失", KeyboardThemes.getKeyPressHighlightColor(id, isDark))
                assertNotNull("$id 强调键底色缺失", KeyboardThemes.getAccentKeyBackground(id, isDark))
                assertNotNull("$id 强调键文字缺失", KeyboardThemes.getAccentKeyTextColor(id, isDark))
                assertNotNull("$id 气泡底色缺失", KeyboardThemes.getPopupBackground(id, isDark))
                assertNotNull("$id 气泡文字缺失", KeyboardThemes.getPopupTextColor(id, isDark))
                assertNotNull("$id 候选注释缺失", KeyboardThemes.getCandidateCommentColor(id, isDark))
            }
        }
    }

    @Test
    fun `按压高亮以叠加层合成而不是直接替换`() {
        val base = Color(0xFFE8F0FE)
        // 未配置主题色：与旧的亮度推导结果一致
        assertEquals(keyPressedColor(base), pressedKeyColor(base, null))
        // 配置半透明高亮：按叠加层合成，保留键帽自身色相（与"直接替换"不同）
        val highlight = Color(0x1F000000)
        assertEquals(highlight.compositeOver(base), pressedKeyColor(base, highlight))
        assertNotEquals(highlight, pressedKeyColor(base, highlight))
    }
}
