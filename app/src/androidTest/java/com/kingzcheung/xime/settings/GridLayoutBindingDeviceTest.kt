package com.kingzcheung.xime.settings

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * 14/17/18 键与 xime.yaml 键面段的绑定：方案 → section → 行 → 每个键点按送出的组码。
 *
 * 触摸路径没法在单测里模拟，但这几段都是可断言的：方案映射到哪个 section、
 * section 的行键 id、以及每个合并键的 tap.value（按下去真正送给引擎的字母）。
 */
@RunWith(AndroidJUnit4::class)
class GridLayoutBindingDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Before
    fun setUp() {
        // 方案 → section 的绑定表在 loadConfig 之后才有，断言前必须先加载
        KeysConfigHelper.loadConfig(context)
    }

    private fun rowsOf(schemaId: String): List<List<String>> {
        KeysConfigHelper.setActiveKeyboardSchema(schemaId)
        return KeysConfigHelper.getRowsLayout(false).rows.map { it.keys }
    }

    @Test
    fun abcde14RowsAndGroupCodes() {
        assertEquals("qwerty_14_abcde", KeysConfigHelper.mergedSectionForSchema("pinyin_14jian_abcde"))
        val rows = rowsOf("pinyin_14jian_abcde")
        assertEquals(listOf(listOf("ab", "cd", "ef", "gh", "i")), rows.take(1))
        assertEquals(listOf("jk", "lm", "no", "pq", "r"), rows[1])
        assertEquals(listOf("st", "uv", "wx", "yz"), rows[2])

        assertEquals("组内首字母就是送出的组码", "w", KeysConfigHelper.getKeyGesture("wx")?.tap?.value)
        assertEquals("n", KeysConfigHelper.getKeyGesture("no")?.tap?.value)
        assertEquals("a", KeysConfigHelper.getKeyGesture("ab")?.tap?.value)
        val single = KeysConfigHelper.getKeyGesture("i")
        assertNotNull(single)
        assertEquals("i", single?.tap?.value)
    }

    @Test
    fun qwerty14RowsAndGroupCodes() {
        assertEquals("qwerty_14", KeysConfigHelper.mergedSectionForSchema("pinyin_14jian"))
        val rows = rowsOf("pinyin_14jian")
        assertEquals(listOf("qw", "er", "ty", "ui", "op"), rows[0])
        assertEquals(listOf("as", "df", "gh", "jk", "l"), rows[1])
        assertEquals(listOf("zx", "cv", "bn", "m"), rows[2])
        assertEquals("q", KeysConfigHelper.getKeyGesture("qw")?.tap?.value)
        assertEquals("o", KeysConfigHelper.getKeyGesture("op")?.tap?.value)
    }

    @Test
    fun abcde18RowsAndGroupCodes() {
        assertEquals("qwerty_18_abcde", KeysConfigHelper.mergedSectionForSchema("pinyin_18jian_abcde"))
        val rows = rowsOf("pinyin_18jian_abcde")
        assertEquals(listOf("ab", "cd", "ef", "g", "h", "i", "j"), rows[0])
        assertEquals(listOf("kl", "mn", "op", "q", "r", "s"), rows[1])
        assertEquals(listOf("tu", "vw", "x", "y", "z"), rows[2])
        assertEquals("v", KeysConfigHelper.getKeyGesture("vw")?.tap?.value)
        assertEquals("o", KeysConfigHelper.getKeyGesture("op")?.tap?.value)
    }
}