package com.kingzcheung.xime.objectstorage

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 三家对象存储签名的纯函数单测。
 *
 * 用固定时间戳 + 固定凭据，断言签名串的确定性、组成部分和各协议差异；
 * 真机无真实桶可测，这里至少保证签名计算不随环境漂移、URL 编码规则正确。
 */
class StorageSignersTest {

    private val cosCfg = ObjectStorageConfig(
        protocol = StorageProtocol.COS,
        endpoint = "https://demo-1250000000.cos.ap-guangzhou.myqcloud.com",
        bucket = "demo-1250000000",
        accessKeyId = "AKIDEXAMPLE",
        secretKey = "SECRETEXAMPLE",
        privateAuth = true,
    )
    private val ossCfg = cosCfg.copy(protocol = StorageProtocol.OSS, endpoint = "https://demo.oss-cn-hangzhou.aliyuncs.com", bucket = "demo")
    private val s3Cfg = cosCfg.copy(protocol = StorageProtocol.S3, endpoint = "https://s3.ap-southeast-1.amazonaws.com", bucket = "demo")

    private fun input(cfg: ObjectStorageConfig, method: String = "PUT", path: String = "/XimeGboard/index.json") =
        StorageSigners.SignInput(
            config = cfg,
            method = method,
            uriPath = path,
            query = emptyMap(),
            headersToSign = mapOf("host" to cfg.host),
            payloadSha256 = "",
            nowEpochSeconds = 1_700_000_000L,
            region = "ap-southeast-1",
        )

    // ── COS ──

    @Test
    fun `COS 签名串包含全部必需字段`() {
        val auth = StorageSigners.sign(input(cosCfg)).headers["Authorization"]!!
        assertTrue(auth.startsWith("q-sign-algorithm=sha1"))
        assertTrue(auth.contains("q-ak=AKIDEXAMPLE"))
        assertTrue(auth.contains("q-sign-time=1699999940;1700000600"))
        assertTrue(auth.contains("q-header-list=host"))
        assertTrue(auth.contains("q-url-param-list="))
        assertTrue(auth.contains("q-signature="))
    }

    @Test
    fun `COS 签名对固定输入确定且含 40 位十六进制`() {
        val a = StorageSigners.sign(input(cosCfg)).headers["Authorization"]!!
        val b = StorageSigners.sign(input(cosCfg)).headers["Authorization"]!!
        assertEquals(a, b)
        val sig = a.substringAfter("q-signature=")
        assertEquals(40, sig.length)
        assertTrue(sig.all { it in '0'..'9' || it in 'a'..'f' })
    }

    @Test
    fun `COS 不同 method 签名不同`() {
        val put = StorageSigners.sign(input(cosCfg, "PUT")).headers["Authorization"]!!
        val get = StorageSigners.sign(input(cosCfg, "GET")).headers["Authorization"]!!
        assertFalse(put == get)
    }

    @Test
    fun `COS query 参与签名且按字典序`() {
        val q = mapOf("prefix" to "a/b", "max-keys" to "10")
        val auth = StorageSigners.sign(input(cosCfg).copy(query = q)).headers["Authorization"]!!
        assertTrue(auth.contains("q-url-param-list=max-keys;prefix"))
    }

    // ── URL 编码 ──

    @Test
    fun `cosUrlEncode 空格用 20 而不是加号`() {
        assertEquals("a%20b", StorageSigners.cosUrlEncode("a b"))
    }

    @Test
    fun `cosUrlEncode 星号编码为 2A 且波浪号不编码`() {
        assertEquals("%2A", StorageSigners.cosUrlEncode("*"))
        assertEquals("~", StorageSigners.cosUrlEncode("~"))
    }

    @Test
    fun `cosUrlEncode 中文按 UTF8 编码`() {
        assertEquals("%E4%B8%AD", StorageSigners.cosUrlEncode("中"))
    }

    // ── OSS ──

    @Test
    fun `OSS 生成 Authorization 与 Date 头`() {
        val headers = StorageSigners.sign(input(ossCfg)).headers
        val auth = headers["Authorization"]!!
        assertTrue(auth.startsWith("OSS AKIDEXAMPLE:"))
        assertEquals("Tue, 14 Nov 2023 22:13:20 GMT", headers["Date"])
    }

    @Test
    fun `OSS 签名确定`() {
        assertEquals(
            StorageSigners.sign(input(ossCfg)).headers["Authorization"],
            StorageSigners.sign(input(ossCfg)).headers["Authorization"],
        )
    }

    @Test
    fun `OSS 子资源按字典序拼接`() {
        val q = mapOf("uploadId" to "u1", "partNumber" to "2", "prefix" to "x")
        assertEquals("?partNumber=2&uploadId=u1", StorageSigners.ossSubResource(q))
    }

    @Test
    fun `OSS 非子资源参数不进 CanonicalizedResource`() {
        assertEquals("", StorageSigners.ossSubResource(mapOf("prefix" to "x", "marker" to "m")))
    }

    // ── S3 SigV4 ──

    @Test
    fun `S3 生成 AWS4 Authorization 与 X-Amz-Date`() {
        val headers = StorageSigners.sign(input(s3Cfg)).headers
        assertEquals("20231114T221320Z", headers["X-Amz-Date"])
        val auth = headers["Authorization"]!!
        assertTrue(auth.startsWith("AWS4-HMAC-SHA256 Credential=AKIDEXAMPLE/20231114/ap-southeast-1/s3/aws4_request"))
        assertTrue(auth.contains("SignedHeaders=host"))
        assertTrue(auth.contains("Signature="))
    }

    @Test
    fun `S3 签名确定且为 64 位十六进制`() {
        val a = StorageSigners.sign(input(s3Cfg)).headers["Authorization"]!!
        val b = StorageSigners.sign(input(s3Cfg)).headers["Authorization"]!!
        assertEquals(a, b)
        val sig = a.substringAfter("Signature=")
        assertEquals(64, sig.length)
    }

    @Test
    fun `S3 空 region 回退 us-east-1`() {
        val auth = StorageSigners.sign(input(s3Cfg).copy(region = "")).headers["Authorization"]!!
        assertTrue(auth.contains("/us-east-1/s3/aws4_request"))
    }

    @Test
    fun `s3UriEncode 保留 unreserved 字符`() {
        assertEquals("aZ09-_.~", StorageSigners.s3UriEncode("aZ09-_.~"))
    }

    @Test
    fun `s3UriEncode 空格编码为 20`() {
        assertEquals("a%20b", StorageSigners.s3UriEncode("a b"))
    }

    @Test
    fun `s3UriEncode 中文按 UTF8 大写编码`() {
        assertEquals("%E4%B8%AD", StorageSigners.s3UriEncode("中"))
    }

    // ── 时间格式 ──

    @Test
    fun `httpDate 固定 RFC7231`() {
        assertEquals("Tue, 14 Nov 2023 22:13:20 GMT", StorageSigners.httpDate(1_700_000_000L))
    }

    @Test
    fun `amzDate 固定 ISO8601 基本格式`() {
        assertEquals("20231114T221320Z", StorageSigners.amzDate(1_700_000_000L))
    }

    // ── 协议分发 ──

    @Test
    fun `按协议生成不同 Authorization 前缀`() {
        assertTrue(StorageSigners.sign(input(cosCfg)).headers["Authorization"]!!.startsWith("q-sign-algorithm"))
        assertTrue(StorageSigners.sign(input(ossCfg)).headers["Authorization"]!!.startsWith("OSS "))
        assertTrue(StorageSigners.sign(input(s3Cfg)).headers["Authorization"]!!.startsWith("AWS4-HMAC-SHA256"))
    }
}
