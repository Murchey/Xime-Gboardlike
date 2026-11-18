package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 字母长按变体表：大小写 + 注音/变音字符（与 fcitx5-android PopupPreset 同源）。 */
class LetterLongPressVariantsTest {

    @Test
    fun `26个字母都有变体且不含自身小写`() {
        for (ch in 'a'..'z') {
            val variants = LetterLongPressVariants.forLetter(ch)
            assertTrue("$ch 没有变体", variants.isNotEmpty())
            assertFalse("$ch 的变体不应包含自身", variants.contains(ch.toString()))
            assertEquals("$ch 变体有重复", variants.distinct(), variants)
            assertTrue("$ch 缺大写", variants.contains(ch.uppercaseChar().toString()))
        }
    }

    @Test
    fun `注音与变音字符齐全`() {
        val a = LetterLongPressVariants.forLetter('a')
        assertTrue(a.containsAll(listOf("á", "ǎ", "à", "ā", "ä", "â")))
        val v = LetterLongPressVariants.forLetter('v')
        assertTrue(v.containsAll(listOf("ǖ", "ǘ", "ǚ", "ǜ")))
        // fcitx5 同源：e 的变体最多（含 ə），f 只有大写
        assertTrue(LetterLongPressVariants.forLetter('e').contains("ə"))
        assertEquals(listOf("F"), LetterLongPressVariants.forLetter('f'))
    }

    @Test
    fun `大写输入取同一张表只支持单字母`() {
        assertEquals(LetterLongPressVariants.forLetter('a'), LetterLongPressVariants.forLetter('A'))
        assertTrue(LetterLongPressVariants.supports("a"))
        assertTrue(LetterLongPressVariants.supports("A"))
        assertFalse(LetterLongPressVariants.supports("ab"))
        assertFalse(LetterLongPressVariants.supports("1"))
    }
}
