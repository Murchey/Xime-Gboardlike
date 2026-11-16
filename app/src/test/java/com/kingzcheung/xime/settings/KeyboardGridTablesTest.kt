package com.kingzcheung.xime.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeyboardGridTablesTest {

    private fun faces(rows: List<List<KeyboardGridTables.Key>>) =
        rows.map { r -> r.map { it.face } }

    @Test
    fun `14 键与参考实现 msime 的键面一致`() {
        assertEquals(
            listOf(
                listOf("QW", "ER", "TY", "UI", "OP"),
                listOf("AS", "DF", "GH", "JK", "L"),
                listOf("ZX", "CV", "BN", "M"),
            ),
            faces(KeyboardGridTables.ROWS_14_QWERTY)
        )
    }

    @Test
    fun `18 键与参考实现 msime 的键面一致`() {
        assertEquals(
            listOf(
                listOf("Q", "WE", "RT", "Y", "U", "IO", "P"),
                listOf("A", "SD", "FG", "H", "JK", "L"),
                listOf("Z", "XC", "V", "BN", "M"),
            ),
            faces(KeyboardGridTables.ROWS_18_QWERTY)
        )
    }

    @Test
    fun `每套组键都正好覆盖全部字母各一次`() {
        listOf("14", "17", "18").forEach { layout ->
            val rows = KeyboardGridTables.rowsFor(layout)
            val letters = rows.flatten().joinToString("") { it.letters }
            assertEquals("$layout 键字母数", 26, letters.length)
            assertEquals("$layout 键字母不重复", ('a'..'z').toList(), letters.toList().sorted())
            assertEquals("$layout 键数", layout.toInt(), rows.sumOf { it.size })
        }
    }

    @Test
    fun `组码是组内首字母`() {
        val rows = KeyboardGridTables.ROWS_14_QWERTY
        assertEquals('q', KeyboardGridTables.groupCode(rows, 'q'))
        assertEquals('q', KeyboardGridTables.groupCode(rows, 'w'))
        assertEquals('e', KeyboardGridTables.groupCode(rows, 'r'))
        assertEquals('b', KeyboardGridTables.groupCode(rows, 'n'))
        assertEquals('m', KeyboardGridTables.groupCode(rows, 'm'))
        assertNull(KeyboardGridTables.groupCode(rows, '1'))
        assertEquals("大写字母也归到同一组码（组码统一小写）", 'q', KeyboardGridTables.groupCode(rows, 'Q'))
    }

    @Test
    fun `ABCDE 排布按字母序且行数与键数一致`() {
        listOf("14", "17", "18").forEach { layout ->
            val shape = KeyboardGridTables.shapeOf(layout)
            val rows = KeyboardGridTables.abcdeRows(shape)
            assertEquals("$layout 行数", shape, rows.map { it.size })
            val letters = rows.flatten().joinToString("") { it.letters }
            assertEquals("$layout ABCDE 字母表", ('a'..'z').joinToString(""), letters)
            rows.flatten().forEach { key -> assertTrue("组码必须是首字母", key.code == key.letters[0]) }
        }
    }

    @Test
    /** 双字母键按行均摊：14 键三行各 4 个双字母键 + 1 个单字母键（末行 4 个都是双字母）。 */
    fun `14 键 ABCDE 的具体分组`() {
        val rows = KeyboardGridTables.abcdeRows(KeyboardGridTables.shapeOf("14"))
        assertEquals(
            listOf(
                listOf("AB", "CD", "EF", "GH", "I"),
                listOf("JK", "LM", "NO", "PQ", "R"),
                listOf("ST", "UV", "WX", "YZ"),
            ),
            faces(rows)
        )
    }
}
