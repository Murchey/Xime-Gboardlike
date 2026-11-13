package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 键盘默认高度占比：手势导航 35%、传统导航栏 30%。 */
class KeyboardHeightPercentTest {

    @Test
    fun gestureNavUsesThirtyFive() {
        assertEquals(35, SettingsPreferences.defaultKeyboardHeightPercent(isGestureNav = true))
    }

    @Test
    fun traditionalNavBarUsesThirty() {
        assertEquals(30, SettingsPreferences.defaultKeyboardHeightPercent(isGestureNav = false))
    }

    @Test
    fun traditionalNavIsNotTallerThanGesture() {
        // 传统导航栏自带约 48dp 占位，键盘比例必须更小，总占用才不会顶到输入区
        val gesture = SettingsPreferences.defaultKeyboardHeightPercent(true)
        val navBar = SettingsPreferences.defaultKeyboardHeightPercent(false)
        org.junit.Assert.assertTrue(navBar < gesture)
    }
}
