package com.kingzcheung.xime.ui.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 「强调第一个候选」开关必须能反映到主题预览。
 *
 * 回归背景：预览曾硬编码 isSelected = true，且编辑器调用预览时漏传开关值，
 * 导致来回切换开关预览完全不变。
 */
class ThemePreviewHighlightTest {

    @Test
    fun `开关关闭时首个候选不显示为选中`() {
        assertFalse(previewFirstCandidateSelected(highlightFirstCandidate = false))
    }

    @Test
    fun `开关开启时首个候选显示为选中`() {
        assertTrue(previewFirstCandidateSelected(highlightFirstCandidate = true))
    }
}
