package com.kingzcheung.xime.glide

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 验证控制器确实优先使用神经来源，并在其返回 null 时回退几何解码。 */
class GlideNeuralProviderTest {

    private val host = "host-1"
    private val schema = "double_pinyin_flypy@v1"

    private class Lookup : GlideSentenceLookup {
        var input: String? = null
        override fun lookup(rawInput: String, maxCount: Int): GlideLookupResult {
            input = rawInput
            return GlideLookupResult.Candidates(
                listOf(GlideCandidate("c0", "你好", rawInput)),
            )
        }
    }

    private class Committer : GlideCommitter {
        override fun installRawInput(rawInput: String): Boolean = true
        override fun selectCandidate(rawInput: String, text: String): Boolean = true
    }

    private fun registry(): GlideGeometryRegistry {
        val registry = GlideGeometryRegistry()
        registry.beginFrame(1000f, 300f)
        val rows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
        rows.forEachIndexed { row, letters ->
            letters.forEachIndexed { col, ch ->
                val left = col * 100f
                val top = row * 100f
                registry.register(
                    GlideKey("k" + row + "_" + col, ch.toString(),
                        GlideKeyRect(left, top, left + 100f, top + 100f)),
                )
            }
        }
        return registry
    }

    private fun swipe(controller: GlideKeyboardController, code: String) {
        var first = true
        var previous: Pair<Float, Float>? = null
        for (ch in code) {
            val index = "qwertyuiopasdfghjklzxcvbnm".indexOf(ch)
            val cx = (index % 10) * 100f + 50f
            val cy = (index / 10) * 100f + 50f
            previous?.let { p ->
                for (k in 1..5) {
                    val f = k / 6f
                    controller.onMove(1, p.first + (cx - p.first) * f, p.second + (cy - p.second) * f, false)
                }
            }
            if (first) {
                controller.onDown(1, cx, cy, host, schema)
                first = false
            } else {
                controller.onMove(1, cx, cy, false)
            }
            previous = cx to cy
        }
    }

    /** 记录是否被调用：用于验证短词根本不会触发模型推理。 */
    private class RecordingProvider : GlideHypothesisProvider {
        var called = false
        override fun hypotheses(
            points: List<GlidePoint>,
            geometry: GlideGeometrySnapshot,
            limit: Int,
            spellings: Collection<String>?,
            densityDpi: Float,
        ): List<GlideGeometryDecoder.Hypothesis> {
            called = true
            return listOf(GlideGeometryDecoder.Hypothesis("nihao", 0f, GlideHypothesisOrigin.NEURAL))
        }
    }

    @Test
    fun neuralUsedForLongWord() {
        val lookup = Lookup()
        val provider = RecordingProvider()
        val controller = GlideKeyboardController(
            registry(), GlideCoordinator(lookup, Committer()),
            glideEnabled = { true }, neuralProvider = provider,
        )
        // 超过三个字的长词才走神经解码
        swipe(controller, "jintiantianqi")
        val up = controller.onUp(1, host)
        assertTrue(up is GlideKeyboardController.UpResult.Candidates)
        assertTrue("长词应当调用神经解码", provider.called)
        assertEquals("nihao", lookup.input)
        assertTrue((up as GlideKeyboardController.UpResult.Candidates).outcome
            is GlideCoordinator.Outcome.ShowCandidates)
    }

    @Test
    fun shortWordSkipsNeural() {
        // 短词直接几何解码，不跑模型（按音节数分流，不依赖具体轨迹解码结果）
        assertTrue(!shouldRunNeuralFor(listOf(hypothesis("nihao"))))
        assertTrue(!shouldRunNeuralFor(listOf(hypothesis("jintiantian"))))
        assertTrue(shouldRunNeuralFor(listOf(hypothesis("jintiantianqi"))))
        // 没有任何几何假设时也不跑模型（跑了也没得比）
        assertTrue(!shouldRunNeuralFor(emptyList()))
    }

    private fun hypothesis(letters: String) =
        GlideGeometryDecoder.Hypothesis(letters, 0f, GlideHypothesisOrigin.GEOMETRY)

    @Test
    fun fallsBackToGeometry() {
        val lookup = Lookup()
        val provider = object : GlideHypothesisProvider {
            override fun hypotheses(
                points: List<GlidePoint>,
                geometry: GlideGeometrySnapshot,
                limit: Int,
                spellings: Collection<String>?,
                densityDpi: Float,
            ): List<GlideGeometryDecoder.Hypothesis>? = null
        }
        val controller = GlideKeyboardController(
            registry(), GlideCoordinator(lookup, Committer()),
            glideEnabled = { true }, neuralProvider = provider,
        )
        swipe(controller, "nihao")
        val up = controller.onUp(1, host)
        assertTrue(up is GlideKeyboardController.UpResult.Candidates)
        assertNotNull(lookup.input)
    }
}
