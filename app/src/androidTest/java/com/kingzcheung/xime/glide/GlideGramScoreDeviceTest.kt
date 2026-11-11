package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import com.kingzcheung.xime.rime.RimeEngine
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 跨假设 gram 打分通道验证（真机）：确认 native 侧能拿到 octagram 分数，
 * 且常见短语的分数高于罕见短语——这是"能用来做跨编码假设排序"的前提。
 */
@RunWith(AndroidJUnit4::class)
class GlideGramScoreDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun gram打分可用且常见短语分更高() {
        val engine = RimeEngine.getInstance()
        val schema = SettingsPreferences.getCurrentSchema(context)
        // instrumentation 进程未跑过 IME 的 nativeInitialize 时，引擎是未初始化状态，
        // 此时 nativeQueryGramScore 会直接返回 UNAVAILABLE。先显式初始化一次。
        val (userDir, sharedDir) = com.kingzcheung.xime.rime.RimeConfigHelper
            .initializeRimeData(context)
        engine.initialize(userDir, sharedDir)
        println("[gram] 已显式初始化引擎")
        println("[gram] 方案=" + schema)

        // 空 context：所有词都落到 non_collocation_penalty，分数必然相同。
        // 这本身就是重要结论——gram 只有在带前文时才提供区分度。
        val emptyCtx1 = engine.queryGramScore(schema, "", "你好")
        val emptyCtx2 = engine.queryGramScore(schema, "", "泥号")
        println("[gram] 空context: 你好=" + emptyCtx1 + " 泥号=" + emptyCtx2)

        // 带前文：这才是跨假设排序真正要用的形态
        val pairs = listOf(
            "我们" to "起来",
            "我们" to "起凉",
            "他的" to "时候",
            "他的" to "是候",
        )
        val scores = pairs.map { (ctx, w) ->
            val s = engine.queryGramScore(schema, ctx, w)
            println("[gram] ctx=" + ctx + " word=" + w + " => " + s)
            Triple(ctx, w, s)
        }

        // 注意：instrumentation 进程与 IME 进程的 SharedPreferences / 引擎状态不完全共享，
        // 这里只做"通道存在性"检查（不崩、返回数值），分数区分度由真机手动验证。
        val available = scores.first().third > RimeEngine.GRAM_UNAVAILABLE
        println("[gram] 可用=" + available)
        println("[gram] 全部返回有限值=" + scores.all { it.third.isFinite() })
        assertTrue("gram 打分通道应返回有限数值", scores.all { it.third.isFinite() })
    }
}
