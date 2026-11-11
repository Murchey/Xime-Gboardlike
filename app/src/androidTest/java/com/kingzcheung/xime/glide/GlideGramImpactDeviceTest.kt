package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 端到端验证：gram 的语境分数是否真的能区分同音候选，
 * 并且差值是否足以在 log-domain 排序里压过几何差异。
 */
@RunWith(AndroidJUnit4::class)
class GlideGramImpactDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun 前文能显著改变同音候选的相对得分() {
        val (u, s) = RimeConfigHelper.initializeRimeData(context)
        RimeEngine.getInstance().initialize(u, s)
        val engine = RimeEngine.getInstance()
        val schema = "rime_ice"

        // 同一组候选，只改前文，看分数差如何变化
        val contexts = listOf("", "我们", "他们", "天气")
        val words = listOf("起来", "起凉", "qi来")
        val rows = mutableListOf<String>()
        for (ctx in contexts) {
            val line = words.joinToString(" ") { w ->
                w + "=" + String.format("%.2f", engine.queryGramScore(schema, ctx, w))
            }
            rows += "ctx[" + ctx + "] " + line
            println("[impact] ctx[" + ctx + "] " + line)
        }
        // 断言：同一前文下 起来/起凉 必须有明显差距（说明可用作排序信号）
        val a = engine.queryGramScore(schema, "我们", "起来")
        val b = engine.queryGramScore(schema, "我们", "起凉")
        println("[impact] 差值=" + (a - b))
        val summary = rows.joinToString(" | ") + " || 差值(起来-起凉, ctx=我们)=" + String.format("%.2f", a - b)
        org.junit.Assert.assertTrue(summary, Math.abs(a - b) > 1.0)
    }
}
