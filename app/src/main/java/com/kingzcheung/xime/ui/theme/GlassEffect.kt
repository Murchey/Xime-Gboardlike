package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** 玻璃效果开关（Compose 状态：读它的合成会自动重组）。 */
object GlassEffect {
    var enabled by mutableStateOf(false)
        internal set
}
