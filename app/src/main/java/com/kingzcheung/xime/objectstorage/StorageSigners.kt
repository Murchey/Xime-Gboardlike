package com.kingzcheung.xime.objectstorage

import java.net.URLEncoder
import java.security.MessageDigest
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec

/**
 * 对象存储签名工具（纯函数，便于用固定时间戳做单测）。
 *
 * 三家协议各自实现，但对外只暴露「给我请求要素，返回要加的 headers」，
 * [ObjectStorageClient] 不感知差异。
 */
object StorageSigners {

    /** 统一签名输入。 */
    data class SignInput(
        val config: ObjectStorageConfig,
        val method: String,
        /** 已编码的对象路径，以 / 开头，如 /XimeGboard/index.json */
        val uriPath: String,
        val query: Map<String, String> = emptyMap(),
        val headersToSign: Map<String, String> = emptyMap(),
        /** 请求体 SHA-256 十六进制（S3 SigV4 需要，COS/OSS 忽略） */
        val payloadSha256: String = "",
        val nowEpochSeconds: Long = System.currentTimeMillis() / 1000,
        /** S3 的 region（从 endpoint 推导或用户填） */
        val region: String = "",
    )

    /** 签名结果：要附加到请求上的 header 键值。 */
    data class SignedHeaders(val headers: Map<String, String>)

    fun sign(input: SignInput): SignedHeaders = when (input.config.protocol) {
        StorageProtocol.COS -> cosSign(input)
        StorageProtocol.OSS -> ossSign(input)
        StorageProtocol.S3 -> s3Sign(input)
    }

    // ── 腾讯云 COS（q-sign-algorithm=sha1，照参考项目 CosClient 移植）──

    private fun cosSign(input: SignInput): SignedHeaders {
        val c = input.config
        val now = input.nowEpochSeconds
        val keyTime = "${now - 60};${now + 600}"
        val signKey = hmacSha1Hex(c.secretKey, keyTime)

        val path = input.uriPath.let { if (it.startsWith("/")) it else "/$it" }
        val paramList = input.query.keys.map { cosUrlEncode(it.lowercase()) }.sorted().joinToString(";")
        val headerList = input.headersToSign.keys.map { it.lowercase() }.sorted().joinToString(";")
        val paramString = input.query.entries
            .sortedBy { it.key.lowercase() }
            .joinToString("&") { (k, v) -> "${cosUrlEncode(k.lowercase())}=${cosUrlEncode(v)}" }
        val headerString = input.headersToSign.entries
            .sortedBy { it.key.lowercase() }
            .joinToString("&") { (k, v) -> "${k.lowercase()}=${v.trim()}" }

        val httpString = input.method.lowercase() + "\n" + path + "\n" +
            paramString + "\n" + headerString + "\n"
        val stringToSign = "sha1\n$keyTime\n${sha1Hex(httpString)}\n"
        val signature = hmacSha1Hex(signKey, stringToSign)

        val auth = "q-sign-algorithm=sha1&q-ak=${c.accessKeyId}" +
            "&q-sign-time=$keyTime&q-key-time=$keyTime" +
            "&q-header-list=$headerList&q-url-param-list=$paramList" +
            "&q-signature=$signature"
        return SignedHeaders(mapOf("Authorization" to auth))
    }

    /**
     * COS URL 编码：UTF-8，空格 → %20（不是 +），* → %2A，~ 不编码。
     * 与签名严格一致，否则服务端返回 SignatureDoesNotMatch。
     */
    internal fun cosUrlEncode(value: String): String {
        if (value.isEmpty()) return ""
        return URLEncoder.encode(value, "UTF-8")
            .replace("+", "%20")
            .replace("*", "%2A")
            .replace("%7E", "~")
    }

    // ── 阿里云 OSS（Authorization: OSS AccessKeyId:Signature）──

    private fun ossSign(input: SignInput): SignedHeaders {
        val c = input.config
        val date = httpDate(input.nowEpochSeconds)
        // OSS 参与签名的头部固定为 content-type + date + 规范化的 x-oss-*
        val canonicalHeaders = buildString {
            val ct = input.headersToSign.entries.firstOrNull { it.key.equals("content-type", true) }
            if (ct != null) append("content-type:").append(ct.value.trim()).append('\n')
            append("date:").append(date).append('\n')
            input.headersToSign.entries
                .filter { it.key.lowercase().startsWith("x-oss-") }
                .sortedBy { it.key.lowercase() }
                .forEach { append(it.key.lowercase()).append(':').append(it.value.trim()).append('\n') }
        }
        val canonicalResource = input.uriPath.let { if (it.startsWith("/")) it else "/$it" } +
            ossSubResource(input.query)
        val stringToSign = input.method.uppercase() + "\n" +
            (input.headersToSign.entries.firstOrNull { it.key.equals("content-md5", true) }?.value ?: "") + "\n" +
            (input.headersToSign.entries.firstOrNull { it.key.equals("content-type", true) }?.value ?: "") + "\n" +
            date + "\n" + canonicalHeaders + canonicalResource
        val signature = hmacSha1Base64(c.secretKey, stringToSign)
        val auth = "OSS ${c.accessKeyId}:$signature"
        return SignedHeaders(
            mapOf("Authorization" to auth, "Date" to date)
        )
    }

    /** OSS 子资源（如 ?uploadId=xxx）按字典序拼进 CanonicalizedResource。 */
    internal fun ossSubResource(query: Map<String, String>): String {
        val sub = setOf(
            "acl", "uploads", "location", "cors", "logging", "website", "referer",
            "lifecycle", "delete", "append", "tagging", "objectmeta", "uploadid",
            "partnumber", "security-token", "position", "img", "style", "stylename",
            "replication", "replicationProgress", "replicationLocation", "cname",
            "bucketInfo", "comp", "qos", "live", "status", "vod", "startTime",
            "endtime", "symlink", "x-oss-process", "response-content-type",
            "x-oss-server-side-encryption", "stat", "x-oss-request-payer",
        )
        val hits = query.filterKeys { it.lowercase() in sub }
            .toSortedMap(compareBy(String::lowercase))
        if (hits.isEmpty()) return ""
        return "?" + hits.entries.joinToString("&") { (k, v) -> if (v.isEmpty()) k else "$k=$v" }
    }

    // ── AWS S3 Signature V4（HMAC-SHA256）──

    private fun s3Sign(input: SignInput): SignedHeaders {
        val c = input.config
        val amzDate = amzDate(input.nowEpochSeconds)
        val dateStamp = amzDate.substring(0, 8)
        val region = input.region.ifBlank { "us-east-1" }
        val service = "s3"

        val host = input.headersToSign.entries
            .firstOrNull { it.key.equals("host", true) }?.value?.trim().orEmpty()
        val canonicalHeaders = "host:$host\n"
        val signedHeaders = "host"
        val canonicalQuery = input.query.entries
            .sortedWith(compareBy({ it.key }, { it.value }))
            .joinToString("&") { (k, v) -> "${s3UriEncode(k)}=${s3UriEncode(v)}" }
        val canonicalRequest = input.method.uppercase() + "\n" +
            s3CanonicalPath(input.uriPath) + "\n" + canonicalQuery + "\n" +
            canonicalHeaders + "\n" + signedHeaders + "\n" + input.payloadSha256

        val scope = "$dateStamp/$region/$service/aws4_request"
        val stringToSign = "AWS4-HMAC-SHA256\n$amzDate\n$scope\n${sha256Hex(canonicalRequest)}"
        val kDate = hmacSha256(("AWS4" + c.secretKey).toByteArray(Charsets.UTF_8), dateStamp)
        val kRegion = hmacSha256(kDate, region)
        val kService = hmacSha256(kRegion, service)
        val kSigning = hmacSha256(kService, "aws4_request")
        val signature = hmacSha256Hex(kSigning, stringToSign)

        val auth = "AWS4-HMAC-SHA256 Credential=${c.accessKeyId}/$scope, " +
            "SignedHeaders=$signedHeaders, Signature=$signature"
        return SignedHeaders(
            mapOf("Authorization" to auth, "X-Amz-Date" to amzDate)
        )
    }

    /** S3 路径编码：/ 不编码，其余按 RFC3986 unreserved 规则。 */
    internal fun s3UriEncode(value: String): String {
        val sb = StringBuilder()
        for (b in value.toByteArray(Charsets.UTF_8)) {
            val c = b.toInt() and 0xFF
            if ((c in 'A'.code..'Z'.code) || (c in 'a'.code..'z'.code) ||
                (c in '0'.code..'9'.code) || c == '-'.code || c == '_'.code ||
                c == '.'.code || c == '~'.code
            ) {
                sb.append(c.toChar())
            } else {
                sb.append('%').append(String.format("%02X", c))
            }
        }
        return sb.toString()
    }

    private fun s3CanonicalPath(path: String): String {
        val p = path.let { if (it.startsWith("/")) it else "/$it" }
        // S3 规范请求里路径段做 uri 编码但保留 / 分隔（不对已编码的 % 二次编码）
        return "/" + p.trimStart('/').split("/").joinToString("/") { s3UriEncode(it) }
    }

    // ── 通用工具 ──

    internal fun sha1Hex(data: String): String =
        MessageDigest.getInstance("SHA-1").digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    internal fun sha256Hex(data: String): String =
        MessageDigest.getInstance("SHA-256").digest(data.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }

    internal fun sha256Hex(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }

    internal fun hmacSha1Hex(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8)).joinToString("") { "%02x".format(it) }
    }

    private fun hmacSha1Base64(key: String, data: String): String {
        val mac = Mac.getInstance("HmacSHA1")
        mac.init(SecretKeySpec(key.toByteArray(Charsets.UTF_8), "HmacSHA1"))
        // 用 java.util.Base64（JVM 单测可运行），与 android.util.Base64.NO_WRAP 等价
        return java.util.Base64.getEncoder().encodeToString(
            mac.doFinal(data.toByteArray(Charsets.UTF_8))
        )
    }

    private fun hmacSha256(key: ByteArray, data: String): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(key, "HmacSHA256"))
        return mac.doFinal(data.toByteArray(Charsets.UTF_8))
    }

    private fun hmacSha256Hex(key: ByteArray, data: String): String =
        hmacSha256(key, data).joinToString("") { "%02x".format(it) }

    /** RFC 7231 IMF-fixdate，OSS Date 头用。 */
    internal fun httpDate(epochSeconds: Long): String {
        val sdf = java.text.SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
        return sdf.format(java.util.Date(epochSeconds * 1000))
    }

    /** ISO8601 基本格式，S3 X-Amz-Date 用。 */
    internal fun amzDate(epochSeconds: Long): String {
        val sdf = java.text.SimpleDateFormat("yyyyMMdd'T'HHmmss'Z'", java.util.Locale.US)
        sdf.timeZone = java.util.TimeZone.getTimeZone("GMT")
        return sdf.format(java.util.Date(epochSeconds * 1000))
    }
}
