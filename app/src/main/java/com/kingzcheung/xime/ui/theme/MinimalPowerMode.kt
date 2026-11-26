package com.kingzcheung.xime.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object MinimalPowerMode {
    var enabled by mutableStateOf(false)
        internal set
}
