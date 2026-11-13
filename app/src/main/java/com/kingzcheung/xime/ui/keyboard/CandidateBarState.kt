package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.clipboard.ClipboardItem

sealed interface CandidateBarState {

    data object Idle : CandidateBarState

    data class ChineseCandidates(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val inputText: String = "",
        val preeditText: String = "",
        val hasMore: Boolean = false,
        val associationCandidates: List<String> = emptyList(),
        /** 编码显示串中的光标偏移（字符，-1 = 末尾/不适用），编码编辑光标可视化用。 */
        val preeditCaretPos: Int = -1,
        /** 滑动解码进行中：候选栏底部叠加滚动 loading 条。 */
        val isLoading: Boolean = false,
    ) : CandidateBarState

    data class AssociationOnly(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val hasMore: Boolean = false,
        val highlightIndex: Int = -1,
    ) : CandidateBarState

    data class EnglishCandidates(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val pendingText: String = "",
    ) : CandidateBarState

    data class ClipboardDisplay(
        val candidates: List<String> = emptyList(),
        /**
         * 与 [candidates] 等长（图片位为条目、非图片位为 null）：候选栏据此把图片候选渲染成
         * Gboard 风格的"圆角卡片 + 圆形缩略图 + 图片"芯片。
         */
        val images: List<ClipboardItem?> = emptyList(),
    ) : CandidateBarState

    data class Calculator(
        val candidates: List<String> = emptyList(),
        val comments: List<String> = emptyList(),
        val expression: String = "",
        val result: String = "",
    ) : CandidateBarState

    companion object {
        fun from(
            candidates: List<String>,
            candidateComments: List<String>,
            inputText: String,
            preeditText: String = "",
            isComposing: Boolean,
            associationCandidates: List<String>,
            isShowingRecentClipboard: Boolean,
            hasNextPage: Boolean,
            isCalculatorActive: Boolean = false,
            preeditCaretPos: Int = -1,
            /** 剪贴板展示态下与 [candidates] 等长的图片条目列表（非图片位为 null）。 */
            clipboardImages: List<ClipboardItem?> = emptyList(),
            /** 滑动解码进行中（候选栏底部滚动 loading 条）。 */
            isLoading: Boolean = false,
        ): CandidateBarState {
            val hasCandidates = candidates.isNotEmpty()
            val hasAssociations = associationCandidates.isNotEmpty()
            val hasInput = inputText.isNotEmpty()
            return when {
                // 解码中优先占位：此时可能既无编码也无候选，但用户必须看到反馈
                isLoading && !hasCandidates && !hasInput ->
                    ChineseCandidates(isLoading = true)
                isShowingRecentClipboard && hasCandidates ->
                    ClipboardDisplay(candidates = candidates, images = clipboardImages)
                isCalculatorActive && hasCandidates ->
                    Calculator(candidates = candidates, comments = candidateComments)
                isComposing && (hasCandidates || hasInput) ->
                    ChineseCandidates(
                        candidates = candidates,
                        comments = candidateComments,
                        inputText = inputText,
                        preeditText = preeditText,
                        hasMore = hasCandidates && hasNextPage,
                        associationCandidates = associationCandidates,
                        preeditCaretPos = preeditCaretPos,
                        isLoading = isLoading,
                    )
                !isComposing && !hasInput && hasAssociations && !hasCandidates ->
                    AssociationOnly(
                        candidates = associationCandidates,
                        hasMore = hasNextPage,
                    )
                hasCandidates || hasInput ->
                    ChineseCandidates(
                        candidates = candidates,
                        comments = candidateComments,
                        inputText = inputText,
                        preeditText = preeditText,
                        hasMore = hasCandidates && hasNextPage,
                        preeditCaretPos = preeditCaretPos,
                        isLoading = isLoading,
                    )
                else -> Idle
            }
        }
    }
}

/**
 * 候选栏展示数据：候选栏各状态到「实际渲染条目」的唯一映射。
 *
 * 截断口径、更多指示、左侧图标集中在此，避免分支内散落 take(N) 造成
 * 「候选被截断但注释/图片没同步截断」这类索引错位。
 */
data class CandidateBarContent(
    val candidates: List<String>,
    val comments: List<String>,
    val images: List<ClipboardItem?>,
    val hasAnyMore: Boolean,
    val showLeftIcon: Boolean,
) {
    companion object {
        /** 剪贴板/计算器固定展示上限（保持历史行为）。 */
        const val FIXED_LIMIT = 20

        fun from(
            state: CandidateBarState,
            pageSize: Int,
            canScrollForward: Boolean,
        ): CandidateBarContent {
            val limit = pageSize.coerceAtLeast(0)
            return when (state) {
                is CandidateBarState.Idle -> CandidateBarContent(
                    candidates = emptyList(),
                    comments = emptyList(),
                    images = emptyList(),
                    hasAnyMore = false,
                    showLeftIcon = true,
                )
                is CandidateBarState.ChineseCandidates -> CandidateBarContent(
                    candidates = state.candidates.take(limit),
                    comments = state.comments,
                    images = emptyList(),
                    hasAnyMore = state.hasMore || canScrollForward,
                    showLeftIcon = false,
                )
                is CandidateBarState.AssociationOnly -> CandidateBarContent(
                    candidates = emptyList(),
                    comments = state.comments,
                    images = emptyList(),
                    hasAnyMore = state.hasMore,
                    showLeftIcon = false,
                )
                is CandidateBarState.EnglishCandidates -> CandidateBarContent(
                    candidates = state.candidates.take(limit),
                    comments = state.comments,
                    images = emptyList(),
                    hasAnyMore = false,
                    showLeftIcon = false,
                )
                is CandidateBarState.ClipboardDisplay -> CandidateBarContent(
                    candidates = state.candidates.take(FIXED_LIMIT),
                    // 与服务层同源同序截断，保证图片位索引不错位
                    images = state.images.take(FIXED_LIMIT),
                    comments = emptyList(),
                    hasAnyMore = false,
                    showLeftIcon = true,
                )
                is CandidateBarState.Calculator -> CandidateBarContent(
                    candidates = state.candidates.take(FIXED_LIMIT),
                    comments = state.comments,
                    images = emptyList(),
                    hasAnyMore = false,
                    showLeftIcon = false,
                )
            }
        }
    }
}
