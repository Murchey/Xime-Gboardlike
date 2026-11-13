package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.service.CandidateState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滑动解码 loading 条的状态链路：
 * CandidateState.isGlideDecoding → CandidateBarState.ChineseCandidates.isLoading。
 */
class GlideDecodingIndicatorTest {

    private fun barState(
        isLoading: Boolean,
        candidates: List<String> = listOf("你好"),
        inputText: String = "nihao",
        isComposing: Boolean = true,
    ): CandidateBarState = CandidateBarState.from(
        candidates = candidates,
        candidateComments = List(candidates.size) { "" },
        inputText = inputText,
        isComposing = isComposing,
        associationCandidates = emptyList(),
        isShowingRecentClipboard = false,
        hasNextPage = false,
        isLoading = isLoading,
    )

    @Test
    fun decodingFlagReachesCandidateBarState() {
        val loading = barState(isLoading = true)
        assertTrue(loading is CandidateBarState.ChineseCandidates)
        assertTrue((loading as CandidateBarState.ChineseCandidates).isLoading)

        val settled = barState(isLoading = false)
        assertTrue(settled is CandidateBarState.ChineseCandidates)
        assertFalse((settled as CandidateBarState.ChineseCandidates).isLoading)
    }

    @Test
    fun loadingDoesNotTakeOverBarWithoutCandidates() {
        // 解码中且还没有任何候选时，候选栏状态保持 Idle：
        // loading 只作为顶缘细条叠加，工具栏按钮必须留在原位可点。
        // （早期实现会在这里占位成 loading，误触滑动就让整行工具栏消失。）
        val idle = CandidateBarState.from(
            candidates = emptyList(),
            candidateComments = emptyList(),
            inputText = "",
            isComposing = true,
            associationCandidates = emptyList(),
            isShowingRecentClipboard = false,
            hasNextPage = false,
            isLoading = true,
        )
        assertTrue("解码中不应抢占候选栏", idle is CandidateBarState.Idle)

        // 已经有编码/候选时照常显示内容，loading 条叠加在顶缘
        val withInput = CandidateBarState.from(
            candidates = emptyList(),
            candidateComments = emptyList(),
            inputText = "ni",
            isComposing = true,
            associationCandidates = emptyList(),
            isShowingRecentClipboard = false,
            hasNextPage = false,
            isLoading = true,
        )
        assertTrue(withInput is CandidateBarState.ChineseCandidates)
        assertTrue((withInput as CandidateBarState.ChineseCandidates).isLoading)
    }

    @Test
    fun candidateStateDefaultIsNotDecoding() {
        assertFalse(CandidateState().isGlideDecoding)
        assertTrue(CandidateState(isGlideDecoding = true).isGlideDecoding)
    }
}
