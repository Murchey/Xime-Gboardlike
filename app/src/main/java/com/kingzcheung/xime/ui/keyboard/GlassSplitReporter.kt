package com.kingzcheung.xime.ui.keyboard

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Rect

/**
 * 分体键盘左右两半的窗口矩形上报（共享点，避免逐层改签名）。
 * 服务层用它把模糊面拆成 keyboard-left/right，中缝保持清晰。
 */
val LocalGlassSplitReporter = staticCompositionLocalOf<((Rect?, Rect?) -> Unit)?> { null }
