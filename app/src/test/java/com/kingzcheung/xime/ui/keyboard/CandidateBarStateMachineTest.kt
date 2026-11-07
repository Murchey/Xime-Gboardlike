package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选栏状态机分支与优先级：剪贴板 > 计算器 > 组合输入 > 仅联想 > 普通候选 > 空闲。
 * 这些分支直接决定候选栏渲染哪一态，锁定优先级可避免边缘场景"该显示候选却显示空闲"。
 */
class CandidateBarStateMachineTest {

    private fun from(
        candidates: List<String> = emptyList(),
        comments: List<String> = emptyList(),
        inputText: String = "",
        isComposing: Boolean = false,
        associations: List<String> = emptyList(),
        isShowingRecentClipboard: Boolean = false,
        hasNextPage: Boolean = false,
        isCalculatorActive: Boolean = false,
        preeditCaretPos: Int = -1,
    ) = CandidateBarState.from(
        candidates = candidates,
        candidateComments = comments,
        inputText = inputText,
        isComposing = isComposing,
        associationCandidates = associations,
        isShowingRecentClipboard = isShowingRecentClipboard,
        hasNextPage = hasNextPage,
        isCalculatorActive = isCalculatorActive,
        preeditCaretPos = preeditCaretPos,
    )

    @Test
    fun `剪贴板态优先于计算器与输入态`() {
        val state = from(
            candidates = listOf("剪贴板"),
            inputText = "hou",
            isComposing = true,
            isShowingRecentClipboard = true,
            isCalculatorActive = true,
        )
        assertTrue(state is CandidateBarState.ClipboardDisplay)
    }

    @Test
    fun `计算器态优先于输入态`() {
        val state = from(
            candidates = listOf("1+1"),
            inputText = "1+1",
            isComposing = true,
            isCalculatorActive = true,
        )
        assertTrue(state is CandidateBarState.Calculator)
    }

    @Test
    fun `组合态只有编码没有候选时仍是候选态`() {
        val state = from(inputText = "ni", isComposing = true)
        assertTrue(state is CandidateBarState.ChineseCandidates)
        val chinese = state as CandidateBarState.ChineseCandidates
        assertEquals(emptyList<String>(), chinese.candidates)
        assertEquals("ni", chinese.inputText)
    }

    @Test
    fun `非组合态有编码也进入候选态`() {
        val state = from(inputText = "hello")
        assertTrue(state is CandidateBarState.ChineseCandidates)
    }

    @Test
    fun `仅联想词时进入联想态`() {
        val state = from(associations = listOf("世界"), hasNextPage = true)
        assertTrue(state is CandidateBarState.AssociationOnly)
        val assoc = state as CandidateBarState.AssociationOnly
        assertEquals(listOf("世界"), assoc.candidates)
        assertEquals(true, assoc.hasMore)
    }

    @Test
    fun `全部为空时进入空闲态`() {
        assertTrue(from() is CandidateBarState.Idle)
    }

    @Test
    fun `hasMore 仅在存在候选时为真`() {
        // 有候选 + 有下一页
        val withCandidates = from(candidates = listOf("你"), inputText = "ni", isComposing = true, hasNextPage = true)
        assertEquals(true, (withCandidates as CandidateBarState.ChineseCandidates).hasMore)
        // 只有编码（无候选）时即便引擎说有下一页也不该启用展开
        val inputOnly = from(inputText = "ni", isComposing = true, hasNextPage = true)
        assertEquals(false, (inputOnly as CandidateBarState.ChineseCandidates).hasMore)
    }

    @Test
    fun `注释与编码光标透传到候选态`() {
        val state = from(
            candidates = listOf("你"),
            comments = listOf("ni"),
            inputText = "ni",
            isComposing = true,
            preeditCaretPos = 2,
        )
        val chinese = state as CandidateBarState.ChineseCandidates
        assertEquals(listOf("ni"), chinese.comments)
        assertEquals(2, chinese.preeditCaretPos)
    }
}
