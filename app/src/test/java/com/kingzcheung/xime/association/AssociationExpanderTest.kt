package com.kingzcheung.xime.association

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationExpanderTest {

    private fun cands(vararg pairs: Pair<String, Float>) =
        pairs.map { AssociationCandidate(it.first, it.second) }

    @Test
    fun `高置信度续写会拼成双字候选`() = runTest {
        val seeds = cands("不" to 8.0f, "没" to 5.0f)
        val prefixes = ArrayList<String>()
        val out = AssociationExpander.expand("今天天气", seeds) { prefix ->
            prefixes.add(prefix)
            cands("错" to 7.9f, "过" to 4.0f)
        }
        assertEquals(listOf("今天天气不", "今天天气没"), prefixes)
        assertEquals(2, out.size)
        assertEquals("不错", out[0].text)
    }

    @Test
    fun `低置信度不拼词`() = runTest {
        val seeds = cands("不" to 8.0f)
        val flat = cands(
            "错" to 5.0f, "过" to 4.9f, "是" to 4.8f, "要" to 4.7f,
            "好" to 4.6f, "行" to 4.5f, "很" to 4.4f, "吧" to 4.3f
        )
        val out = AssociationExpander.expand("今天天气", seeds) { flat }
        assertTrue("平均分布不该拼词", out.isEmpty())
    }

    @Test
    fun `词候选的分数不会越过自己的首字`() = runTest {
        val seeds = cands("不" to 8.0f)
        val out = AssociationExpander.expand("今天天气", seeds) { cands("错" to 20.0f) }
        assertEquals("不错", out[0].text)
        assertTrue("词必须排在首字之后", out[0].score < 8.0f)
    }

    @Test
    fun `最多只扩展 maxSeeds 个首字`() = runTest {
        var calls = 0
        val seeds = cands("不" to 8.0f, "是" to 7.0f, "没" to 6.0f, "和" to 5.0f)
        AssociationExpander.expand("今天天气", seeds) { _ ->
            calls++
            cands("错" to 7.9f)
        }
        assertEquals(AssociationExpander.DEFAULT.maxSeeds, calls)
    }

    @Test
    fun `标点与英文首字不续写`() = runTest {
        var calls = 0
        val seeds = cands("，" to 9.0f, "a" to 8.0f, "。" to 7.0f)
        val out = AssociationExpander.expand("今天天气", seeds) { _ ->
            calls++
            cands("的" to 7.9f)
        }
        assertEquals(0, calls)
        assertTrue(out.isEmpty())
    }

    @Test
    fun `空上下文或空候选直接返回`() = runTest {
        var calls = 0
        val out1 = AssociationExpander.expand("", cands("不" to 8.0f)) { _ -> calls++; emptyList() }
        val out2 = AssociationExpander.expand("今天", emptyList()) { _ -> calls++; emptyList() }
        assertTrue(out1.isEmpty() && out2.isEmpty())
        assertEquals(0, calls)
    }

    @Test
    fun `bestContinuation 只接受单字且份额达标`() {
        assertNotNull(AssociationExpander.bestContinuation(cands("错" to 9.0f, "过" to 1.0f)))
        assertNull(AssociationExpander.bestContinuation(emptyList()))
        assertNull("双字 token 不当作续写", AssociationExpander.bestContinuation(cands("不错" to 9.0f)))
        assertNull(
            "份额不足（4 个等分 logit，份额 0.25 < 0.30）",
            AssociationExpander.bestContinuation(
                cands("错" to 1.0f, "过" to 1.0f, "是" to 1.0f, "要" to 1.0f)
            )
        )
        assertNotNull(
            "份额刚好达标（3 个等分 logit，份额 0.33 ≥ 0.30）",
            AssociationExpander.bestContinuation(cands("错" to 1.0f, "过" to 1.0f, "是" to 1.0f))
        )
    }

    @Test
    fun `isExpandable 只认单个汉字`() {
        assertTrue(AssociationExpander.isExpandable("不"))
        assertTrue(!AssociationExpander.isExpandable("，"))
        assertTrue(!AssociationExpander.isExpandable("ab"))
        assertTrue(!AssociationExpander.isExpandable("不错"))
        assertTrue(!AssociationExpander.isExpandable(""))
    }
}
