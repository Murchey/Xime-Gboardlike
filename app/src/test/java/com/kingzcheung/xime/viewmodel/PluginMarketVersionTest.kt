package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.settings.HostVersion
import com.kingzcheung.xime.settings.XimeIndexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMarketVersionTest {

    @Test
    fun `插件市场按上游 3_3_0 兼容`() {
        assertEquals("3.3.0", HostVersion.COMPAT)
        assertEquals(HostVersion.COMPAT, PLUGIN_MARKET_HOST_VERSION)
        assertTrue(XimeIndexParser.isCompatible(PLUGIN_MARKET_HOST_VERSION, ">=3.3.0"))
        assertFalse(XimeIndexParser.isCompatible(PLUGIN_MARKET_HOST_VERSION, ">=3.4.0"))
    }

    @Test
    fun `应用版本号归零后仍用兼容版本判定插件`() {
        assertTrue(HostVersion.DISPLAY.startsWith("1."))
        // 若误用 1.0.2 与 minHostVersion 3.0.0 比较会被判为不兼容。
        assertFalse(XimeIndexParser.isCompatible(HostVersion.DISPLAY, ">=3.0.0"))
        assertTrue(XimeIndexParser.isCompatible(HostVersion.COMPAT, ">=3.0.0"))
    }
}
