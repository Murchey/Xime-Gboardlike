package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class GlideBubblePolicyTest {
    @Test fun 滑动输入开启不显示起点字母或分号按压气泡() {
        assertTrue(hideGlidePressBubble(true, SwipeState(isPressed = true, pressedText = "u", isLetterPress = true)))
        assertTrue(hideGlidePressBubble(true, SwipeState(isPressed = true, pressedText = ";")))
    }
    @Test fun 原地长按符号气泡仍保留() {
        assertFalse(hideGlidePressBubble(true, SwipeState(isPressed = true, isLongPress = true, longPressItems = listOf("!"))))
    }
    @Test fun 关闭滑动输入和功能键不受影响() {
        assertFalse(hideGlidePressBubble(false, SwipeState(isPressed = true, pressedText = "u", isLetterPress = true)))
        assertFalse(hideGlidePressBubble(true, SwipeState(isPressed = true, pressedText = "退格")))
    }
}
