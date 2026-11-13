package com.kingzcheung.xime.ui.settings

import androidx.compose.ui.unit.dp

/** 设置页统一尺寸令牌：所有页面共用，避免各页各写一套 padding。 */
internal object SettingsDimens {
    val PageHorizontal = 16.dp
    val GroupGap = 12.dp
    val SectionGap = 24.dp
    val CardCorner = 20.dp
    val RowHorizontal = 16.dp
    val RowVerticalSingle = 14.dp
    val RowVerticalTwoLine = 12.dp
    val RowMinHeightSingle = 56.dp
    val RowMinHeightTwoLine = 72.dp
    val IconSize = 24.dp
    val IconGap = 16.dp
    val TrailingGap = 12.dp
    val DividerInset = 56.dp
    val DividerThickness = 1.dp
    val HeaderBottom = 8.dp
}

/** 页面底色：比卡片暗一级，卡片才"浮"得出来（浅色 ≈239，卡片 ≈252）。 */
@androidx.compose.runtime.Composable
internal fun settingsPageContainerColor() =
    androidx.compose.material3.MaterialTheme.colorScheme.surfaceContainerLow

/** 行最小高度（dp）：带摘要的行需要更高，保证两行文字舒适且触达 ≥48dp。 */
internal fun settingsRowMinHeightDp(hasSummary: Boolean): Int =
    if (hasSummary) 72 else 56

/** 卡片内分隔线的 y 偏移（px）：每行底部一条，最后一行不画。 */
internal fun dividerOffsets(heights: List<Int>): List<Int> {
    var y = 0
    val out = ArrayList<Int>(heights.size)
    heights.forEachIndexed { index, h ->
        y += h
        if (index != heights.lastIndex) out.add(y)
    }
    return out
}
