package com.kingzcheung.xime.handwriting

import android.content.Context
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import okhttp3.OkHttpClient
import okhttp3.Request

/**
 * 公式手写模型包（编码器 + 解码器 + 记号表 + 参数）的下载与校验。
 *
 * 模型约 32.4MB，不随包分发：首次使用公式模式时按需下载到 `files/models/handwriting-formula/`。
 * 来源是 CyIME 公开发布的 v1 模型包（GitHub release + ModelScope 镜像），逐文件校验 sha256。
 * 模型基于 MathWriting（CC BY-NC-SA 4.0），仅限非商业用途，下载时会一并落 NOTICE。
 */
object FormulaModelDownloader {

    private const val TAG = "FormulaModelDownload"
    private const val GITHUB = "https://github.com/Cyletix/CyIME/releases/download/models/"
    private const val MODELSCOPE = "https://www.modelscope.cn/models/Cyletix/cyime-model/resolve/master/"

    /** 模型文件清单：名称 → (sha256, 字节数)。 */
    internal val FILES = listOf(
        Triple("formula_encoder.onnx", "c2c55f14e8d3bc892c7f86ec9b237e54f46997300550a5c76501c54b7344b6dd", 21_137_800L),
        Triple("formula_decoder.onnx", "ab11c1ceb4cb58b044be89b77c71eb32fcbc53ff1ddfc136c51444b002bdb2a9", 13_346_580L),
        Triple("formula_vocab.json", "73653abb6993d5f2451501c804fb66671c05f86b4165bbe10cb3ef6ef96ec92c", 2_003L),
        Triple("formula_meta.json", "2ad149afaa9f3872f872e1d4bf9560dfb67be21d08d69ec1a0fa05441cff7541", 205L),
    )

    /** NOTICE 文本（随模型一起放在模型目录，满足许可要求）。 */
    private const val NOTICE_NAME = "NOTICE-formula-handwriting.txt"

    private val client = OkHttpClient.Builder()
        .connectTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(120, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    fun modelDir(context: Context): File =
        ModelStorage.getModelDir(context, FormulaRecognizer.MODEL_ID)

    fun isComplete(context: Context): Boolean = FormulaRecognizer.isAvailable(context)

    fun totalBytes(): Long = FILES.sumOf { it.third }

    /** 已下载字节数（用于进度显示）。 */
    fun downloadedBytes(context: Context): Long {
        val dir = modelDir(context)
        return FILES.sumOf { (name, _, size) ->
            val f = File(dir, name)
            if (f.isFile) f.length().coerceAtMost(size) else 0L
        }
    }

    /**
     * 下载并校验全部模型文件；已存在且校验通过的文件跳过。
     * @param onProgress 进度回调（0f~1f）；在 IO 线程调用，调用方自行切主线程
     */
    suspend fun download(context: Context, onProgress: (Float) -> Unit = {}): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val dir = modelDir(context)
                if (!dir.exists() && !dir.mkdirs()) throw IOException("无法创建模型目录")
                val total = totalBytes().toDouble()
                var done = 0L
                for ((name, sha, size) in FILES) {
                    val target = File(dir, name)
                    if (target.isFile && target.length() == size && sha256(target) == sha) {
                        done += size
                        onProgress((done / total).toFloat())
                        continue
                    }
                    val bytes = downloadFile(name, sha, size)
                    target.writeBytes(bytes)
                    done += size
                    onProgress((done / total).toFloat())
                }
                // 许可提示：拿不到也不影响识别，失败只记日志
                runCatching { File(dir, NOTICE_NAME).writeText(fetchNotice()) }
                FileLogger.i(TAG, "formula model ready at ${dir.absolutePath}")
            }.onFailure { FileLogger.e(TAG, "download formula model failed", it) }
        }

    /** 删除本地公式模型（释放约 32MB）。 */
    fun delete(context: Context): Boolean {
        FormulaRecognizer.release()
        val dir = modelDir(context)
        return runCatching { dir.deleteRecursively() }.getOrDefault(false)
    }

    private fun downloadFile(name: String, sha: String, size: Long): ByteArray {
        val urls = listOf(GITHUB + name, MODELSCOPE + name)
        var lastError: Exception? = null
        for (url in urls) {
            try {
                val request = Request.Builder().url(url).build()
                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) throw IOException("HTTP ${response.code}: $url")
                    val bytes = response.body?.bytes() ?: throw IOException("空响应: $url")
                    if (bytes.size.toLong() != size) throw IOException("大小不符: $url")
                    val actual = sha256(bytes)
                    if (actual != sha) throw IOException("sha256 不符: $url")
                    return bytes
                }
            } catch (e: Exception) {
                lastError = e
                FileLogger.w(TAG, "download $name from $url failed", e)
            }
        }
        throw lastError ?: IOException("无法下载 $name")
    }

    private fun fetchNotice(): String {
        val request = Request.Builder().url(GITHUB + NOTICE_NAME).build()
        return client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            response.body?.string().orEmpty()
        }
    }

    internal fun sha256(file: File): String? = runCatching {
        sha256(file.readBytes())
    }.getOrNull()

    internal fun sha256(bytes: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString("") { "%02x".format(it) }
}
