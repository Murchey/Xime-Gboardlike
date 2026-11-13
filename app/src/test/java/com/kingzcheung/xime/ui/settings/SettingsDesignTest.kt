package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 设置卡片的分隔线与行高规则（纯函数）。 */
class SettingsDesignTest {

    @Test
    fun noDividerAfterLastRow() {
        assertEquals(listOf(72, 144), dividerOffsets(listOf(72, 72, 72)))
        assertEquals(emptyList<Int>(), dividerOffsets(listOf(72)))
        assertEquals(emptyList<Int>(), dividerOffsets(emptyList()))
    }

    @Test
    fun dividersAccumulateRowHeights() {
        // 行高不同也要落在每行底部
        assertEquals(listOf(56, 128), dividerOffsets(listOf(56, 72, 60)))
    }

    @Test
    fun twoLineRowIsTallerThanSingleLine() {
        assertEquals(72, settingsRowMinHeightDp(hasSummary = true))
        assertEquals(56, settingsRowMinHeightDp(hasSummary = false))
    }
}
