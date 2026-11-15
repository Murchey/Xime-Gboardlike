package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationPostFilterTest {

    private fun c(text: String) = AssociationCandidate(text, 1f)

    @Test
    fun `纯标点候选整条丢掉`() {
        val out = AssociationPostFilter.dropPunctuation(
            listOf(c("，"), c("。"), c("！？"), c("不"), c("不好"))
        )
        assertEquals(listOf("不", "不好"), out.map { it.text })
    }

    @Test
    fun `含文字的混合候选保留`() {
        val out = AssociationPostFilter.dropPunctuation(listOf(c("A"), c("好！"), c("1"), c("，")))
        assertEquals(listOf("A", "好！", "1"), out.map { it.text })
    }

    @Test
    fun `全标点时返回空表`() {
        val out = AssociationPostFilter.dropPunctuation(listOf(c("，"), c("……"), c(" ")))
        assertTrue(out.isEmpty())
    }

    @Test
    fun `空表原样返回`() {
        assertTrue(AssociationPostFilter.dropPunctuation(emptyList()).isEmpty())
    }

    @Test
    fun `isPunctuationOnly 边界`() {
        assertTrue(AssociationPostFilter.isPunctuationOnly("，"))
        assertTrue(AssociationPostFilter.isPunctuationOnly("……"))
        assertFalse(AssociationPostFilter.isPunctuationOnly("不"))
        assertFalse(AssociationPostFilter.isPunctuationOnly("不好"))
        assertFalse(AssociationPostFilter.isPunctuationOnly(""))
    }

    @Test
    fun `过滤不改变顺序与非标点的分数`() {
        val list = listOf(
            AssociationCandidate("，", 9f),
            AssociationCandidate("不", 8f),
            AssociationCandidate("好", 7f)
        )
        val out = AssociationPostFilter.dropPunctuation(list)
        assertEquals(listOf("不", "好"), out.map { it.text })
        assertEquals(listOf(8f, 7f), out.map { it.score })
    }
}
