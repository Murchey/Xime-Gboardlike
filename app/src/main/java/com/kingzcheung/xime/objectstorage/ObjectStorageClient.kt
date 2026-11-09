package com.kingzcheung.xime.objectstorage

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
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
        data class Failure(val message: String, val httpCode: Int? = null) : Result<Nothing>()
    }

    enum class TestStage { WRITE, READ, VERIFY, CLEANUP }

    sealed class ConnectionTestResult {
        data object Success : ConnectionTestResult()
        data class Failure(
            val stage: TestStage,
            val detail: String,
            val cleanupFailed: Boolean = false,
            val testKey: String? = null,
        ) : ConnectionTestResult()
    }

    /** 只操作随机测试对象；即使写入响应失败，也尝试清理可能已落盘的对象。 */
    suspend fun testConnection(): ConnectionTestResult = withContext(Dispatchers.IO) {
        val key = "$ROOT_PREFIX/connection-tests/" + java.util.UUID.randomUUID() + ".bin"
        val payload = java.util.UUID.randomUUID().toString().toByteArray(Charsets.UTF_8)
        var failure: ConnectionTestResult.Failure? = null
        var stage = TestStage.WRITE
        try {
            when (val written = execute("PUT", key, payload, "application/octet-stream")) {
                is Result.Failure -> failure = ConnectionTestResult.Failure(stage, written.message)
                is Result.Success -> {
                    stage = TestStage.READ
                    val request = buildRequest("GET", key, null, null)
                    if (request == null) {
                        failure = ConnectionTestResult.Failure(stage, tr(R.string.config_incomplete))
                    } else {
                        http.newCall(request).execute().use { response ->
                            if (!response.isSuccessful) {
                                failure = ConnectionTestResult.Failure(stage, describeHttpError(response))
                            } else if (!payload.contentEquals(response.body?.bytes())) {
                                failure = ConnectionTestResult.Failure(TestStage.VERIFY, tr(R.string.config_mismatch))
                            }
                        }
                    }
                }
            }
        } catch (_: Exception) {
            failure = ConnectionTestResult.Failure(stage, tr(R.string.config_network_failed))
        } finally {
            val cleaned = runCatching { execute("DELETE", key, null, null) }.getOrNull()
            if (cleaned !is Result.Success) {
                failure = failure?.copy(cleanupFailed = true, testKey = key)
                    ?: ConnectionTestResult.Failure(TestStage.CLEANUP,
                        (cleaned as? Result.Failure)?.message ?: tr(R.string.config_cleanup_request_failed), true, key)
            }
        }
        failure ?: ConnectionTestResult.Success
    }

    /** 是否对请求签名：仅私有读写模式。 */
    private val signEnabled: Boolean get() = config.privateAuth && config.isConfigured

    suspend fun putObject(key: String, bytes: ByteArray, contentType: String = "application/octet-stream"): Result<Unit> =
        withContext(Dispatchers.IO) {
            execute("PUT", key, bytes, contentType)
        }

    suspend fun getObject(key: String): Result<ByteArray> = withContext(Dispatchers.IO) {
        try {
            val request = buildRequest("GET", key, null, null)
                ?: return@withContext Result.Failure(tr(R.string.config_storage_incomplete))
            http.newCall(request).execute().use { response ->
                if (response.isSuccessful) Result.Success(response.body?.bytes() ?: ByteArray(0))
                else Result.Failure(describeHttpError(response), response.code)
            }
        } catch (_: Exception) {
            Result.Failure(tr(R.string.config_network_failed))
        }
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
            ?: return@withContext Result.Failure(tr(R.string.config_incomplete))
        runCatching {
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful || resp.code == 404) Result.Success(Unit)
                else Result.Failure("HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}")
            }
        }.getOrElse { Result.Failure(it.message ?: tr(R.string.config_network_error)) }
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
            ?: return@withContext Result.Failure(tr(R.string.config_incomplete))
        val body = runCatching {
            http.newCall(request).execute().use { resp ->
                if (!resp.isSuccessful) return@withContext Result.Failure(
                    "HTTP ${resp.code}: ${resp.body?.string().orEmpty().take(200)}"
                )
                resp.body?.string().orEmpty()
            }
        }.getOrElse { return@withContext Result.Failure(it.message ?: tr(R.string.config_network_error)) }
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
            ?: return Result.Failure(tr(R.string.config_storage_incomplete))
        return runCatching {
            http.newCall(request).execute().use { resp ->
                if (resp.isSuccessful) Result.Success(Unit)
                else Result.Failure(describeHttpError(resp))
            }
        }.getOrElse { Result.Failure(it.message ?: tr(R.string.config_network_error)) }
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
            403 -> if (config.privateAuth) tr(R.string.config_private_forbidden)
            else tr(R.string.config_public_forbidden)
            404 -> tr(R.string.config_not_found)
            in 500..599 -> tr(R.string.config_server_failed)
            else -> tr(R.string.config_request_failed)
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
