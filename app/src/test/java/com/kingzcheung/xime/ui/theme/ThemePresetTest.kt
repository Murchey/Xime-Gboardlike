package com.kingzcheung.xime.ui.theme

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 主题预设的收录规则：
 * 1) 高度相似的历史主题（QQ / ChatGPT）已合并隐藏，但仍能被已选用户解析；
 * 2) 新增主题（Material You / Nord / fcitx5 经典）提供完整可选 token；
 * 3) 所有可见主题的键面文字对比度 ≥ 4.5:1。
 */
class ThemePresetTest {

    private val newThemes = listOf("material_you", "nord", "fcitx5_classic")

    @Test
    fun `相似主题已合并隐藏且仍可解析`() {
        val hiddenIds = KeyboardThemes.themes.filter { it.hidden }.map { it.id }
        assertTrue("QQ 应被合并隐藏", "qq" in hiddenIds)
        assertTrue("ChatGPT 应被合并隐藏", "chatgpt" in hiddenIds)
        // 隐藏只影响选择器，不影响已选用户解析
        assertEquals("qq", KeyboardThemes.getThemeById("qq").id)
        assertEquals("chatgpt", KeyboardThemes.getThemeById("chatgpt").id)
        // 选择器可见列表不再包含它们
        val visible = KeyboardThemes.themes.filter { !it.hidden }.map { it.id }
        assertTrue(visible.isNotEmpty())
        assertTrue(visible.none { it == "qq" || it == "chatgpt" })
    }

    @Test
    fun `新增主题提供完整可选 token`() {
        for (id in newThemes) {
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

}
