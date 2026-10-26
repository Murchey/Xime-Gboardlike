package com.kingzcheung.xime.ui.keyboard

/**
 * 手势阈值按实际键宽/键高缩放：手机竖屏保持 50dp 手感，
 * 大键（平板/分体）提高阈值防误触，矮键（横屏）降低阈值防滑不动。
 */
object GestureThresholds {
    private const val FALLBACK_DP = 50f
    private const val MIN_VERTICAL_DP = 30f
    private const val MAX_VERTICAL_DP = 60f
    private const val MIN_HORIZONTAL_DP = 40f
    private const val MAX_HORIZONTAL_DP = 60f

    fun vertical(keyHeightPx: Float, density: Float): Float {
        val d = if (density > 0f) density else 1f
        if (keyHeightPx <= 0f) return FALLBACK_DP * d
        return keyHeightPx.coerceIn(MIN_VERTICAL_DP * d, MAX_VERTICAL_DP * d)
    }

    fun horizontal(keyWidthPx: Float, density: Float): Float {
        val d = if (density > 0f) density else 1f
        if (keyWidthPx <= 0f) return FALLBACK_DP * d
        return keyWidthPx.coerceIn(MIN_HORIZONTAL_DP * d, MAX_HORIZONTAL_DP * d)
    }
}
