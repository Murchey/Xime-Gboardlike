package com.kingzcheung.xime.objectstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObjectStorageConfigTest {

    @Test
    fun `默认私有读写且协议为 COS`() {
        val c = ObjectStorageConfig()
        assertEquals(StorageProtocol.COS, c.protocol)
        assertTrue(c.privateAuth)
        assertFalse(c.isConfigured)
    }

    @Test
    fun `host 去掉协议头与路径`() {
        val c = ObjectStorageConfig(endpoint = "https://demo.cos.ap-shanghai.myqcloud.com/sub/path?x=1")
        assertEquals("demo.cos.ap-shanghai.myqcloud.com", c.host)
    }

    @Test
    fun `显式 http 被尊重`() {
        val c = ObjectStorageConfig(endpoint = "http://127.0.0.1:9000")
        assertFalse(c.useHttps)
        assertEquals("http", c.scheme)
    }

    @Test
    fun `isConfigured 需要 host 与两把 key`() {
        assertFalse(ObjectStorageConfig(endpoint = "https://a.cos.x.com", accessKeyId = "a").isConfigured)
        assertFalse(ObjectStorageConfig(endpoint = "https://a.cos.x.com", secretKey = "s").isConfigured)
        assertTrue(ObjectStorageConfig(
            endpoint = "https://a.cos.x.com", accessKeyId = "a", secretKey = "s"
        ).isConfigured)
    }

    @Test
    fun `bucketLabel 优先用显式 bucket`() {
        assertEquals("myb", ObjectStorageConfig(bucket = "myb", endpoint = "x.cos.y.com").bucketLabel)
    }

    @Test
    fun `协议 prefValue 往返`() {
        for (p in StorageProtocol.entries) assertEquals(p, StorageProtocol.fromPrefValue(p.prefValue))
    }

    @Test
    fun `objectPath 分段编码保留斜杠`() {
        assertEquals("/XimeGboard/content/a%20b.json",
            ObjectStorageClient.objectPath("XimeGboard/content/a b.json"))
    }

    @Test
    fun `objectPath 空 key 返回根`() {
        assertEquals("/", ObjectStorageClient.objectPath(""))
    }
}
