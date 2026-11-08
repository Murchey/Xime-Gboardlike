package com.kingzcheung.xime.plugin.core.js

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 备份扩展点的超时策略。
 *
 * 整包上传/下载是真实耗时可到几十秒到几分钟的网络操作；通用 180s 超时在看门狗眼里
 * 与死循环无法区分，慢网络因此会被误判、插件被停用（上游 issue #1244）。
 * 这里守住两点：备份有独立的长超时；超时只中止本次调用，不把插件标记中毒。
 */
class JsBackupTimeoutPolicyTest {

    @Test
    fun `备份超时显著大于通用调用超时`() {
        // 通用 180s 对几十 MB 上传下载不够用，备份放宽到 5 分钟
        assertTrue(
            "备份超时应大于通用 180s",
            JsScriptRuntime.BACKUP_CALL_TIMEOUT_MS > 180_000L,
        )
        assertEquals(300_000L, JsScriptRuntime.BACKUP_CALL_TIMEOUT_MS)
    }

    @Test
    fun `备份超时不作为插件中毒依据`() {
        // callBackup 传 poisonOnTimeout=false：慢网络只中止本次调用
        // 该语义由 JsBackupPluginAdapter.callBackup 的实参保证，这里锁定常量不被改小
        assertTrue(JsScriptRuntime.BACKUP_CALL_TIMEOUT_MS >= 300_000L)
    }
}
