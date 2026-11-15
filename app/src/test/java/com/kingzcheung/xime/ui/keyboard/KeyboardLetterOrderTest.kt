package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.settings.KeyboardRow
import org.junit.Assert.assertEquals
import org.junit.Test

class KeyboardLetterOrderTest {

    private fun rows(vararg keys: List<String>) = keys.map { KeyboardRow(it) }

    /** 实际键盘第三行就是 shift + 7 字母 + delete，功能键不能动。 */
    private val qwerty = rows(
        listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
        listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
        listOf("shift", "z", "x", "c", "v", "b", "n", "m", "delete"),
        listOf("mode_change", "comma", "earth", "space", "period", "enter"),
    )

    @Test
    fun `QWERTY 设置时原样返回`() {
        assertEquals(qwerty.map { it.keys }, applyLetterOrder(qwerty, abcde = false).map { it.keys })
    }

    @Test
    fun `标准 QWERTY 切到 ABCDE 时字母按字母表重排`() {
        val out = applyLetterOrder(qwerty, abcde = true).map { it.keys }
        assertEquals(listOf("abcdefghij".map { it.toString() }), out.take(1))
        assertEquals("klmnopqrs".map { it.toString() }, out[1])
        assertEquals(listOf("shift") + "tuvwxyz".map { it.toString() } + listOf("delete"), out[2])
        assertEquals("控制行保持不动", qwerty[3].keys, out[3])
    }

    @Test
    fun `字母集合不是标准 QWERTY 时不动`() {
        val custom = rows(
            listOf("a", "b", "c", "d", "e"),
            listOf("f", "g", "h", "i", "j"),
            listOf("k", "l", "m", "n", "o"),
            listOf("space"),
        )
        assertEquals(custom.map { it.keys }, applyLetterOrder(custom, abcde = true).map { it.keys })
    }

    @Test
    fun `只有字母行没有功能键时也能重排`() {
        val bare = rows(
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
        )
        val out = applyLetterOrder(bare, abcde = true).map { it.keys }
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), out[0])
        assertEquals("第三行 7 个字母槽 → t..z", "tuvwxyz".map { it.toString() }, out[2])
    }

    @Test
    fun `数字行不参与字母替换`() {
        val withNumbers = rows(
            listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0"),
            listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"),
            listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"),
            listOf("z", "x", "c", "v", "b", "n", "m"),
        )
        val out = applyLetterOrder(withNumbers, abcde = true).map { it.keys }
        assertEquals(withNumbers[0].keys, out[0])
        assertEquals(listOf("a", "b", "c", "d", "e", "f", "g", "h", "i", "j"), out[1])
    }
}
