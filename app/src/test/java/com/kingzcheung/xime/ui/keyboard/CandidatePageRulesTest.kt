package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.rime.RimeCandidate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 展开页纯规则：数据源切换、自动收起、候选栏全局索引。 */
class CandidatePageRulesTest {

    private fun candidate(text: String) = RimeCandidate(text = text, comment = "")

    @Test
    fun `悬浮模式展开页宽度优先按卡片宽`() {
        // 悬浮卡片渲染在父容器内，即使有实测宽也必须按卡片宽算，否则候选被裁成细条
        assertEquals(349, CandidatePageRules.expandedPageWidthDp(true, 349, 900, 1080))
        // 卡片宽异常时退化为 1，避免后续除零
        assertEquals(1, CandidatePageRules.expandedPageWidthDp(true, 0, 900, 1080))
    }

    @Test
    fun `非悬浮优先实测宽，首帧回退屏宽`() {
        assertEquals(900, CandidatePageRules.expandedPageWidthDp(false, 349, 900, 1080))
        assertEquals(1080, CandidatePageRules.expandedPageWidthDp(false, 349, 0, 1080))
    }

    @Test
    fun `网格列数：无注释 4-5 列，有注释 3-4 列`() {
        // 无注释：手机起步 4 列、宽屏封顶 5 列
        assertEquals(4, CandidatePageRules.gridColumnsFor(360, showComments = false))
        assertEquals(5, CandidatePageRules.gridColumnsFor(560, showComments = false))
        assertEquals(5, CandidatePageRules.gridColumnsFor(1000, showComments = false))
        // 有关注：每格更宽，列数压到 3-4
        assertEquals(3, CandidatePageRules.gridColumnsFor(360, showComments = true))
        assertEquals(4, CandidatePageRules.gridColumnsFor(560, showComments = true))
        assertEquals(4, CandidatePageRules.gridColumnsFor(1000, showComments = true))
    }

    @Test
    fun `仅悬浮模式右栏等分压缩`() {
        assertTrue(CandidatePageRules.rightRailEqualSplit(true))
        assertFalse(CandidatePageRules.rightRailEqualSplit(false))
    }

    @Test
    fun `展开且全量候选非空才切换数据源`() {
        assertFalse(CandidatePageRules.usesExpandedData(expanded = false, expandedCount = 5))
        assertFalse(CandidatePageRules.usesExpandedData(expanded = true, expandedCount = 0))
        assertTrue(CandidatePageRules.usesExpandedData(expanded = true, expandedCount = 3))
    }

    @Test
    fun `候选与联想都空才自动收起`() {
        assertTrue(CandidatePageRules.shouldAutoCollapse(true, 0, 0))
        // 未展开时无意义
        assertFalse(CandidatePageRules.shouldAutoCollapse(false, 0, 0))
        // 仍有候选或联想时保持展开
        assertFalse(CandidatePageRules.shouldAutoCollapse(true, 1, 0))
        assertFalse(CandidatePageRules.shouldAutoCollapse(true, 0, 1))
    }

    @Test
    fun `展开态候选栏保留全局索引并支持单字筛选`() {
        val all = listOf(candidate("你"), candidate("你好"), candidate("世"))
        assertEquals(
            listOf(0 to "你", 1 to "你好", 2 to "世"),
            CandidatePageRules.railItems(true, singleCharOnly = false, all)
                .map { it.first to it.second.text },
        )
        assertEquals(
            listOf(0 to "你", 2 to "世"),
            CandidatePageRules.railItems(true, singleCharOnly = true, all)
                .map { it.first to it.second.text },
        )
        assertEquals(emptyList<Pair<Int, String>>(), CandidatePageRules.railItems(false, false, all))
    }
}
