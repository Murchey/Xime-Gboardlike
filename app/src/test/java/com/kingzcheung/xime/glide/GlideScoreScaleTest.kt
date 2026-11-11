package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 排序分项的量纲守护：每一项都必须是 ln 概率（自然对数），才能直接相加。
 *
 * 旧实现是五项不同量纲相加（0.45 / 0.15 / 1.1 都是手感常数），
 * 导致几何难分、语义明显时无解，也无法解释各权重为什么取那个值。
 */
class GlideScoreScaleTest {

    private fun controller(): GlideKeyboardController {
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 300f)
        listOf("qwertyuiop", "asdfghjkl", "zxcvbnm").forEachIndexed { r, row ->
            row.forEachIndexed { c, ch ->
                registry.register(GlideKey(ch.toString(), ch.toString(),
                    GlideKeyRect(c * 100f, r * 100f, c * 100f + 100f, r * 100f + 100f)))
            }
        }
        return GlideKeyboardController(registry, GlideCoordinator(
            object : GlideSentenceLookup {
                override fun lookup(rawInput: String, maxCount: Int) = GlideLookupResult.NoCandidate
            },
            object : GlideCommitter {
                override fun installRawInput(rawInput: String) = true
                override fun selectCandidate(rawInput: String, text: String) = true
            }), glideEnabled = { true })
    }

    private fun invokeInt(ctl: GlideKeyboardController, name: String, a: Int): Double {
        val m = GlideKeyboardController::class.java.getDeclaredMethod(name, Integer.TYPE)
        m.isAccessible = true
        return m.invoke(ctl, a) as Double
    }

    private fun invokeThree(ctl: GlideKeyboardController, name: String, a: Int, b: Int, c: Int): Double {
        val m = GlideKeyboardController::class.java.getDeclaredMethod(
            name, Integer.TYPE, Integer.TYPE, Integer.TYPE)
        m.isAccessible = true
        return m.invoke(ctl, a, b, c) as Double
    }

    @Test
    fun rime位次惩罚应是对数尺度() {
        val ctl = controller()
        val p0 = invokeInt(ctl, "rimeRankLogProb", 0)
        val p1 = invokeInt(ctl, "rimeRankLogProb", 1)
        val p2 = invokeInt(ctl, "rimeRankLogProb", 2)
        assertEquals("首位不应有惩罚", 0.0, p0, 1e-9)
        assertTrue("位次惩罚应随位次递增", p1 > p0 && p2 > p1)
        assertEquals("应为线性 log 步长", p2, p1 * 2, 1e-9)
        println("[scale] rime logP: p0=" + p0 + " p1=" + p1 + " p2=" + p2)
    }

    @Test
    fun 短语连贯项应按对数几率折算() {
        val ctl = controller()
        val none = invokeThree(ctl, "phraseLogProb", 0, 0, 0)
        val one = invokeThree(ctl, "phraseLogProb", 1, 0, 0)
        assertEquals("无支撑时无增益", 0.0, none, 1e-9)
        assertEquals("一条支撑应约等于 ln2", 0.6931, one, 1e-3)
        val penalized = invokeThree(ctl, "phraseLogProb", 0, 0, 1)
        assertTrue("碎词兜底应扣分", penalized < 0)
        println("[scale] phrase logP: 0支撑=" + none + " 1支撑=" + one + " 1碎词=" + penalized)
    }
}
