package com.kingzcheung.xime.association

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AssociationPhraseGroupingTest {

    private fun c(text: String, score: Float = 1f) = AssociationCandidate(text, score)

    @Test
    fun `词候选紧跟它的首字`() {
        val list = listOf(c("不"), c("有"), c("是"), c("有些"))
        val out = AssociationPostFilter.groupPhrasesWithSeeds(list)
        assertEquals(listOf("不", "有", "有些", "是"), out.map { it.text })
    }

    @Test
    fun `一个首字带多个词时按原相对顺序`() {
        val list = listOf(c("不"), c("是"), c("不好"), c("不错"))
        val out = AssociationPostFilter.groupPhrasesWithSeeds(list)
        assertEquals(listOf("不", "不好", "不错", "是"), out.map { it.text })
    }

    @Test
    fun `首字不在候选里时词留在原位`() {
        val list = listOf(c("是"), c("天天"), c("不"))
        val out = AssociationPostFilter.groupPhrasesWithSeeds(list)
        assertEquals(listOf("是", "天天", "不"), out.map { it.text })
    }

    @Test
    fun `全是单字时原样返回`() {
        val list = listOf(c("不"), c("是"), c("有"))
        assertEquals(list.map { it.text }, AssociationPostFilter.groupPhrasesWithSeeds(list).map { it.text })
    }

    @Test
    fun `不增删候选也不改分数`() {
        val list = listOf(c("不", 8f), c("是", 7f), c("不好", 6.5f), c("有些", 5f))
        val out = AssociationPostFilter.groupPhrasesWithSeeds(list)
        assertEquals(list.size, out.size)
        assertEquals(list.map { it.text }.toSet(), out.map { it.text }.toSet())
        assertEquals(6.5f, out.first { it.text == "不好" }.score)
    }

    @Test
    fun `空表与单个候选安全`() {
        assertTrue(AssociationPostFilter.groupPhrasesWithSeeds(emptyList()).isEmpty())
        assertEquals(listOf("不"), AssociationPostFilter.groupPhrasesWithSeeds(listOf(c("不"))).map { it.text })
    }
}
