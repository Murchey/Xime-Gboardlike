package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SttEngineTest {

    @Test
    fun `本地已就绪直接使用本地`() {
        val r = SttEngineResolver.resolve(SttEngine.LOCAL, true, false, emptyList())
        assertEquals(SttEngine.LOCAL, r.engine)
        assertTrue(r.isAvailable)
        assertFalse(r.isFallback)
    }

    @Test
    fun `本地缺模型时报不可用而不是偷偷换成在线`() {
        val r = SttEngineResolver.resolve(SttEngine.LOCAL, false, true, listOf("p1"))
        assertEquals(SttEngine.LOCAL, r.engine)
        assertEquals(SttUnavailable.MODEL_MISSING, r.unavailable)
        assertFalse(r.isAvailable)
        assertFalse(r.isFallback)
    }

    @Test
    fun `内置在线已配置可用`() {
        val r = SttEngineResolver.resolve(SttEngine.BUILTIN_ONLINE, false, true, emptyList())
        assertEquals(SttEngine.BUILTIN_ONLINE, r.engine)
        assertTrue(r.isAvailable)
    }

    @Test
    fun `内置在线未填 Key 报不可用`() {
        val r = SttEngineResolver.resolve(SttEngine.BUILTIN_ONLINE, true, false, listOf("p1"))
        assertEquals(SttEngine.BUILTIN_ONLINE, r.engine)
        assertEquals(SttUnavailable.KEY_MISSING, r.unavailable)
    }

    @Test
    fun `插件可用时使用插件`() {
        val r = SttEngineResolver.resolve(SttEngine.PLUGIN, false, false, listOf("p1"))
        assertEquals(SttEngine.PLUGIN, r.engine)
        assertTrue(r.isAvailable)
        assertNull(r.fallbackFrom)
    }

    @Test
    fun `插件缺失但内置已配置时降级到内置`() {
        val r = SttEngineResolver.resolve(SttEngine.PLUGIN, false, true, emptyList())
        assertEquals(SttEngine.BUILTIN_ONLINE, r.engine)
        assertEquals(SttEngine.PLUGIN, r.fallbackFrom)
        assertTrue(r.isFallback)
        assertTrue(r.isAvailable)
    }

    @Test
    fun `插件缺失且内置未配置报插件缺失`() {
        val r = SttEngineResolver.resolve(SttEngine.PLUGIN, false, false, emptyList())
        assertEquals(SttEngine.PLUGIN, r.engine)
        assertEquals(SttUnavailable.PLUGIN_MISSING, r.unavailable)
    }

    @Test
    fun `未设置偏好时默认内置在线`() {
        val r = SttEngineResolver.resolve(null, false, true, emptyList())
        assertEquals(SttEngine.BUILTIN_ONLINE, r.engine)
    }

    @Test
    fun `偏好值可往返`() {
        SttEngine.entries.forEach {
            assertEquals(it, SttEngine.fromPrefValue(it.prefValue))
        }
        assertNull(SttEngine.fromPrefValue("别的"))
        assertNull(SttEngine.fromPrefValue(null))
    }
}
