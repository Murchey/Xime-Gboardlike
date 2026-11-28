package com.kingzcheung.xime.ui.emoji

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EmojiPanelHeightTest {

    @Test
    fun `期望高度放得下时取期望值`() {
        // 屏 800dp - 输入框预留 120dp = 680dp 预算
        assertEquals(
            440,
            emojiPanelBrowseHeightDp(
                desiredDp = 440, screenHeightDp = 800,
                minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
            ),
        )
    }

    @Test
    fun `期望高度超预算时压到预算`() {
        // 屏 240 - 预留 120 = 120 预算，下限 200 生效 → 200
        assertEquals(
            200,
            emojiPanelBrowseHeightDp(
                desiredDp = 240, screenHeightDp = 240,
                minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
            ),
        )
    }

    @Test
    fun `小屏仍保住最小高度并给输入框留位置`() {
        val height = emojiPanelBrowseHeightDp(
            desiredDp = 500, screenHeightDp = 600,
            minHeightDp = EMOJI_PANEL_MIN_HEIGHT_DP,
        )
        assertEquals(480, height)
        assertTrue("必须给宿主输入框留下空间", 600 - height >= EMOJI_HOST_INPUT_RESERVE_DP)
    }

    @Test
    fun `搜索态高度明显小于浏览态`() {
        assertTrue(EMOJI_PANEL_SEARCH_HEIGHT_DP < 300)
        assertTrue(EMOJI_PANEL_BROWSE_HEIGHT_RATIO > 0f && EMOJI_PANEL_BROWSE_HEIGHT_RATIO < 1f)
    }
}
