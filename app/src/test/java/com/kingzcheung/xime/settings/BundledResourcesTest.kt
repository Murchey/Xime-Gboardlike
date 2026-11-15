package com.kingzcheung.xime.settings

import com.kingzcheung.xime.settings.BundledResources.Action
import org.junit.Assert.assertEquals
import org.junit.Test

class BundledResourcesTest {

    @Test
    fun `文件缺失时无论版本都恢复`() {
        assertEquals(Action.Restore, BundledResources.decide(true, false, null, "abc"))
        assertEquals(Action.Restore, BundledResources.decide(true, true, null, null))
    }

    @Test
    fun `版本没变时不动已有文件`() {
        assertEquals(Action.Leave, BundledResources.decide(false, false, "abc", "abc"))
        assertEquals(Action.Leave, BundledResources.decide(false, false, "user", "abc"))
    }

    @Test
    fun `版本变了且内容是我们那份时覆盖`() {
        assertEquals(Action.Replace, BundledResources.decide(false, true, "abc", "abc"))
    }

    @Test
    fun `版本变了且没有历史 hash 时覆盖老资源`() {
        assertEquals("第一次启用版本化机制，要把上一版内置资源换掉", Action.Replace, BundledResources.decide(false, true, "old", null))
    }

    @Test
    fun `版本变了但用户换过内容时保留`() {
        assertEquals(Action.Keep, BundledResources.decide(false, true, "user", "abc"))
    }

    @Test
    fun `目标 hash 读不出来时保留`() {
        assertEquals(Action.Keep, BundledResources.decide(false, true, null, "abc"))
    }
}
