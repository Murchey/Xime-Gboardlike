package com.kingzcheung.xime.ui.theme

import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * 玻璃「柔化顶部边缘」的模糊细条：系统窗口模糊只能整块一个强度，
 * 所以把固定键盘上沿这 16dp 切成几条横条，透明度一级级升上去，下面整块是满强度。
 * 只切键盘自己的上沿，键盘外面的应用内容不会被模糊。
 */
val GLASS_TOP_FEATHER_HEIGHT = 16.dp
const val GLASS_TOP_FEATHER_STEPS = 6

/** 一条模糊窗口：窗口坐标（像素）和它的透明度系数。 */
data class GlassFeatherBand(val left: Int, val top: Int, val right: Int, val bottom: Int, val alpha: Float)

/**
 * 把卡片顶上 [featherPx] 切成 [steps] 条，从上到下透明度 1/(steps+1) … steps/(steps+1)。
 * 卡片太矮（不到两段高）时不切，返回空。
 */
fun glassTopFeatherBands(
    left: Int,
    top: Int,
    right: Int,
    bottom: Int,
    featherPx: Int,
    steps: Int = GLASS_TOP_FEATHER_STEPS,
): List<GlassFeatherBand> {
    if (featherPx <= 0 || steps <= 0 || right <= left || bottom - top < featherPx * 2) return emptyList()
    return (0 until steps).mapNotNull { i ->
        val from = top + (featherPx * i / steps.toFloat()).roundToInt()
        val to = top + (featherPx * (i + 1) / steps.toFloat()).roundToInt()
        if (to <= from) null else GlassFeatherBand(left, from, right, to, (i + 1f) / (steps + 1f))
    }
}
