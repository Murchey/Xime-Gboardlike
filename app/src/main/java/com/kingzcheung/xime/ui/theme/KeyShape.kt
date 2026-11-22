package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 键帽圆角形状（所有布局与面板统一生效）。
 *
 * - [ROUNDED]：沿用配置的圆角（xime.yaml 的 key.corner_radius，默认 6dp）；
 * - [ROUNDER]：圆角下限抬到 [ROUNDER_RADIUS]，已经更大的圆角保持不动；
 * - [SQUARE]：圆角上限压到 5dp（仍有一点圆角，像早期 iOS）；
 * - [SHARP]：直角。
 *
 * id 会持久化，升级时必须保持兼容。
 */
enum class KeyShape(
    val id: String,
    val title: String,
    private val maxRadius: Int?,
    private val minRadius: Int? = null,
) {
    ROUNDED("rounded", "圆角", null),
    ROUNDER("rounder", "大圆角", null, ROUNDER_RADIUS),
    SQUARE("square", "方形", 5),
    SHARP("sharp", "直角", 0);

    /** 按当前形状钳制配置里的圆角（dp 版本；所有键面都走这一个入口）。 */
    fun radius(configured: Dp): Dp {
        val capped = maxRadius?.let { minOf(it.dp, configured) } ?: configured
        return minRadius?.let { maxOf(it.dp, capped) } ?: capped
    }

    companion object {
        fun fromId(id: String?) = entries.firstOrNull { it.id == id } ?: ROUNDED
    }
}

private const val ROUNDER_RADIUS = 12

/** 当前生效的按键形状（Compose 状态：读它的合成会自动重组）。 */
object KeyShapes {
    var current by mutableStateOf(KeyShape.ROUNDED)
        internal set
}
