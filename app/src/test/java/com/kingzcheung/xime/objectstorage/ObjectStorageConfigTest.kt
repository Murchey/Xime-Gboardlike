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
    fun `私有模式 isConfigured 需要 host 与两把 key`() {
        assertFalse(ObjectStorageConfig(endpoint = "https://a.cos.x.com", accessKeyId = "a").isConfigured)
        assertFalse(ObjectStorageConfig(endpoint = "https://a.cos.x.com", secretKey = "s").isConfigured)
        assertTrue(ObjectStorageConfig(
            endpoint = "https://a.cos.x.com", accessKeyId = "a", secretKey = "s"
        ).isConfigured)
    }

    @Test
    fun `公有 COS 和 OSS 允许匿名且无需单独填写 bucket`() {
        for (protocol in listOf(StorageProtocol.COS, StorageProtocol.OSS)) {
            assertTrue(ObjectStorageConfig(protocol = protocol, endpoint = "bucket.example.com", privateAuth = false).isConfigured)
            assertFalse(ObjectStorageConfig(protocol = protocol, privateAuth = false).isConfigured)
        }
    }

    @Test
    fun `S3 不论公有私有都需要 bucket`() {
        for (privateAuth in listOf(true, false)) {
            val config = ObjectStorageConfig(protocol = StorageProtocol.S3, endpoint = "s3.example.com",
                accessKeyId = "id", secretKey = "secret", privateAuth = privateAuth)
            assertFalse(config.isConfigured)
            assertFalse(config.copy(bucket = " ").isConfigured)
            assertTrue(config.copy(bucket = "bucket").isConfigured)
        }
    }

    @Test
    fun `S3 公有无需凭据且切为私有后不可用`() {
        val config = ObjectStorageConfig(protocol = StorageProtocol.S3, endpoint = "s3.example.com",
            bucket = "bucket", privateAuth = false)
        assertTrue(config.isConfigured)
        assertFalse(config.copy(privateAuth = true).isConfigured)
        assertFalse(config.copy(privateAuth = true, accessKeyId = "id").isConfigured)
        assertFalse(config.copy(privateAuth = true, secretKey = "secret").isConfigured)
        assertTrue(config.copy(privateAuth = true, accessKeyId = "id", secretKey = "secret").isConfigured)
    }

    @Test
    fun `bucketLabel 优先用显式 bucket`() {
        assertEquals("myb", ObjectStorageConfig(protocol = StorageProtocol.S3, bucket = "myb", endpoint = "s3.example.com").bucketLabel)
    }

    @Test
    fun `COS 和 OSS 展示实际地址中的桶而非无效的独立桶名`() {
        assertEquals("actual", ObjectStorageConfig(bucket = "unused", endpoint = "actual.cos.ap-guangzhou.myqcloud.com").bucketLabel)
        assertEquals("actual", ObjectStorageConfig(protocol = StorageProtocol.OSS, bucket = "unused", endpoint = "actual.oss-cn-hangzhou.aliyuncs.com").bucketLabel)
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
