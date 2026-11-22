package com.kingzcheung.xime.ui.keyboard

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** 按住时键面叠一层强调色（18% 填充 + 0.8 描边），短按动画期间保持不动。 */
@Composable
internal fun Modifier.keyHeldHighlight(held: Boolean): Modifier {
    val accent = MaterialTheme.colorScheme.primary
    val radius = LocalKeyCornerRadius.current
    if (!held) return this
    return drawBehind {
        drawRoundRect(accent.copy(alpha = 0.18f), cornerRadius = CornerRadius(radius.toPx()))
        val stroke = 1.dp.toPx()
        drawRoundRect(
            accent.copy(alpha = 0.8f),
            topLeft = Offset(stroke / 2f, stroke / 2f),
            size = Size((size.width - stroke).coerceAtLeast(0f), (size.height - stroke).coerceAtLeast(0f)),
            cornerRadius = CornerRadius((radius.toPx() - stroke / 2f).coerceAtLeast(0f)),
            style = Stroke(stroke),
        )
    }
}

/** 松开辉光时长与粒子数（与借用实现同一节奏：500ms、方块旋转外飞、透明度独立收尾）。 */
private const val GLOW_MS = 500
private const val GLOW_PARTICLES = 6

/**
 * 松开按键时从键帽边缘飞出的小方块辉光：旋转、向外扩散、迅速淡出。
 * 只在 [pressed] 由 true 变 false 时播一次，不参与布局与点击。
 */
@Composable
internal fun Modifier.keyReleaseGlow(radius: Dp, pressed: Boolean): Modifier {
    val progress = remember { Animatable(0f) }
    var wasPressed by remember { mutableStateOf(pressed) }
    LaunchedEffect(pressed) {
        if (wasPressed && !pressed) {
            progress.snapTo(0f)
            progress.animateTo(1f, tween(GLOW_MS, easing = LinearEasing))
            progress.snapTo(0f)
        }
        wasPressed = pressed
    }
    return drawWithContent {
        drawContent()
        val t = progress.value
        if (t <= 0f || t >= 1f) return@drawWithContent
        val corner = radius.toPx()
        val remaining = 1f - t
        val alpha = remaining * remaining * (3f - 2f * remaining) * 0.45f
        // 先快后慢的外移（四次幂），和借用实现同一曲线
        val travel = (1f - remaining * remaining * remaining * remaining) * size.minDimension * 0.10f
        val cx = size.width / 2f
        val cy = size.height / 2f
        val side = 2.dp.toPx() * (0.4f + remaining * 0.9f)
        val cornerOffset = minOf(corner, size.minDimension / 2f) + travel
        repeat(GLOW_PARTICLES) { i ->
            rotate(degrees = t * 260f + i * (360f / GLOW_PARTICLES), pivot = Offset(cx, cy)) {
                drawRect(
                    color = Color.White.copy(alpha = alpha),
                    topLeft = Offset(cx - size.width * 0.5f + size.width * 0.5f - side / 2f, cy - cornerOffset - side / 2f),
                    size = Size(side, side),
                )
            }
        }
    }
}
