package com.kingzcheung.xime.glide

import androidx.compose.runtime.compositionLocalOf

/** 下发到 26 键根容器的滑动输入状态与编排器。 */
data class GlideContext(
    val enabled: Boolean = false,
    val showTrail: Boolean = true,
    /** 物理键位注册表，由 KeyboardLayout 根容器持有；字母键在布局时上报几何。 */
    val registry: GlideGeometryRegistry? = null,
    /** 滑动编排器：字母键据此判断本次手势是否已被 glide 接管。 */
    val controller: GlideKeyboardController? = null,
)

val LocalGlideContext = compositionLocalOf { GlideContext() }
