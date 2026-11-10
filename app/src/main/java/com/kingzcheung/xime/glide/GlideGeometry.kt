package com.kingzcheung.xime.glide

import kotlin.math.sqrt

/**
 * 阶段B纯逻辑地基：滑动专用的物理键位几何快照。
 *
 * 与现有 KeyTapLogger 的区别：允许同一字母多个物理实例（分体键盘），
 * 编码取真实逻辑绑定而非键面文字，矩形为键盘局部像素坐标。
 * 全部为不可变快照，DOWN 时冻结；布局版本变化即取消本次轨迹。
 */
data class GlideKeyRect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = heightOrZero()
    private fun heightOrZero(): Float = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val isValid: Boolean get() = width > 0f && bottom > top

    fun contains(x: Float, y: Float): Boolean =
        x >= left && x <= right && y >= top && y <= bottom

    /** 触点到键中心的距离（键盘局部 px）。 */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = x - centerX; val dy = y - centerY
        return sqrt(dx * dx + dy * dy)
    }

    /** 触点到矩形边界的距离；在矩形内为 0。 */
    fun distanceToEdge(x: Float, y: Float): Float {
        val dx = maxOf(left - x, 0f, x - right)
        val dy = maxOf(top - y, 0f, y - bottom)
        return sqrt(dx * dx + dy * dy)
    }
}

/** 一个物理键实例。id 在同一快照内稳定，与显示文字无关。 */
data class GlideKey(
    val id: String,
    /** 参与解码的逻辑编码（如 'a'，或分号方案的 ';'）。 */
    val code: String,
    val rect: GlideKeyRect,
    /** 是否是可参与滑动编码的字母/韵母键（排除功能键）。 */
    val eligible: Boolean = true,
    /** 分体键盘左右区等归属，供调试与分区特征。 */
    val zone: String = "",
    /** 逻辑行列：用于"相邻键才可能连续"的路径合理性约束。 */
    val row: Int = 0,
    val col: Int = 0,
) {
    val isValid: Boolean get() = id.isNotEmpty() && code.isNotEmpty() && rect.isValid

    /** 与另一键的"键盘距离"（行列差的切比雪夫距离）。 */
    fun gridDistance(other: GlideKey): Int =
        maxOf(kotlin.math.abs(row - other.row), kotlin.math.abs(col - other.col))
}

/**
 * 一次滑动开始时的不可变几何快照。
 * [version] 覆盖布局、尺寸、分体、悬浮位置、方案指纹；任一变化都必须取消旧轨迹。
 */
data class GlideGeometrySnapshot(
    val version: String,
    val keys: List<GlideKey>,
    val widthPx: Float,
    val heightPx: Float,
) {
    val eligibleKeys: List<GlideKey> get() = keys.filter { it.eligible && it.isValid }

    val isValid: Boolean get() =
        version.isNotEmpty() && widthPx > 0f && heightPx > 0f && eligibleKeys.isNotEmpty()

    /** 找触点最近的可参与键；超出 maxDistance 返回 null。 */
    fun nearestKey(x: Float, y: Float, maxDistance: Float = Float.MAX_VALUE): GlideKey? =
        eligibleKeys.minByOrNull { it.rect.distanceToEdge(x, y) }
            ?.takeIf { it.rect.distanceToEdge(x, y) <= maxDistance }

    /** 找触点落在哪个键内（含多个重叠时取中心最近）。 */
    fun keyAt(x: Float, y: Float): GlideKey? =
        eligibleKeys.filter { it.rect.contains(x, y) }
            .minByOrNull { it.rect.distanceTo(x, y) }
}
