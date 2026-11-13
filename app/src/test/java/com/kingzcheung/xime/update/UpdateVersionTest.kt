package com.kingzcheung.xime.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 版本比较：只认 x.y.z 数字段，避免 tag 命名差异导致误报/漏报。 */
class UpdateVersionTest {

    @Test
    fun normalizeStripsPrefixAndSuffix() {
        assertEquals("1.2.3", normalizeTag("v1.2.3"))
        assertEquals("1.2.3", normalizeTag("V1.2.3"))
        assertEquals("1.2.3", normalizeTag("1.2.3-beta"))
        assertEquals("1.2", normalizeTag("v1.2"))
        assertNull(normalizeTag("nightly"))
        assertNull(normalizeTag(""))
    }

    @Test
    fun newerVersionDetected() {
        assertTrue(isNewerVersion("1.1.6", "1.1.5"))
        assertTrue(isNewerVersion("1.2.0", "1.1.9"))
        assertTrue(isNewerVersion("2.0", "1.9.9"))
        assertFalse(isNewerVersion("1.1.5", "1.1.5"))
        assertFalse(isNewerVersion("1.1.4", "1.1.5"))
    }

    @Test
    fun tagWithPrefixComparesLikePlainVersion() {
        // 仓库 tag 常带 v 前缀；当前版本来自 BuildConfig（无前缀）
        assertTrue(isNewerVersion("v1.1.6", "1.1.5"))
        assertFalse(isNewerVersion("v1.1.5", "1.1.5"))
    }

    @Test
    fun unparsableInputIsNeverNewer() {
        assertFalse(isNewerVersion("nightly", "1.1.5"))
        assertFalse(isNewerVersion("1.1.5", "unknown"))
    }
}
