package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.rime.RimeCandidate
import com.kingzcheung.xime.service.ExpandedCandidatePager

/**
 * 候选展开页的纯规则（可 JVM 单测）。
 *
 * 这些条件原先分别写在服务层与键盘视图里，容易改一处漏一处；
 * 收敛到此处后，收起/数据源切换只有一个事实来源。
 */
object CandidatePageRules {

    /**
     * 展开页宽度基准：悬浮模式固定按卡片宽（卡片渲染在父容器内）；
     * 否则优先用实测宽（收窄/分屏），首帧尚未测量时回退屏宽。
     */
    fun expandedPageWidthDp(
        isFloating: Boolean,
        cardWidthDp: Int,
        measuredWidthDp: Int,
        screenWidthDp: Int,
    ): Int = when {
        isFloating -> cardWidthDp.coerceAtLeast(1)
        measuredWidthDp > 0 -> measuredWidthDp
        else -> screenWidthDp
    }

    /** 悬浮模式页高有限：右栏 4 键等分压缩（与横屏同款）。 */
    fun rightRailEqualSplit(isFloating: Boolean): Boolean = isFloating

    /**
     * 网格列数：扣除左右栏与内边距后按可用宽度估算。
     *
     * 注释占宽明显（每个候选多挂一行小字），开启时压到 3~4 列；
     * 关闭时手机 4~5 列、平板最多 6 列，避免过密或过疏。
     */
    fun gridColumnsFor(pageWidthDp: Int, showComments: Boolean): Int {
        val available = (pageWidthDp - 100f).coerceAtLeast(160f)
        val columns = if (showComments) available / 110f else available / 90f
        return columns.toInt().coerceIn(if (showComments) 3 else 4, if (showComments) 4 else 5)
    }

    /** 展开态且已有全量候选时，候选栏与展开页都切换到全量数据源。 */
    fun usesExpandedData(expanded: Boolean, expandedCount: Int): Boolean =
        expanded && expandedCount > 0

    /**
     * 选词结束后自动收起：候选与联想都为空才收起。
     * 选词过程中仍有候选/联想时保持展开，避免长候选选择被中断。
     */
    fun shouldAutoCollapse(
        expanded: Boolean,
        candidateCount: Int,
        associationCount: Int,
    ): Boolean = expanded && candidateCount == 0 && associationCount == 0

    /**
     * 展开态候选栏条目：单字筛选只保留单字，且始终携带原始全局索引
     * （点选/长按要用它换算回全量列表位置）。
     */
    fun railItems(
        expandedData: Boolean,
        singleCharOnly: Boolean,
        all: List<RimeCandidate>,
    ): List<Pair<Int, RimeCandidate>> {
        if (!expandedData) return emptyList()
        return all.withIndex()
            .filter { !singleCharOnly || it.value.text.length == 1 }
            .map { it.index to it.value }
    }
}
