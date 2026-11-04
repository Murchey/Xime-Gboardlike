package com.kingzcheung.xime.settings

import com.kingzcheung.xime.clipboard.ClipboardManager
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
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

    @Test
    fun `到点清理按最早过期条目计算等待时间`() {
        val now = 1_000_000L
        val oldest = now - 10 * 60_000L
        // 10 分钟前的条目、保留 30 分钟 → 20 分钟后到期
        assertEquals(20 * 60_000L, ClipboardManager.nextPurgeDelayMs(now, oldest, 30))
        // 已过线的条目返回负等待，由调用方立即清理（下限防抖在调用侧处理）
        assertEquals(-5 * 60_000L, ClipboardManager.nextPurgeDelayMs(now, oldest, 5))
        // 无普通条目或未启用保留策略 → 不排程
        assertNull(ClipboardManager.nextPurgeDelayMs(now, null, 30))
        assertNull(ClipboardManager.nextPurgeDelayMs(now, oldest, 0))
    }
}
