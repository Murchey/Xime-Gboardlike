package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 设置分组：卡片外的小标题 + 圆角卡片，Google 设置那种分组块。
 *
 * 子项之间的分隔线由 [SettingsCard] 自动画，不用每页手写 HorizontalDivider。
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .padding(start = SettingsDimens.RowHorizontal, bottom = SettingsDimens.HeaderBottom)
                    .padding(top = 4.dp),
            )
        }
        SettingsCard(content = content)
    }
}

/**
 * 卡片底色：浅色用纯白（Google 设置那种白卡），深色用高一级容器色。
 *
 * 不用 surfaceContainerLowest：它随主题调色板浮动（实测有 239/252 两种），
 * 换了主题就跟页面底色贴在一起；纯白稳定保证层次。
 */
@Composable
internal fun settingsCardContainerColor() =
    if (isSystemInDarkTheme()) MaterialTheme.colorScheme.surfaceContainerHigh
    else androidx.compose.ui.graphics.Color.White

/** 圆角卡片容器：负责底色、圆角与子项间的自动分隔线。 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val container = settingsCardContainerColor()
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val density = LocalDensity.current
    val thicknessPx = with(density) { SettingsDimens.DividerThickness.toPx() }
    val insetPx = with(density) { SettingsDimens.DividerInset.toPx() }
    // 布局阶段写入、绘制阶段读取；不放进 Snapshot 状态，避免布局中触发重组
    val dividers = remember { mutableListOf<Float>() }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimens.CardCorner),
        color = container,
    ) {
        Layout(
            content = content,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(SettingsDimens.CardCorner))
                .drawBehind {
                    dividers.forEach { y ->
                        drawLine(
                            color = dividerColor,
                            start = Offset(insetPx, y),
                            end = Offset(size.width, y),
                            strokeWidth = thicknessPx,
                        )
                    }
                },
        ) { measurables, constraints ->
            val placeables = measurables.map { it.measure(Constraints(maxWidth = constraints.maxWidth)) }
            val width = placeables.maxOfOrNull { it.width } ?: constraints.minWidth
            val height = placeables.sumOf { it.height }
            val offsets = dividerOffsets(placeables.map { it.height })
            dividers.clear()
            dividers.addAll(offsets.map { it.toFloat() })
            layout(width, height) {
                var y = 0
                placeables.forEach { placeable ->
                    placeable.place(0, y)
                    y += placeable.height
                }
            }
        }
    }
}
