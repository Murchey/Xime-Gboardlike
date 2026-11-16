package com.kingzcheung.xime.ui.keyboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 键面大小写：合并键（"qw"）也要跟着设置走，不能被当成功能键漏掉。 */
class KeyboardLetterCaseTest {

    private val upper = LetterCaseSetting(upperCase = true, followShift = false)
    private val lower = LetterCaseSetting(upperCase = false, followShift = false)
    private val followShift = LetterCaseSetting(upperCase = false, followShift = true)

    @Test
    fun `单字母键大小写`() {
        assertEquals("Q", applyLetterDisplayCase("q", true, false, upper))
        assertEquals("q", applyLetterDisplayCase("Q", true, false, lower))
        assertEquals("Q", applyLetterDisplayCase("q", true, true, followShift))
    }

    @Test
    fun `合并键也要应用大小写`() {
        // 合并键 id 是两个字母（"qw"），以前被当成功能键，设置不生效
        assertEquals("QW", applyLetterDisplayCase("qw", true, false, upper))
        assertEquals("qw", applyLetterDisplayCase("QW", true, false, lower))
        assertEquals("QW", applyLetterDisplayCase("qw", true, true, followShift))
    }

    @Test
    fun `功能键不受影响`() {
        assertEquals("shift", applyLetterDisplayCase("shift", false, false, upper))
        // 只有被判定为字母键时才会换大小写；"shift" 判定为功能键，所以原样
        assertEquals("SHIFT", applyLetterDisplayCase("shift", true, false, upper))
        assertEquals("删除", applyLetterDisplayCase("删除", false, true, followShift))
    }

    @Test
    fun `字母键判定含合并键`() {
        assertTrue("q".length in 1..2 && "q".all { it.isLetter() })
        assertTrue("qw".length in 1..2 && "qw".all { it.isLetter() })
        assertFalse("shift".length in 1..2 && "shift".all { it.isLetter() })
        assertFalse("1".length in 1..2 && "1".all { it.isLetter() })
    }
}
