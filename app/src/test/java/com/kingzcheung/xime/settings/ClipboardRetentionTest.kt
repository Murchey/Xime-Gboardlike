package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClipboardRetentionTest {

    @Test
    fun `旧版天数迁移到分钟`() {
        assertEquals(0, SettingsPreferences.legacyRetentionDaysToMinutes(0))
        assertEquals(1440, SettingsPreferences.legacyRetentionDaysToMinutes(1))
        assertEquals(10080, SettingsPreferences.legacyRetentionDaysToMinutes(7))
        assertEquals(43200, SettingsPreferences.legacyRetentionDaysToMinutes(30))
        assertEquals(129600, SettingsPreferences.legacyRetentionDaysToMinutes(90))
        assertEquals(0, SettingsPreferences.legacyRetentionDaysToMinutes(2))
    }

    @Test
    fun `自动清理候选包含小时档并保持递增`() {
        val options = SettingsPreferences.CLIPBOARD_RETENTION_MINUTE_OPTIONS
        assertTrue(60 in options)
        assertTrue(180 in options)
        assertTrue(360 in options)
        assertTrue(720 in options)
        assertTrue(1440 in options)
        assertEquals(options.sorted(), options)
        assertEquals(options.first(), 0)
    }
}
