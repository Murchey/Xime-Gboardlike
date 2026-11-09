package com.kingzcheung.xime.glide

import androidx.compose.runtime.compositionLocalOf

/**
 * 阶段B接入骨架：把滑动开关与仲裁器下发到 26 键根容器。
 *
 * 目前只传递状态，不改动任何现有 pointerInput。功能默认关闭时，
 * 所有现有手势（点按、长按、上滑符号、横滑光标）保持原样。
 */
data class GlideContext(
    val enabled: Boolean = false,
    val showTrail: Boolean = true,
    val arbiter: GlideGestureArbiter? = null,
)

val LocalGlideContext = compositionLocalOf { GlideContext() }
