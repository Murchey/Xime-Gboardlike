package com.kingzcheung.xime

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 子进程不能开 Rime 引擎：抢不到 userdb 锁会触发 RepairDB，学习数据整段丢失。 */
class RimePreInitProcessTest {

    private val pkg = "com.ximegboard"

    @Test
    fun mainProcessMayInitialize() {
        assertTrue(XimeApplication.shouldPreInitializeRime(pkg, pkg))
    }

    @Test
    fun inferenceAndAsrProcessesAreSkipped() {
        assertFalse(XimeApplication.shouldPreInitializeRime("$pkg:inference", pkg))
        assertFalse(XimeApplication.shouldPreInitializeRime("$pkg:asr", pkg))
    }

    @Test
    fun unknownProcessNameIsSkipped() {
        assertFalse(XimeApplication.shouldPreInitializeRime(null, pkg))
        assertFalse(XimeApplication.shouldPreInitializeRime("", pkg))
    }
}
