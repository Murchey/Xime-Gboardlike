package com.kingzcheung.xime.association

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 原生库没装载（或装载失败）时，[NativeOnnxEngine] 的入口必须安静降级。
 *
 * 背景：真机上出现过 `UnsatisfiedLinkError` 直接把联想进程带崩——它是 Error，
 * 上层 catch(e: Exception) 接不住。这里不主动装载库，只验证这些入口不抛异常
 * （库恰好已被别的测试装载时，这些调用也应该正常）。
 */
@RunWith(AndroidJUnit4::class)
class NativeOnnxEngineGuardDeviceTest {

    @Test
    fun entriesDoNotThrowWithoutLibrary() {
        val initialized = NativeOnnxEngine.isInitialized()
        println("isInitialized=" + initialized)
        val candidates = NativeOnnxEngine.predict("今天天气", 5)
        assertTrue("未装载时应返回空表", candidates.isEmpty() || initialized)
        println("fixedSeqLen=" + NativeOnnxEngine.fixedSeqLen())
        NativeOnnxEngine.initVocab(mapOf("[BOS]" to 1))
    }
}
