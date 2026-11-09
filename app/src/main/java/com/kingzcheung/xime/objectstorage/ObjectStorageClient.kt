package com.kingzcheung.xime.objectstorage

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.ByteArrayInputStream
import java.util.concurrent.TimeUnit

/**
 * 内置对象存储客户端（COS / OSS / S3）。
 *
 * 不依赖插件，OkHttp 直连。私有读写时按协议签名；公有读写时**保留 key/id
 * 但不加签名**（用户决策），写失败由调用方给出可自诊断提示。
 *
 * 只实现本功能所需的对象级操作：PUT / GET / DELETE / HEAD / LIST(GET Bucket)。
 * 所有方法 suspend + [Dispatchers.IO]，不阻塞主线程。
 */
class ObjectStorageClient(
    private val config: ObjectStorageConfig,
    private val http: OkHttpClient = defaultClient(),
) {
    companion object {
        const val ROOT_PREFIX = "XimeGboard"

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .followRedirects(false)
            .build()

        /** 分段 URL 编码对象 key（COS 规则，三家都接受）。 */
        internal fun objectPath(key: String): String {
            if (key.isBlank()) return "/"
            return "/" + key.trimStart('/').split("/")
                .joinToString("/") { StorageSigners.cosUrlEncode(it) }
        }

        /** 从 endpoint 推导 S3 region（oss-cn-hangzhou / s3.ap-southeast-1 等常见形态）。 */
        internal fun inferRegion(host: String): String {
            val parts = host.lowercase().split(".")
            val idx = parts.indexOfFirst { it == "oss" || it == "s3" || it.startsWith("cos") }
            return if (idx >= 0 && idx + 1 < parts.size) parts[idx + 1] else ""
        }
    }

    data class ObjectMeta(val key: String, val size: Long, val lastModified: String)

    sealed class Result<out T> {
        data class Success<T>(val value: T) : Result<T>()
        data class Failure(val message: String) : Result<Nothing>()
    }

    /** 是否对请求签名：仅私有读写模式。 */
    private val signEnabled: Boolean get() = config.privateAuth && config.isConfigured

    suspend fun putObject(key: String, bytes: ByteArray, contentType: String = "application/octet-stream"): Result<Unit> =
        withContext(Dispatchers.IO) {
            execute("PUT", key, bytes, contentType)
        }

    suspend fun getObject(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        val raw = executeRaw("GET", key, null, null)
            ?: return@withContext Result.Failure("读取失败：$key")
        Result.Success(raw)
    }

    suspend fun deleteObject(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        execute("DELETE", key, null, null)
    }

    suspend fun objectExists(key: String): Boolean = withContext(Dispatchers.IO) {
        val request = buildRequest("HEAD", key, null, null) ?: return@withContext false
        runCatching {
            http.newCall(request).execute().use { it.isSuccessful }
        }.getOrDefault(false)
    }

    /** 仅判断读通道是否可用（GET 一个已知存在或允许 404 的对象）。 */
    suspend fun probeRead(key: String): Result<Unit> = withContext(Dispatchers.IO) {
        val request = buildRequest("GET", key, null, null)
            ?: return@withContext Result.Failure("配置不完整")
        runCatching {
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) Result.Success(Unit)
                else Result.Failure("HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
            }
        }.getOrElse { Result.Failure(it.message ?: "网络错误") }
    }

    /** 列出指定前缀下的对象（GET Bucket /?prefix=...）。 */
    suspend fun listObjects(prefix: String): Result<List<ObjectMeta>> = withContext(Dispatchers.IO) {
        val query = linkedMapOf<String, String>()
        when (config.protocol) {
            StorageProtocol.S3 -> {
                query["list-type"] = "2"
                query["prefix"] = prefix
            }
            else -> query["prefix"] = prefix
        }
        val request = buildRequest("GET", "", null, null, query = query, bucketRequest = true)
            ?: return@withContext Result.Failure("配置不完整")
        val body = runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Failure(
                    "HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}"
                )
                resp.body?.string().orEmpty()
            }
        }.getOrElse { return@withContext Result.Failure(it.message ?: "网络错误") }
        Result.Success(parseListXml(body))
    }

    // ── 请求构造 ────────────────────────────────────────────

    /**
     * [bucketRequest] = true 时请求打在桶根（不带对象路径），用于 ListObjects。
     * S3 path-style 需要把 bucket 放进路径；COS/OSS 用 host 中的 bucket。
     */
    private fun buildRequest(
        method: String,
        key: String,
        body: ByteArray?,
        contentType: String?,
        query: Map<String, String> = emptyMap(),
        bucketRequest: Boolean = false,
    ): Request? {
        val c = config
        if (c.host.isBlank()) return null

        // S3 path-style：host/bucket/key；COS/OSS：host/key
        val path = when {
            bucketRequest && c.protocol == StorageProtocol.S3 ->
                "/" + StorageSigners.cosUrlEncode(c.bucket.ifBlank { return null }) + "/"
            bucketRequest -> "/"
            c.protocol == StorageProtocol.S3 ->
                "/" + StorageSigners.cosUrlEncode(c.bucket.ifBlank { return null }) + objectPath(key)
            else -> objectPath(key)
        }

        val urlBuilder = StringBuilder("${c.scheme}://${c.host}$path")
        if (query.isNotEmpty()) {
            urlBuilder.append('?')
            urlBuilder.append(query.entries.joinToString("&") { (k, v) ->
                "${StorageSigners.cosUrlEncode(k)}=${StorageSigners.cosUrlEncode(v)}"
            })
        }

        val headersToSign = linkedMapOf<String, String>()
        headersToSign["host"] = c.host
        if (contentType != null) headersToSign["content-type"] = contentType

        val reqBuilder = Request.Builder().url(urlBuilder.toString())
        val requestBody = body?.toRequestBody(null)
        when (method.uppercase()) {
            "PUT" -> reqBuilder.put(requestBody ?: ByteArray(0).toRequestBody(null))
            "DELETE" -> reqBuilder.delete()
            "HEAD" -> reqBuilder.head()
            else -> reqBuilder.get()
        }
        contentType?.let { reqBuilder.header("Content-Type", it) }

        if (signEnabled) {
            val payloadSha = if (c.protocol == StorageProtocol.S3) {
                StorageSigners.sha256Hex(body ?: ByteArray(0))
            } else ""
            val signed = StorageSigners.sign(
                StorageSigners.SignInput(
                    config = c,
                    method = method,
                    uriPath = path,
                    query = query,
                    headersToSign = headersToSign,
                    payloadSha256 = payloadSha,
                    region = inferRegion(c.host),
                )
            )
            signed.headers.forEach { (k, v) -> reqBuilder.header(k, v) }
        }
        // 公有模式：保留 key/id 但不签名（不附加任何鉴权头）
        return reqBuilder.build()
    }

    private fun execute(method: String, key: String, body: ByteArray?, contentType: String?): Result<Unit> {
        val request = buildRequest(method, key, body, contentType)
            ?: return Result.Failure("对象存储配置不完整")
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.Success(Unit)
                else Result.Failure(describeHttpError(resp))
            }
        }.getOrElse { Result.Failure(it.message ?: "网络错误") }
    }

    private fun executeRaw(method: String, key: String, body: ByteArray?, contentType: String?): ByteArray? {
        val request = buildRequest(method, key, body, contentType) ?: return null
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) resp.body?.bytes() else null
            }
        }.getOrNull()
    }

    private fun describeHttpError(resp: Response): String {
        val detail = resp.body?.string().orEmpty().replace(Regex("\\s+"), " ").trim().take(200)
        val hint = when (resp.code) {
            403 -> if (config.privateAuth) "签名失败或密钥无权限（检查 key/id 与私有读写设置）"
            else "公有桶可能不允许匿名写入，请改用私有读写"
            404 -> "对象或桶不存在（检查 Endpoint / Bucket）"
            in 500..599 -> "服务端错误，稍后重试"
            else -> "请求失败"
        }
        return if (detail.isEmpty()) "HTTP ${resp.code} · $hint" else "HTTP ${resp.code} · $hint：$detail"
    }

    /** 解析 S3/COS/OSS 共通的 ListBucketResult / ListObjectsV2 XML。 */
    internal fun parseListXml(xml: String): List<ObjectMeta> {
        if (xml.isBlank()) return emptyList()
        val result = mutableListOf<ObjectMeta>()
        try {
            val parser = Xml.newPullParser()
            parser.setInput(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)), "UTF-8")
            var event = parser.eventType
            var key: String? = null
            var size = 0L
            var lastModified = ""
            var inContents = false
            while (event != XmlPullParser.END_DOCUMENT) {
                when (event) {
                    XmlPullParser.START_TAG -> {
                        when (parser.name) {
                            "Contents" -> { inContents = true; key = null; size = 0L; lastModified = "" }
                            "Key" -> if (inContents) key = parser.nextText()
                            "Size" -> if (inContents) size = parser.nextText().toLongOrNull() ?: 0L
                            "LastModified" -> if (inContents) lastModified = parser.nextText()
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (parser.name == "Contents" && inContents) {
                            key?.takeIf { it.isNotBlank() }?.let {
                                result += ObjectMeta(it, size, lastModified)
                            }
                            inContents = false
                        }
                    }
                }
                event = parser.next()
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return result.sortedByDescending { it.lastModified }
    }
}
