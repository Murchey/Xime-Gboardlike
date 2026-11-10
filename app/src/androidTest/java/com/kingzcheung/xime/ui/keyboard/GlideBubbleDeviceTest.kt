package com.kingzcheung.xime.ui.keyboard

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class GlideBubbleDeviceTest {
    @Test fun 普通短按保留气泡而滑动接管隐藏气泡() {
        val press = SwipeState(isPressed = true, pressedText = "u", isLetterPress = true)
        assertFalse("普通短按不应抑制", shouldSuppressGlideBubble(false, press))
        assertTrue("滑动接管应抑制", shouldSuppressGlideBubble(true, press))
        val longPress = SwipeState(isPressed = true, isLongPress = true, longPressItems = listOf("!"))
        assertFalse("长按符号应保留", shouldSuppressGlideBubble(true, longPress))
    }
}
