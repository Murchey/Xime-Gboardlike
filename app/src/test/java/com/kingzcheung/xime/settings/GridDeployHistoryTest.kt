package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 异体键盘部署历史：只保留最近三次，更早的整套删除；重复部署提到最新。 */
class GridDeployHistoryTest {

    @Test
    fun `超过三套时裁剪最旧的`() {
        val (keep, evicted) = KeyboardLayoutApplier.trimGridHistory(
            history = listOf("a", "b", "c", "d"),
            target = "d",
            keep = 3,
        )
        assertEquals(listOf("b", "c", "d"), keep)
        assertEquals(listOf("a"), evicted)
    }

    @Test
    fun `重复部署同一套提到最新`() {
        val (keep, evicted) = KeyboardLayoutApplier.trimGridHistory(
            history = listOf("a", "b", "c"),
            target = "a",
            keep = 3,
        )
        assertEquals(listOf("b", "c", "a"), keep)
        assertEquals(emptyList<String>(), evicted)
    }

    @Test
    fun `不足三套不删除`() {
        val (keep, evicted) = KeyboardLayoutApplier.trimGridHistory(
            history = emptyList(),
            target = "a",
            keep = 3,
        )
        assertEquals(listOf("a"), keep)
        assertEquals(emptyList<String>(), evicted)
    }
}