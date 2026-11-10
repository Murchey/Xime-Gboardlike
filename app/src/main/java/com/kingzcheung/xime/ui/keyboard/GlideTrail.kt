package com.kingzcheung.xime.ui.keyboard

import android.os.SystemClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.glide.GlidePoint

internal const val GLIDE_TRAIL_LIFETIME_MS = 280L

internal fun glideTrailAlpha(pointTimeMs: Long, nowMs: Long): Float {
    val remaining = (1f - (nowMs - pointTimeMs).coerceAtLeast(0L).toFloat() / GLIDE_TRAIL_LIFETIME_MS)
        .coerceIn(0f, 1f)
    return remaining * remaining
}

/** 显示尾迹与解码轨迹分开，抬手后只保留短暂的视觉残影。 */
internal class GlideTrailState {
    var points by mutableStateOf<List<GlidePoint>>(emptyList())
        private set
    var frameTimeMs by mutableLongStateOf(0L)
        private set
    private var sourceTime = 0L
    private var displayTime = 0L

    fun update(source: List<GlidePoint>, nowMs: Long) {
        if (source.isEmpty()) return // 抬手不突兀清除，由帧动画完成消退。
        if (source.size == 1 || points.isEmpty()) {
            sourceTime = source.first().timeMs
            displayTime = nowMs - (source.last().timeMs - sourceTime).coerceAtLeast(0L)
        }
        frameTimeMs = nowMs
        points = source.asSequence().map { p ->
            p.copy(timeMs = (displayTime + p.timeMs - sourceTime).coerceAtMost(nowMs))
        }.filter { nowMs - it.timeMs < GLIDE_TRAIL_LIFETIME_MS }.toList().takeLast(192)
    }

    fun advance(nowMs: Long) {
        frameTimeMs = nowMs
        if (points.lastOrNull()?.let { nowMs - it.timeMs >= GLIDE_TRAIL_LIFETIME_MS } == true) clear()
    }

    fun clear() { points = emptyList() }
}

@Composable
internal fun rememberGlideTrail(enabled: Boolean): GlideTrailState {
    val trail = remember { GlideTrailState() }
    val points = trail.points
    LaunchedEffect(points, enabled) {
        if (!enabled) { trail.clear(); return@LaunchedEffect }
        if (points.isEmpty()) return@LaunchedEffect
        while (trail.points.isNotEmpty()) {
            withFrameNanos { trail.advance(SystemClock.uptimeMillis()) }
        }
    }
    return trail
}

internal fun DrawScope.drawGlideTrail(trail: GlideTrailState, color: Color) {
    val points = trail.points
    if (points.size < 2) return
    val now = trail.frameTimeMs
    for (i in 1 until points.size) {
        val a = points[i - 1]
        val b = points[i]
        val alphaA = glideTrailAlpha(a.timeMs, now)
        val alphaB = glideTrailAlpha(b.timeMs, now)
        if (alphaA == 0f && alphaB == 0f) continue
        val from = Offset(a.x, a.y)
        val to = Offset(b.x, b.y)
        if (from == to) continue
        drawLine(
            brush = Brush.linearGradient(listOf(color.copy(alpha = alphaA), color.copy(alpha = alphaB)), from, to),
            start = from, end = to,
            strokeWidth = (0.6f + 3.4f * (alphaA + alphaB) / 2f).dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}
