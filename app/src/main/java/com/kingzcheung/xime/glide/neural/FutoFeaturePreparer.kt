package com.kingzcheung.xime.glide.neural

import com.kingzcheung.xime.glide.GlideGeometrySnapshot
import com.kingzcheung.xime.glide.GlidePoint
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/** 送进 FUTO 编码器的一份不可变输入。 */
data class PreparedSwipe(
    /** [2, 64]，先 x 后 y，键盘 bbox 归一化到 [0,1]。 */
    val features: FloatArray,
    /** [64, 2]，按 [slotCodes] 顺序排列的键中心。 */
    val keys: FloatArray,
    /** [64]，有效键槽为 true。 */
    val mask: BooleanArray,
    /** 每个键槽对应的逻辑编码（同一字母的分体键会重复出现）。 */
    val slotCodes: List<Char>,
    /** 去重后的编码集合，字母在前、分号最后。 */
    val alphabet: List<Char>,
) {
    /** 实际使用的键槽数（可能包含分体键盘的重复字母）。 */
    val keyCount: Int get() = slotCodes.size
}

/**
 * 轨迹预处理：与 FUTO 参考实现保持一致。
 *
 * 1. 用所有字母键矩形的并集 bbox 归一化坐标（标准 26 键正好得到
 *    FUTO QWERTY 的 0.05..0.95 / 0.167..0.833 坐标系）；
 * 2. 先按 60Hz 做时间均匀重采样，再线性取 64 个点；
 * 3. 键数组按编码排序，重复键原样保留，交给解码阶段做 logsumexp 合并。
 *
 * 与像素、DPI、键盘整体尺寸无关；悬浮/分体键盘只要键矩形可靠即可。
 */
object FutoFeaturePreparer {

    const val POINTS = 64
    const val MAX_KEYS = 64

    fun prepare(points: List<GlidePoint>, geometry: GlideGeometrySnapshot): PreparedSwipe? {
        if (points.size < 2 || !geometry.isValid) return null
        val usable = geometry.eligibleKeys.filter {
            it.code.length == 1 && (it.code[0] in 'a'..'z' || it.code[0] == ';')
        }
        if (usable.isEmpty() || usable.size > MAX_KEYS) return null

        val left = usable.minOf { it.rect.left }
        val top = usable.minOf { it.rect.top }
        val right = usable.maxOf { it.rect.right }
        val bottom = usable.maxOf { it.rect.bottom }
        val width = right - left
        val height = bottom - top
        if (width <= 0f || height <= 0f) return null

        val ordered = usable.sortedWith(
            compareBy({ if (it.code[0] == ';') 1 else 0 }, { it.code[0] }),
        )
        val slotCodes = ordered.map { it.code[0].lowercaseChar() }
        val alphabet = slotCodes.distinct().sortedWith(
            compareBy({ if (it == ';') 1 else 0 }, { it }),
        )

        val keys = FloatArray(MAX_KEYS * 2)
        val mask = BooleanArray(MAX_KEYS)
        ordered.forEachIndexed { index, key ->
            keys[index * 2] = (key.rect.centerX - left) / width
            keys[index * 2 + 1] = (key.rect.centerY - top) / height
            mask[index] = true
        }

        val features = resample(points, left, top, width, height)
        return PreparedSwipe(features, keys, mask, slotCodes, alphabet)
    }

    private fun resample(
        points: List<GlidePoint>,
        left: Float,
        top: Float,
        width: Float,
        height: Float,
    ): FloatArray {
        val xs = DoubleArray(points.size) { ((points[it].x - left) / width).toDouble() }
        val ys = DoubleArray(points.size) { ((points[it].y - top) / height).toDouble() }

        val t0 = points.first().timeMs
        val duration = (points.last().timeMs - t0).toDouble()
        var sx = xs
        var sy = ys
        if (duration > 1.0) {
            val n60 = max(2, (duration / (1000.0 / 60.0)).roundToInt() + 1)
            val rx = DoubleArray(n60)
            val ry = DoubleArray(n60)
            var segment = 0
            for (i in 0 until n60) {
                val target = duration * i / (n60 - 1).toDouble()
                while (segment + 2 < points.size && (points[segment + 1].timeMs - t0) < target) segment++
                val ta = (points[segment].timeMs - t0).toDouble()
                val tb = (points[segment + 1].timeMs - t0).toDouble()
                val span = tb - ta
                val f = if (span > 0.0) ((target - ta) / span).coerceIn(0.0, 1.0) else 0.0
                rx[i] = xs[segment] + (xs[segment + 1] - xs[segment]) * f
                ry[i] = ys[segment] + (ys[segment + 1] - ys[segment]) * f
            }
            sx = rx
            sy = ry
        }

        val features = FloatArray(POINTS * 2)
        val last = sx.size - 1
        for (i in 0 until POINTS) {
            val target = last * i / (POINTS - 1).toDouble()
            val index = target.toInt().coerceIn(0, last - 1)
            val f = (target - index).coerceIn(0.0, 1.0)
            features[i] = (sx[index] + (sx[index + 1] - sx[index]) * f).toFloat()
            features[POINTS + i] = (sy[index] + (sy[index + 1] - sy[index]) * f).toFloat()
        }
        return features
    }

    /** 仅供测试/诊断：预处理后的总弧长（键为单位）。 */
    fun arcLength(prepared: PreparedSwipe): Float {
        var total = 0f
        for (i in 1 until POINTS) {
            total += hypot(
                prepared.features[i] - prepared.features[i - 1],
                prepared.features[POINTS + i] - prepared.features[POINTS + i - 1],
            )
        }
        return total
    }

    /** 仅供测试：两点是否为同一归一化键位。 */
    fun sameKey(a: FloatArray, b: FloatArray, tolerance: Float = 1e-4f): Boolean =
        abs(a[0] - b[0]) <= tolerance && abs(a[1] - b[1]) <= tolerance
}
