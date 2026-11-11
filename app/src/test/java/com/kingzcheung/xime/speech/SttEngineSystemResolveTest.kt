package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 系统语音引擎（SYSTEM）的解析规则。
 *
 * 沿用现有「不静默换引擎」原则：选了系统引擎但本机不可用时只报告
 * [SttUnavailable.SYSTEM_MISSING]，不偷偷改用别的引擎——系统引擎在不同机型
 * 差异极大，换引擎会让用户以为在跑系统识别，出问题无从判断。
 */
class SttEngineSystemResolveTest {

    private fun resolve(
        preferred: SttEngine?,
        localAvailable: Boolean = true,
        builtinConfigured: Boolean = true,
        plugins: List<String> = listOf("p1"),
        systemAvailable: Boolean = false,
    ) = SttEngineResolver.resolve(preferred, localAvailable, builtinConfigured, plugins, systemAvailable)

    @Test
    fun 系统引擎可用时直接选中() {
        val r = resolve(SttEngine.SYSTEM, systemAvailable = true)
        assertEquals(SttEngine.SYSTEM, r.engine)
        assertTrue(r.isAvailable)
        assertFalse(r.isFallback)
    }

    @Test
    fun 系统引擎不可用时报告缺失且不回退() {
        val r = resolve(SttEngine.SYSTEM, systemAvailable = false)
        assertEquals("仍应回显用户选择", SttEngine.SYSTEM, r.engine)
        assertEquals(SttUnavailable.SYSTEM_MISSING, r.unavailable)
        assertFalse(r.isAvailable)
        assertNull("不得静默换引擎", r.fallbackFrom)
    }

    @Test
    fun 系统引擎不受本地模型与内置Key影响() {
        // 其他引擎都不可用，但系统引擎可用 → 应正常选中
        val r = resolve(
            SttEngine.SYSTEM,
            localAvailable = false,
            builtinConfigured = false,
            plugins = emptyList(),
            systemAvailable = true,
        )
        assertEquals(SttEngine.SYSTEM, r.engine)
        assertTrue(r.isAvailable)
    }

    @Test
    fun 系统引擎不可用也不降级到内置在线() {
        // 与 PLUGIN 的降级规则刻意不同：插件缺失时可降级到内置在线，
        // 系统引擎缺失时不降级，保持用户显式选择的可解释性。
        val r = resolve(SttEngine.SYSTEM, builtinConfigured = true, systemAvailable = false)
        assertEquals(SttEngine.SYSTEM, r.engine)
        assertEquals(SttUnavailable.SYSTEM_MISSING, r.unavailable)
    }

    @Test
    fun 其他引擎的解析不受新增参数影响() {
        assertEquals(SttEngine.LOCAL, resolve(SttEngine.LOCAL, localAvailable = true).engine)
        assertEquals(
            SttUnavailable.MODEL_MISSING,
            resolve(SttEngine.LOCAL, localAvailable = false).unavailable,
        )
        assertEquals(
            SttEngine.BUILTIN_ONLINE,
            resolve(SttEngine.BUILTIN_ONLINE, builtinConfigured = true).engine,
        )
        // 插件缺失 + 内置可用 → 降级到内置在线（既有行为不变）
        val fallback = resolve(SttEngine.PLUGIN, plugins = emptyList(), builtinConfigured = true)
        assertEquals(SttEngine.BUILTIN_ONLINE, fallback.engine)
        assertEquals(SttEngine.PLUGIN, fallback.fallbackFrom)
    }

    @Test
    fun 枚举值可往返序列化() {
        assertEquals(SttEngine.SYSTEM, SttEngine.fromPrefValue("system"))
        assertEquals("system", SttEngine.SYSTEM.prefValue)
    }
}
