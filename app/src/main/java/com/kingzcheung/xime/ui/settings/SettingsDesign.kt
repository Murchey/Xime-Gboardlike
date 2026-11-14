package com.kingzcheung.xime.ui.settings

import androidx.compose.ui.graphics.luminance
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

/** 深色模式页面底色：Material 3 标准深色 surface(#1C1B1F)，不用主题调色后的偏色灰。 */
private val SettingsDarkPageColor = androidx.compose.ui.graphics.Color(0xFF1C1B1F)

/**
 * 页面底色：浅色用主题 background；深色用 Android 标准灰，避免整屏发蓝/发紫。
 *
 * 判据取"当前实际生效的主题"（background 亮度），不能用 isSystemInDarkTheme()：
 * 应用内可以强制浅色/深色，系统模式与主题模式不一致时，底色会跟着系统走、
 * 文字颜色跟着主题走，出现深底深字（或浅底浅字）看不清的情况。
 */
@androidx.compose.runtime.Composable
internal fun settingsPageContainerColor(): androidx.compose.ui.graphics.Color {
    val background = androidx.compose.material3.MaterialTheme.colorScheme.background
    return if (background.luminance() < 0.5f) SettingsDarkPageColor else background
}

/** 分组标题内边距：上 18 / 下 6，与上一组拉开、与自己的行贴紧（Android 设置的比例）。 */
internal val SettingsGroupHeaderPadding = androidx.compose.foundation.layout.PaddingValues(
    start = SettingsDimens.RowHorizontal,
    top = 18.dp,
    end = SettingsDimens.RowHorizontal,
    bottom = 6.dp,
)
