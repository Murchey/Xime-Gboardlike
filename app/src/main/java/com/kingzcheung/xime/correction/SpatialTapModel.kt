package com.kingzcheung.xime.correction

import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.sqrt

/**
 * Gboard 风格的空间触控模型：把每个键建成二维高斯热区。
 *
 * 与上游 Xime 的差异：
 *  - 上游：单键、单偏移量，交给 channel.onnx 判断「这个偏移像不像误触」
 *  - Gboard：按下时同时评估**按下键 + 半径内邻键**，各自算出概率密度，
 *    让解码器在多候选中联合「空间 × 语言」决定最终词
 *
 * 方差公式沿用 Gboard 逆向得出的经验式（键尺寸以毫米计）：
 *   varX = 0.0075 * (1.5 * keyWidthMm)^2 + 1.68
 *   varY = 0.0108 * (1.5 * keyHeightMm)^2 + 1.33
 * 热区边界固定为 0（触点在键中心时概率最高，向边缘衰减但不归零）。
 */
object SpatialTapModel {

    /** 邻键搜索半径：约 1.2 个最小键宽（Gboard 的 SpatialModelMotionEventHandler 同口径）。 */
    private const val NEIGHBOR_RADIUS_FACTOR = 1.2f

    /** 单个触点最多参与的候选键数。 */
    private const val MAX_CANDIDATES = 4

    /**
     * 一次按键的空间候选。
     *
     * @param letter 候选字母（a-z）
     * @param logProbability 对数概率密度，越大越可能
     */
    data class SpatialCandidate(val letter: Char, val logProbability: Float)

    /**
     * 计算触点到各邻键的对数概率密度并排序。
     *
     * @param touchX/touchY 触点（键盘局部坐标，px）
     * @param keys 26 字母键的几何：letter -> (中心x, 中心y, 宽, 高)，单位 px
     * @param densityDpi 屏幕密度，用于 px -> mm
     */
    fun candidates(
        touchX: Float,
        touchY: Float,
        keys: Map<Char, KeyGeometry>,
        densityDpi: Float,
    ): List<SpatialCandidate> {
        if (keys.isEmpty() || densityDpi <= 0f) return emptyList()
        val pxPerMm = densityDpi / 25.4f
        if (pxPerMm <= 0f) return emptyList()

        // 以最小键宽作为邻键搜索基准
        val minKeyWidthPx = keys.values.minOf { it.widthPx }
        val radiusPx = minKeyWidthPx * NEIGHBOR_RADIUS_FACTOR

        val scored = mutableListOf<SpatialCandidate>()
        for ((letter, geo) in keys) {
            val dxPx = touchX - geo.centerXPx
            val dyPx = touchY - geo.centerYPx
            // 先按像素半径粗筛，再算高斯
            if (dxPx * dxPx + dyPx * dyPx > radiusPx * radiusPx * 2.25f) continue

            val wMm = geo.widthPx / pxPerMm
            val hMm = geo.heightPx / pxPerMm
            val varX = 0.0075f * (1.5f * wMm) * (1.5f * wMm) + 1.68f
            val varY = 0.0108f * (1.5f * hMm) * (1.5f * hMm) + 1.33f
            if (varX <= 0f || varY <= 0f) continue

            val cost = alignmentCost(touchX, touchY, geo, densityDpi) ?: continue
            val logP = -cost -
                0.5f * ln(2f * Math.PI.toFloat() * sqrt(varX * varY))

            scored.add(SpatialCandidate(letter, logP))
        }

        if (scored.isEmpty()) return emptyList()
        scored.sortByDescending { it.logProbability }

        // softmax 归一化：把绝对密度转成相对权重，便于与语言分混合
        val maxLog = scored.first().logProbability
        val weights = scored.map { exp((it.logProbability - maxLog).toDouble()).toFloat() }
        val sum = weights.sum()
        return scored.take(MAX_CANDIDATES).mapIndexed { i, c ->
            SpatialCandidate(c.letter, if (sum > 0f) weights[i] / sum else 0f)
        }
    }

    /** 高斯中心相对负对数概率；不截断邻键，供连续轨迹解码比较候选键。 */
    fun alignmentCost(touchX: Float, touchY: Float, geo: KeyGeometry, densityDpi: Float): Float? {
        if (!touchX.isFinite() || !touchY.isFinite() || !densityDpi.isFinite() || densityDpi <= 0f ||
            !geo.centerXPx.isFinite() || !geo.centerYPx.isFinite() ||
            !geo.widthPx.isFinite() || !geo.heightPx.isFinite() || geo.widthPx <= 0f || geo.heightPx <= 0f) return null
        val pxPerMm = densityDpi / 25.4f
        val wMm = geo.widthPx / pxPerMm
        val hMm = geo.heightPx / pxPerMm
        val varX = 0.0075f * (1.5f * wMm) * (1.5f * wMm) + 1.68f
        val varY = 0.0108f * (1.5f * hMm) * (1.5f * hMm) + 1.33f
        val dxMm = (touchX - geo.centerXPx) / pxPerMm
        val dyMm = (touchY - geo.centerYPx) / pxPerMm
        return (0.5f * (dxMm * dxMm / varX + dyMm * dyMm / varY)).takeIf { it.isFinite() }
    }

    /** 键的几何信息（px，键盘局部坐标）。 */
    data class KeyGeometry(
        val centerXPx: Float,
        val centerYPx: Float,
        val widthPx: Float,
        val heightPx: Float,
    )
}
