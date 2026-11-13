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

/** 页面底色：GKD 同款，直接用 colorScheme.background（设置行平铺其上，不再垫卡片）。 */
@androidx.compose.runtime.Composable
internal fun settingsPageContainerColor() =
    androidx.compose.material3.MaterialTheme.colorScheme.background

/** 分组标题内边距：上 18 / 下 6，和内容行拉开又贴紧自己的行（GKD 的 titleItemPadding 比例）。 */
internal val SettingsGroupHeaderPadding = androidx.compose.foundation.layout.PaddingValues(
    start = SettingsDimens.RowHorizontal,
    top = 18.dp,
    end = SettingsDimens.RowHorizontal,
    bottom = 6.dp,
)
