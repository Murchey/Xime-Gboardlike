package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.*
import org.junit.Test

class GlideBubblePolicyTest {
    private val press = SwipeState(isPressed = true, pressedText = "u", isLetterPress = true)

    @Test fun 普通短按气泡在滑动开关开启时仍显示() {
        assertFalse(shouldSuppressGlideBubble(false, press))
    }
    @Test fun 手势被滑动接管后抑制按压气泡() {
        assertTrue(shouldSuppressGlideBubble(true, press))
    }
    @Test fun 原地长按符号气泡始终保留() {
        val longPress = SwipeState(isPressed = true, isLongPress = true, longPressItems = listOf("!"))
        assertFalse(shouldSuppressGlideBubble(false, longPress))
        assertFalse(shouldSuppressGlideBubble(true, longPress))
    }
    @Test fun 上滑提示气泡始终保留() {
        val swiping = SwipeState(isPressed = true, isSwiping = true, swipeText = "1")
        assertFalse(shouldSuppressGlideBubble(true, swiping))
    }
}
