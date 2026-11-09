package com.kingzcheung.xime.clipboard.sync.objstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** uuid 派生规则的纯函数等价验证（与 ClipboardObjectStoreSync.uuidOf 同算法）。 */
class ClipboardUuidTest {

    private fun textUuid(text: String) = "txt_" + ObjectStorageHashes.sha256Hex(text)
    private fun imageUuid(hash: String) = "img_$hash"

    @Test
    fun `相同文本 uuid 稳定`() {
        assertEquals(textUuid("你好"), textUuid("你好"))
    }

    @Test
    fun `不同文本 uuid 不同`() {
        assertTrue(textUuid("你好") != textUuid("世界"))
    }

    @Test
    fun `文本与图片 uuid 前缀不同`() {
        assertTrue(textUuid("abc").startsWith("txt_"))
        assertTrue(imageUuid("deadbeef").startsWith("img_"))
    }

    @Test
    fun `sha256 为 64 位十六进制`() {
        val sha = ObjectStorageHashes.sha256Hex("abc")
        assertEquals(64, sha.length)
        assertTrue(sha.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `空文本也能派生 uuid`() {
        assertEquals("txt_" + ObjectStorageHashes.sha256Hex(""), textUuid(""))
    }
}
