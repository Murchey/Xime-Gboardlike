package com.kingzcheung.xime.model

import android.content.Context
import android.net.Uri
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * 预测模型导出：把 `filesDir/models/<id>/` 打成扁平 zip（模型文件 + manifest.json），
 * 经系统文件选择器写到用户指定路径。
 *
 * 扁平结构与 [ModelImporter.extractZip] 的 basename 落盘规则一致，导出的包可被
 * 「本地模型 → 导入模型」原样回灌。manifest.json 只作说明，不参与
 * [ModelManager.isModelDownloaded] 的完整性判定（后者只校验索引声明的文件）。
 */
object ModelExporter {

    private const val TAG = "ModelExporter"
    private const val MANIFEST_NAME = "manifest.json"
    private const val FORMAT_VERSION = 1

    data class FileEntry(val name: String, val sizeBytes: Long, val sha256: String)

    data class Manifest(
        val modelId: String,
        val modelName: String,
        val version: String,
        val exportedAtMs: Long,
        val source: String,
        val files: List<FileEntry>,
    )

    /** 建议文件名，交由 [androidx.activity.result.contract.ActivityResultContracts.CreateDocument] 预填。 */
    fun suggestFileName(model: ModelInfo): String {
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val version = model.resolvedVersion()?.version?.takeIf { it.isNotBlank() } ?: "default"
        return "XimeGboard-${model.id}-${version}-${stamp}.zip"
    }

    /**
     * 打包到 cacheDir 临时文件，成功返回该文件（调用方负责删除）。
     * 模型文件缺失或为空时返回失败，不生成空包。
     */
    suspend fun buildArchive(
        context: Context,
        model: ModelInfo,
        exportedAtMs: Long = System.currentTimeMillis(),
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            if (!ModelManager.isModelDownloaded(context, model)) {
                throw IOException(tr(R.string.s_moxingwenjianbuzai_wufadaochu))
            }
            val dir = ModelStorage.getModelDir(context, model.id)
            val entries = collectEntries(model, dir)
            if (entries.isEmpty()) throw IOException(tr(R.string.s_moxingwenjianbuzai_wufadaochu))

            val manifest = Manifest(
                modelId = model.id,
                modelName = model.name,
                version = model.resolvedVersion()?.version.orEmpty(),
                exportedAtMs = exportedAtMs,
                source = "XimeGboard",
                files = entries,
            )

            val target = File(context.cacheDir, "model-export-${System.nanoTime()}.zip")
            try {
                writeZip(target, entries.map { it.name }, dir, manifest)
            } catch (e: Exception) {
                target.delete()
                throw e
            }
            target
        }.onFailure { FileLogger.e(TAG, "buildArchive failed: ${it.message}", it) }
    }

    /** 导出到 SAF uri，返回写入的文件数（不含 manifest）。 */
    suspend fun exportTo(context: Context, model: ModelInfo, uri: Uri): Result<Int> =
        withContext(Dispatchers.IO) {
            runCatching {
                val archive = buildArchive(context, model).getOrElse { throw it }
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        archive.inputStream().use { it.copyTo(output) }
                    } ?: throw IOException(tr(R.string.s_wufaxierusuoxuanwenjian))
                    ModelManager.getModel(model.id)?.files?.size ?: archiveEntryCount(archive)
                } finally {
                    archive.delete()
                }
            }.onFailure { FileLogger.e(TAG, "exportTo failed: ${it.message}", it) }
        }

    /** 按索引声明的文件清单收集；实际目录里多出的文件（如旧版残留）不打包。 */
    private fun collectEntries(model: ModelInfo, dir: File): List<FileEntry> {
        val declared = model.files.map { it.name }
        return declared.mapNotNull { name ->
            val file = File(dir, name)
            if (!file.exists() || file.length() <= 0L) return@mapNotNull null
            FileEntry(name, file.length(), sha256(file))
        }
    }

    private fun sha256(file: File): String {
        val digest = MessageDigest.getInstance("SHA-256")
        file.inputStream().use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val read = input.read(buffer)
                if (read <= 0) break
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    /** 逐条目流式写入，内存占用与包体无关。 */
    private fun writeZip(target: File, names: List<String>, dir: File, manifest: Manifest) {
        ZipOutputStream(FileOutputStream(target).buffered()).use { zip ->
            names.forEach { name ->
                val file = File(dir, name)
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            zip.putNextEntry(ZipEntry(MANIFEST_NAME))
            zip.write(manifestToJson(manifest).toByteArray(Charsets.UTF_8))
            zip.closeEntry()
        }
    }

    internal fun manifestToJson(manifest: Manifest): String {
        val files = JSONArray()
        manifest.files.forEach { entry ->
            files.put(
                JSONObject()
                    .put("name", entry.name)
                    .put("size_bytes", entry.sizeBytes)
                    .put("sha256", entry.sha256)
            )
        }
        return JSONObject()
            .put("format_version", FORMAT_VERSION)
            .put("model_id", manifest.modelId)
            .put("model_name", manifest.modelName)
            .put("version", manifest.version)
            .put("exported_at_ms", manifest.exportedAtMs)
            .put("source", manifest.source)
            .put("files", files)
            .toString(2)
    }

    private fun archiveEntryCount(archive: File): Int {
        var count = 0
        java.util.zip.ZipFile(archive).use { zip ->
            val entries = zip.entries()
            while (entries.hasMoreElements()) {
                val e = entries.nextElement()
                if (!e.isDirectory && e.name != MANIFEST_NAME) count++
            }
        }
        return count
    }
}
