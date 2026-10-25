package com.kingzcheung.xime.viewmodel

import org.junit.Assert.assertEquals
import org.junit.Test

class SchemaSwitchLabelTest {

    @Test
    fun `按钮文字取当前状态`() {
        val sw = SchemaSwitchUiState(
            name = "full_shape",
            states = listOf("半角", "全角"),
            currentIndex = 1,
        )
        assertEquals("全角", sw.currentLabel())
        assertEquals("全", sw.currentIcon())
    }

    @Test
    fun `有 abbrev 时图标取当前缩写`() {
        val sw = SchemaSwitchUiState(
            name = "search_single_char",
            states = listOf("正常", "单字"),
            abbrev = listOf("词", "单"),
            currentIndex = 1,
        )
        assertEquals("单字", sw.currentLabel())
        assertEquals("单", sw.currentIcon())
    }

    @Test
    fun `越界与空状态安全`() {
        val sw = SchemaSwitchUiState(states = listOf("简", "繁"), currentIndex = 99)
        assertEquals("繁", sw.currentLabel())
        val empty = SchemaSwitchUiState()
        assertEquals("", empty.currentLabel())
        assertEquals("", empty.currentIcon())
    }
}
