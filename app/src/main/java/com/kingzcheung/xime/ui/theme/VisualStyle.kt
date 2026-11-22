package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 键面材质（视觉样式）。主题负责明暗与配色，这里只决定键面那层"材质"。
 * id 会持久化，升级时必须兼容。
 */
enum class VisualStyle(val id: String, val title: String) {
    ORIGINAL("original", "原有外观"),
    NEON("neon", "霓光描边"),
    GLASS("glass", "流光玻璃"),
    FACET("facet", "柔光折面"),
    FROST("frost", "毛玻璃");

    companion object {
        fun fromId(id: String?): VisualStyle = entries.firstOrNull { it.id == id } ?: ORIGINAL

        /** 设置里可选的三种；GLASS/FROST 是旧样式（假玻璃），id 保留，读到就按原有外观画。 */
        val offered = listOf(ORIGINAL, NEON, FACET)

        /** 键盘实际绘制的材质：已不提供的两种回落到原有外观。 */
        fun forKeyboard(style: VisualStyle): VisualStyle = if (style in offered) style else ORIGINAL
    }
}

/** 当前生效的视觉样式（Compose 状态：读它的合成会自动重组）。 */
object VisualStyles {
    var current by mutableStateOf(VisualStyle.ORIGINAL)
        internal set
}

/** 玻璃效果开关（键面半透明 + 玻璃高光/亮边）；与液态玻璃叠加。 */
object GlassEffect {
    var enabled by mutableStateOf(false)
        internal set
}

fun Modifier.visualMaterial(
    style: VisualStyle,
    radius: Dp = 12.dp,
    level: MaterialLevel = MaterialLevel.BASE,
    bottomRadius: Dp = radius,
): Modifier = materialSurface(style, radius, level, bottomRadius)
