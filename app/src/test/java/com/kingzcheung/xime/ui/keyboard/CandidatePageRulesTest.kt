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
