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
    fun loadingSurvivesWithoutCandidates() {
        // 抬手时还没有任何候选：只要有编码（或组合态），loading 条仍应可见，
        // 否则模型推理的这段时间用户看不到任何反馈。
        val state = CandidateBarState.from(
            candidates = emptyList(),
            candidateComments = emptyList(),
            inputText = "",
            isComposing = true,
            associationCandidates = emptyList(),
            isShowingRecentClipboard = false,
            hasNextPage = false,
            isLoading = true,
        )
        // 解码中即使无编码无候选也要占位显示 loading，否则模型推理那段时间毫无反馈
        assertTrue(state is CandidateBarState.ChineseCandidates)
        assertTrue((state as CandidateBarState.ChineseCandidates).isLoading)
        assertTrue(state.candidates.isEmpty())

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
