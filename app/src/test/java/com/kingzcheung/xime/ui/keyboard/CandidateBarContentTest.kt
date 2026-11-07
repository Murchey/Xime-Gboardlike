package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.clipboard.ClipboardItem
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 候选栏展示映射：截断口径、更多指示、左侧图标。
 * 重点锁「剪贴板候选与图片同源截断」——错位会点图片上屏文本。
 */
class CandidateBarContentTest {

    private fun image(id: Long) = ClipboardItem(
        id = id,
        text = "",
        timestamp = id,
        type = ClipboardEntry.TYPE_IMAGE,
        imagePath = "clipboard_images/$id.png",
        imageHash = "hash$id",
        sizeBytes = 1024,
    )

    @Test
    fun `中文候选按页容量截断，更多指示合并横向可滚动`() {
        val state = CandidateBarState.ChineseCandidates(
            candidates = (0 until 30).map { "c$it" },
            comments = (0 until 30).map { "p$it" },
            inputText = "ni",
            hasMore = false,
        )
        val content = CandidateBarContent.from(state, pageSize = 10, canScrollForward = true)
        assertEquals(10, content.candidates.size)
        // 注释不跟随截断（渲染按 index 取值，保持原长以便 getOrElse）
        assertEquals(30, content.comments.size)
        assertTrue(content.hasAnyMore)
        assertFalse(content.showLeftIcon)
        assertTrue(content.images.isEmpty())
    }

    @Test
    fun `中文候选更多指示取状态与滚动或值`() {
        val state = CandidateBarState.ChineseCandidates(candidates = listOf("你"), hasMore = true)
        assertTrue(CandidateBarContent.from(state, 10, canScrollForward = false).hasAnyMore)
        assertFalse(
            CandidateBarContent.from(
                CandidateBarState.ChineseCandidates(candidates = listOf("你")),
                10,
                canScrollForward = false,
            ).hasAnyMore
        )
    }

    @Test
    fun `空闲态显示左侧图标且无任何条目`() {
        val content = CandidateBarContent.from(CandidateBarState.Idle, 50, false)
        assertTrue(content.candidates.isEmpty())
        assertTrue(content.images.isEmpty())
        assertFalse(content.hasAnyMore)
        assertTrue(content.showLeftIcon)
    }

    @Test
    fun `仅联想态不产出候选条目`() {
        val state = CandidateBarState.AssociationOnly(
            candidates = listOf("世界"), comments = listOf("shijie"), hasMore = true,
        )
        val content = CandidateBarContent.from(state, 50, false)
        assertTrue(content.candidates.isEmpty())
        assertEquals(listOf("shijie"), content.comments)
        assertTrue(content.hasAnyMore)
        assertFalse(content.showLeftIcon)
    }

    @Test
    fun `英文候选按页容量截断`() {
        val state = CandidateBarState.EnglishCandidates(
            candidates = (0 until 25).map { "w$it" },
            comments = (0 until 25).map { "" },
        )
        val content = CandidateBarContent.from(state, pageSize = 12, canScrollForward = true)
        assertEquals(12, content.candidates.size)
        assertFalse(content.hasAnyMore)
    }

    @Test
    fun `剪贴板候选与图片同源截断保持索引对齐`() {
        val items = (1..25).map { if (it % 2 == 0) image(it.toLong()) else ClipboardItem(id = it.toLong(), text = "t$it", timestamp = it.toLong()) }
        val state = CandidateBarState.ClipboardDisplay(
            candidates = items.map { it.text },
            images = items.map { if (it.isImage) it else null },
        )
        val content = CandidateBarContent.from(state, 50, false)
        assertEquals(CandidateBarContent.FIXED_LIMIT, content.candidates.size)
        assertEquals(CandidateBarContent.FIXED_LIMIT, content.images.size)
        assertEquals(content.candidates.size, content.images.size)
        // 第 2 位（下标 1）是图片，截断后仍对齐
        assertEquals(2L, content.images[1]?.id)
        assertNull(content.images[0])
        assertTrue(content.showLeftIcon)
    }

    @Test
    fun `计算器固定上限 20`() {
        val state = CandidateBarState.Calculator(
            candidates = (0 until 30).map { "r$it" },
            comments = (0 until 30).map { "" },
        )
        val content = CandidateBarContent.from(state, pageSize = 5, canScrollForward = false)
        assertEquals(CandidateBarContent.FIXED_LIMIT, content.candidates.size)
        assertFalse(content.hasAnyMore)
    }
}
