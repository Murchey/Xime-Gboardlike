package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardTextScaleTest {

    @Test
    fun `字号百分比限制在 80 到 130`() {
        assertEquals(80, SettingsPreferences.coerceKeyboardTextScale(1))
        assertEquals(100, SettingsPreferences.coerceKeyboardTextScale(100))
        assertEquals(130, SettingsPreferences.coerceKeyboardTextScale(999))
    }
}
