package com.kingzcheung.xime.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelHeightTest {

    @Test
    fun `期望高度放得下时取期望值`() {
        // 屏 800dp - 键盘 300dp - 输入框预留 120dp = 380dp 预算
        assertEquals(
            300,
            emojiPanelBrowseHeightDp(
                desiredDp = 300, screenHeightDp = 800, keyboardHeightDp = 300,
                minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
            ),
        )
    }

    @Test
    fun `期望高度超预算时压到预算`() {
        // 屏 800 - 键盘 400 - 预留 120 = 280 预算，期望 360 应被压到 280
        assertEquals(
            280,
            emojiPanelBrowseHeightDp(
                desiredDp = 360, screenHeightDp = 800, keyboardHeightDp = 400,
                minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
            ),
        )
    }

    @Test
    fun `预算不足时保住最小高度`() {
        // 屏 800 - 键盘 700 - 预留 120 为负，仍要保住最小高度
        val height = emojiPanelBrowseHeightDp(
            desiredDp = 360, screenHeightDp = 800, keyboardHeightDp = 700,
            minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
        )
        assertEquals(EMOJI_PANEL_MIN_HEIGHT_DP, height)
        assertTrue(height > 0)
    }

    @Test
    fun `搜索态高度明显小于浏览态`() {
        assertTrue(EMOJI_PANEL_SEARCH_HEIGHT_DP < 300)
        assertTrue(EMOJI_PANEL_BROWSE_HEIGHT_RATIO > 0f && EMOJI_PANEL_BROWSE_HEIGHT_RATIO < 1f)
    }
}
