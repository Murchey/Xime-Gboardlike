package com.kingzcheung.xime.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * SystemSpeechAvailability 的纯逻辑部分（不触 Android 服务查询）。
 *
 * 探测本身依赖 PackageManager / SpeechRecognizer，只能在真机验证；
 * 但「原因枚举 → 可用性」的映射关系是可单测的，也最容易被后续改动破坏。
 */
class SystemSpeechAvailabilityTest {

    @Test
    fun 只有OK表示可用() {
        // 可用的唯一状态是 OK；其余原因都必须判为不可用，
        // 否则设置页会显示「本机已就绪」但实际调不起来。
        for (reason in SystemSpeechAvailability.Reason.entries) {
            val available = reason == SystemSpeechAvailability.Reason.OK
            val r = SystemSpeechAvailability.Result(available = available, reason = reason)
            assertEquals(
                "reason=" + reason + " 的 available 标记应与 OK 判定一致",
                available, r.available,
            )
        }
    }

    @Test
    fun 三种失败原因都应可表达() {
        // 计划要求「返回结构化原因（无服务 / 包不可见 / 服务创建失败）」，
        // 供设置页说明为何不可用，而不是灰掉不解释。
        val reasons = SystemSpeechAvailability.Reason.entries.toSet()
        assertTrue(reasons.contains(SystemSpeechAvailability.Reason.SERVICE_NOT_FOUND))
        assertTrue(reasons.contains(SystemSpeechAvailability.Reason.NOT_RECOGNIZED))
        assertTrue(reasons.contains(SystemSpeechAvailability.Reason.CREATE_FAILED))
        assertTrue(reasons.contains(SystemSpeechAvailability.Reason.OK))
    }

    @Test
    fun 结果可携带服务包名() {
        val ok = SystemSpeechAvailability.Result(
            available = true,
            reason = SystemSpeechAvailability.Reason.OK,
            servicePackage = "com.example.speech",
        )
        assertEquals("com.example.speech", ok.servicePackage)

        val notFound = SystemSpeechAvailability.Result(
            available = false,
            reason = SystemSpeechAvailability.Reason.SERVICE_NOT_FOUND,
        )
        assertNull("无服务时包名应为空", notFound.servicePackage)
        assertFalse(notFound.available)
    }

    @Test
    fun 可用于分流的判定条件() {
        // 调用方普遍用 available 决定是否让用户选该系统引擎，
        // 用 reason 决定提示文案，两者必须解耦且互不矛盾。
        val createFailed = SystemSpeechAvailability.Result(
            available = false,
            reason = SystemSpeechAvailability.Reason.CREATE_FAILED,
            servicePackage = "com.vendor.asr",
        )
        assertFalse("创建失败必须判不可用", createFailed.available)
        assertEquals("但服务包名仍应保留供诊断", "com.vendor.asr", createFailed.servicePackage)
    }
}
