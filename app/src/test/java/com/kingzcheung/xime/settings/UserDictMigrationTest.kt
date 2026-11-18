package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Test

/** 多本 → 一本迁移：只合并目标库以外的旧库，去重、忽略空名。 */
class UserDictMigrationTest {

    @Test
    fun `排除目标库并去重`() {
        val books = UserDictMigration.booksToMerge(
            listOf("rime_ice", "double_pinyin", " t9_pinyin ", "double_pinyin", ""),
            "rime_ice",
        )
        assertEquals(listOf("double_pinyin", "t9_pinyin"), books)
    }

    @Test
    fun `只有目标库时无事可做`() {
        assertEquals(emptyList<String>(), UserDictMigration.booksToMerge(listOf("rime_ice"), "rime_ice"))
    }

    @Test
    fun `目标库名不同也能正确排除`() {
        assertEquals(
            listOf("a", "b"),
            UserDictMigration.booksToMerge(listOf("a", "custom", "b"), "custom"),
        )
    }
}
