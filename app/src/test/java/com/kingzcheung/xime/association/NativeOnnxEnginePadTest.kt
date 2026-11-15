package com.kingzcheung.xime.association

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class NativeOnnxEnginePadTest {

    @Test
    fun `动态模型不填充`() {
        val ids = longArrayOf(1, 5, 6)
        assertArrayEquals(ids, NativeOnnxEngine.padIds(ids, 0))
    }

    @Test
    fun `长度正好时不改动`() {
        val ids = LongArray(64) { it.toLong() }
        assertArrayEquals(ids, NativeOnnxEngine.padIds(ids, 64))
    }

    @Test
    fun `不足时左侧补 PAD`() {
        val ids = longArrayOf(1, 5, 6)
        val out = NativeOnnxEngine.padIds(ids, 64)
        assertEquals(64, out.size)
        assertEquals(0L, out[0])
        assertEquals(0L, out[60])
        assertEquals(1L, out[61])
        assertArrayEquals(ids, out.copyOfRange(61, 64))
    }

    @Test
    fun `超长时丢最早的字保留末位`() {
        val ids = LongArray(70) { (it + 1).toLong() }
        val out = NativeOnnxEngine.padIds(ids, 64)
        assertEquals(64, out.size)
        assertEquals(ids[6], out[0])
        assertEquals(ids[69], out[63])
    }

    @Test
    fun `自定义 PAD id`() {
        val out = NativeOnnxEngine.padIds(longArrayOf(9), 3, padId = 3L)
        assertArrayEquals(longArrayOf(3, 3, 9), out)
    }
}
