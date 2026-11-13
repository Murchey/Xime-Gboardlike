package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 顶部栏提交规则：方案按钮可被拖出删除，删掉后记成隐藏，还能从功能池拖回。 */
class StripCommitTest {

    private val schema = "sys_schema_slot"

    @Test
    fun schemaRemainsVisibleWhenStillInSlots() {
        val commit = commitStripSlots(listOf("a", schema, "b"), maxItems = 5)
        assertTrue(commit.schemaVisible)
        assertEquals(1, commit.schemaIndex)
        assertEquals(listOf("a", "b"), commit.draft)
    }

    @Test
    fun draggingSchemaOutHidesIt() {
        // 拖出栏外：列表里没有它了
        val commit = commitStripSlots(listOf("a", "b"), maxItems = 5)
        assertFalse(commit.schemaVisible)
        assertEquals(listOf("a", "b"), commit.draft)
    }

    @Test
    fun schemaCanBeDraggedBackFromPool() {
        // 从功能池拖回末尾（index 用 Int.MAX_VALUE 时由调用方夹取）
        val commit = commitStripSlots(listOf("a", "b", schema), maxItems = 5)
        assertTrue(commit.schemaVisible)
        assertEquals(2, commit.schemaIndex)
    }

    @Test
    fun extraItemsAreTrimmedFromTheFront() {
        val commit = commitStripSlots(listOf("a", "b", "c", "d", "e", "f", schema), maxItems = 5)
        assertTrue(commit.schemaVisible)
        assertEquals(listOf("b", "c", "d", "e", "f"), commit.draft)
    }

    @Test
    fun schemaOnlyStripKeepsEmptyDraft() {
        val commit = commitStripSlots(listOf(schema), maxItems = 5)
        assertTrue(commit.schemaVisible)
        assertTrue(commit.draft.isEmpty())
    }
}
