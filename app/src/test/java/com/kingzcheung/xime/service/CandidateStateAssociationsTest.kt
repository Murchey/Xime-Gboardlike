package com.kingzcheung.xime.service

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** 光标移动后联想必须退出（旧联想只对原光标位置成立）。 */
class CandidateStateAssociationsTest {

    @Test
    fun associationsAreCleared() {
        val state = CandidateState(candidates = listOf("你好"), associationCandidates = listOf("吗", "呢"))
        assertTrue(state.withoutAssociations().associationCandidates.isEmpty())
    }

    @Test
    fun otherFieldsArePreserved() {
        val state = CandidateState(
            candidates = listOf("你好"),
            inputText = "nihao",
            isComposing = true,
            associationCandidates = listOf("吗"),
            preeditCaretPos = 2,
        )
        val after = state.withoutAssociations()
        assertEquals(state.candidates, after.candidates)
        assertEquals("nihao", after.inputText)
        assertTrue(after.isComposing)
        assertEquals(2, after.preeditCaretPos)
    }

    @Test
    fun noAssociationsMeansNoStateWrite() {
        val state = CandidateState(candidates = listOf("你好"))
        assertSame(state, state.withoutAssociations())
    }
}
