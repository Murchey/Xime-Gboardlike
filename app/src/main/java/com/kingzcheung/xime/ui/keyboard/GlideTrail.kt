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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.glide.GlidePoint

internal const val GLIDE_TRAIL_LIFETIME_MS = 280L

/** 残影分几段绘制：段内平滑、段间靠圆头衔接，既保留时间淡出又不显颗粒。 */
private const val TRAIL_BANDS = 6

/** 采样点太密会放大抖动：先按最小间距抽稀。 */
private const val TRAIL_MIN_POINT_DISTANCE_PX = 1.5f

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
        // 手指位置抖动会让轨迹呈锯齿状：写入前先做抽稀 + 低通平滑
        val smoothed = smoothTrailPoints(source)
        if (source.isEmpty()) return // 抬手不突兀清除，由帧动画完成消退。
        if (source.size == 1 || points.isEmpty()) {
            sourceTime = source.first().timeMs
            displayTime = nowMs - (source.last().timeMs - sourceTime).coerceAtLeast(0L)
        }
        frameTimeMs = nowMs
        points = smoothed.asSequence().map { p ->
            p.copy(timeMs = (displayTime + p.timeMs - sourceTime).coerceAtMost(nowMs))
        }.filter { nowMs - it.timeMs < GLIDE_TRAIL_LIFETIME_MS }.toList().takeLast(192)
    }

    fun advance(nowMs: Long) {
        frameTimeMs = nowMs
        if (points.lastOrNull()?.let { nowMs - it.timeMs >= GLIDE_TRAIL_LIFETIME_MS } == true) clear()
    }

    fun clear() { points = emptyList() }
}

/**
 * 轨迹平滑（纯函数，可单测）：
 * 1. 相邻点距离小于 [minDistancePx] 的直接丢弃（去重，抑制抖动放大）；
 * 2. 再做 [passes] 次三点加权平均（权重 1:2:1），把折线拐角磨圆，端点保持不动。
 */
internal fun smoothTrailPoints(
    points: List<GlidePoint>,
    minDistancePx: Float = TRAIL_MIN_POINT_DISTANCE_PX,
    passes: Int = 2,
): List<GlidePoint> {
    if (points.size <= 2) return points
    val thinned = ArrayList<GlidePoint>(points.size)
    for (p in points) {
        val last = thinned.lastOrNull()
        if (last == null) {
            thinned.add(p)
            continue
        }
        val dx = p.x - last.x
        val dy = p.y - last.y
        if (dx * dx + dy * dy >= minDistancePx * minDistancePx) thinned.add(p)
    }
    // 尾点始终保留，避免轨迹比手指短一截
    if (thinned.lastOrNull() !== points.last()) thinned.add(points.last())
    if (thinned.size <= 2) return thinned

    var current = thinned.toList()
    repeat(passes) {
        val next = ArrayList<GlidePoint>(current.size)
        next.add(current.first())
        for (i in 1 until current.size - 1) {
            val a = current[i - 1]
            val b = current[i]
            val c = current[i + 1]
            next.add(
                b.copy(
                    x = (a.x + 2f * b.x + c.x) / 4f,
                    y = (a.y + 2f * b.y + c.y) / 4f,
                )
            )
        }
        next.add(current.last())
        current = next
    }
    return current
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
    val visible = points.filter { glideTrailAlpha(it.timeMs, now) > 0f }
    if (visible.size < 2) return

    // 按时间分带：每带内画一条平滑路径，带间共享端点由圆头衔接。
    // 相比"每两个点画一条线段"，不会出现一节一节的颗粒感。
    val bandSpan = ((visible.size - 1) / TRAIL_BANDS.toFloat()).let {
        kotlin.math.ceil(it).toInt().coerceAtLeast(1)
    }
    var start = 0
    while (start < visible.size - 1) {
        val end = (start + bandSpan).coerceAtMost(visible.size - 1)
        val slice = visible.subList(start, end + 1)
        if (slice.size >= 2) {
            val alpha = glideTrailAlpha(slice.last().timeMs, now)
            if (alpha > 0f) {
                val smoothed = slice.map { Offset(it.x, it.y) }
                drawSmoothTrailPath(smoothed, color.copy(alpha = alpha), alpha)
            }
        }
        start = end
    }
}

/** 用二次贝塞尔穿过相邻点中点，把折线画成连续曲线。 */
private fun DrawScope.drawSmoothTrailPath(points: List<Offset>, color: Color, alpha: Float) {
    if (points.size < 2) return
    val path = Path().apply {
        moveTo(points[0].x, points[0].y)
        for (i in 1 until points.size - 1) {
            val control = points[i]
            val next = points[i + 1]
            val midX = (control.x + next.x) / 2f
            val midY = (control.y + next.y) / 2f
            quadraticBezierTo(control.x, control.y, midX, midY)
        }
        val last = points.last()
        lineTo(last.x, last.y)
    }
    val width = (3.5f + 4.5f * alpha).dp.toPx()
    drawPath(
        path = path,
        brush = Brush.linearGradient(
            colors = listOf(color.copy(alpha = color.alpha * 0.35f), color),
            start = points.first(),
            end = points.last(),
        ),
        style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round),
    )
}
