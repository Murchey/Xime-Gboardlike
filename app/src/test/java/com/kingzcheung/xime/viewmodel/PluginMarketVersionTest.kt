package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.settings.XimeIndexParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PluginMarketVersionTest {

    @Test
    fun `插件市场按上游 3_3_0 兼容`() {
        assertEquals("3.3.0", PLUGIN_MARKET_HOST_VERSION)
        assertTrue(XimeIndexParser.isCompatible(PLUGIN_MARKET_HOST_VERSION, ">=3.3.0"))
        assertFalse(XimeIndexParser.isCompatible(PLUGIN_MARKET_HOST_VERSION, ">=3.4.0"))
    }
}
