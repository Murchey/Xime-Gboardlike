package com.kingzcheung.xime.ui.settings

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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp

/**
 * 设置分组：灰色小标题 + 一张大圆角卡片（Gboard 新版设置风格）。
 *
 * 行之间由卡片内绘制的 1px 分隔线分隔（图标右侧起、首行上方/末行下方不画）；
 * 分组之间用 GroupGap 拉开，标题只作提示，不再使用主题蓝。
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
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(SettingsGroupHeaderPadding),
            )
        }
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(SettingsDimens.CardCorner),
            color = settingsCardContainerColor(),
        ) {
            SettingsCardColumn(content = content)
        }
    }
}

/**
 * 卡片内容列：相邻行之间画 1px 分隔线。
 *
 * 行边界在测量阶段记录、绘制阶段使用；深度取决于卡片亮度：
 * 深色卡片上用比卡片更暗的线（Gboard 实测 #292C34 → 分隔线 #22252C），浅色用 outlineVariant。
 */
@Composable
private fun SettingsCardColumn(content: @Composable () -> Unit) {
    val density = LocalDensity.current
    val inset = with(density) { SettingsDimens.DividerInset.toPx() }
    val thickness = with(density) { SettingsDimens.DividerThickness.toPx() }
    val card = settingsCardContainerColor()
    val dividerColor = if (card.luminance() < 0.5f) {
        Color.Black.copy(alpha = 0.18f)
    } else {
        MaterialTheme.colorScheme.outlineVariant
    }
    val boundaries = remember { ArrayList<Float>() }

    Layout(
        content = content,
        modifier = Modifier
            .fillMaxWidth()
            .drawBehind {
                val h = size.height
                boundaries.dropLast(1).forEach { y ->
                    if (y > 0f && y < h) {
                        drawLine(dividerColor, Offset(inset, y), Offset(size.width, y), thickness)
                    }
                }
            },
    ) { measurables, constraints ->
        val width = constraints.maxWidth
        val placeables = measurables.map { it.measure(Constraints(minWidth = width, maxWidth = width)) }
        boundaries.clear()
        var total = 0
        placeables.forEach {
            total += it.height
            boundaries.add(total.toFloat())
        }
        layout(width, total) {
            var y = 0
            placeables.forEach { p ->
                p.place(0, y)
                y += p.height
            }
        }
    }
}

/** 需要"内容块"时用的卡片（规则/预览这类），颜色与圆角与分组卡片一致。 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(SettingsDimens.CardCorner),
        color = settingsCardContainerColor(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** 卡片底色：深色用 Gboard 式蓝灰（#2A2D35），浅色沿用 M3 surfaceContainer。 */
@Composable
internal fun settingsCardContainerColor(): androidx.compose.ui.graphics.Color =
    if (settingsPageContainerColor().luminance() < 0.5f) {
        androidx.compose.ui.graphics.Color(0xFF2A2D35)
    } else {
        MaterialTheme.colorScheme.surfaceContainer
    }
