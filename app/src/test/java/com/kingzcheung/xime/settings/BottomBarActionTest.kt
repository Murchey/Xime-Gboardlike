package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 底部工具条默认值与功能枚举映射。 */
class BottomBarActionTest {

    @Test
    fun decodeKnownNames() {
        assertEquals(BottomBarAction.SWITCH_IME, BottomBarAction.fromName("SWITCH_IME"))
        assertEquals(BottomBarAction.CLIPBOARD, BottomBarAction.fromName("CLIPBOARD"))
        assertEquals(BottomBarAction.QUICK_PHRASE, BottomBarAction.fromName("QUICK_PHRASE"))
        assertEquals(BottomBarAction.HIDE_KEYBOARD, BottomBarAction.fromName("HIDE_KEYBOARD"))
    }

    @Test
    fun unknownOrNullFallsBackToNone() {
        // 老版本残留值/手工改坏的值不能崩，也不能画出没有图标的按钮
        assertEquals(BottomBarAction.NONE, BottomBarAction.fromName("NOT_A_REAL_ACTION"))
        assertEquals(BottomBarAction.NONE, BottomBarAction.fromName(null))
        assertEquals(BottomBarAction.NONE, BottomBarAction.fromName(""))
    }

    @Test
    fun defaultsMatchDocumentedBehaviour() {
        assertEquals("SWITCH_IME", SettingsPreferences.BOTTOM_BAR_DEFAULT_LEFT)
        assertEquals("CLIPBOARD", SettingsPreferences.BOTTOM_BAR_DEFAULT_RIGHT)
        // 默认值必须能解码成真实动作（NONE 会让按钮消失，不能是默认）
        assertFalse(BottomBarAction.fromName(SettingsPreferences.BOTTOM_BAR_DEFAULT_LEFT) == BottomBarAction.NONE)
        assertFalse(BottomBarAction.fromName(SettingsPreferences.BOTTOM_BAR_DEFAULT_RIGHT) == BottomBarAction.NONE)
    }
}
