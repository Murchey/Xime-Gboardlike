package com.kingzcheung.xime.clipboard.sync.objstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TombstoneStoreTest {

    @Test
    fun `空或畸形输入回退为空列表`() {
        assertTrue(TombstoneStore.decode(null).isEmpty())
        assertTrue(TombstoneStore.decode("").isEmpty())
        assertTrue(TombstoneStore.decode("not-json").isEmpty())
    }

    @Test
    fun `编解码往返`() {
        val list = listOf(
            ClipboardTombstone("u1", 100L),
            ClipboardTombstone("u2", 200L),
        )
        val decoded = TombstoneStore.decode(TombstoneStore.encode(list))
        assertEquals(2, decoded.size)
        assertEquals(100L, decoded.first { it.uuid == "u1" }.deletedAt)
    }

    @Test
    fun `编码时按 uuid 去重保留最新 deletedAt`() {
        val encoded = TombstoneStore.encode(
            listOf(ClipboardTombstone("u1", 1L), ClipboardTombstone("u1", 9L))
        )
        val decoded = TombstoneStore.decode(encoded)
        assertEquals(1, decoded.size)
        assertEquals(9L, decoded[0].deletedAt)
    }

    @Test
    fun `解码跳过缺 uuid 的脏数据`() {
        val decoded = TombstoneStore.decode(
            """[{"deletedAt":1},{"uuid":"ok","deletedAt":2}]"""
        )
        assertEquals(1, decoded.size)
        assertEquals("ok", decoded[0].uuid)
    }

    @Test
    fun `编码结果按 uuid 排序`() {
        val decoded = TombstoneStore.decode(TombstoneStore.encode(
            listOf(ClipboardTombstone("z", 1), ClipboardTombstone("a", 2))
        ))
        assertEquals(listOf("a", "z"), decoded.map { it.uuid })
    }
}
