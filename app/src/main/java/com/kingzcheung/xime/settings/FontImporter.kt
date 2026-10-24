package com.kingzcheung.xime.settings

import android.content.Context
import android.graphics.Typeface
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * 字体文件导入：把用户从系统文件选择器挑的 ttf/otf/ttc 复制到 `files/rime/fonts/`。
 *
 * 只做文件层面的事（复制、校验、删除），不碰配置；配置写入见 [FontPreferences]。
 * 校验用 [Typeface.createFromFile]：解析失败即认为不是有效字体，直接拒绝，避免
 * 之后键盘渲染时才发现字体坏了。
 */
object FontImporter {
    private const val TAG = "FontImporter"
    private val SUPPORTED_EXT = setOf("ttf", "otf", "ttc", "otc")
    private const val MAX_BYTES = 48L * 1024 * 1024

    /** 导入结果：[relativePath] 形如 `fonts/xime_kai.ttf`（相对 files/rime）。 */
    data class Imported(val relativePath: String)

    class ImportException(message: String) : Exception(message)

    fun fontsDir(context: Context): File =
        File(context.filesDir, "rime/fonts").apply { if (!exists()) mkdirs() }

    /** 已导入的字体文件列表（按文件名排序），供字体选择页展示。 */
    fun list(context: Context): List<Imported> =
        fontsDir(context).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.lowercase() in SUPPORTED_EXT }
            ?.sortedBy { it.name.lowercase() }
            ?.map { Imported("fonts/${it.name}") }
            ?.toList()
            ?: emptyList()

    /** 用户可见的字体名（去掉目录与扩展名）。 */
    fun displayName(relativePath: String): String {
        if (relativePath.isBlank()) return ""
        val file = relativePath.substringAfterLast('/')
        return file.substringBeforeLast('.', file)
    }

    fun import(context: Context, uri: Uri): Imported {
        val original = queryDisplayName(context, uri)
        val ext = original.substringAfterLast('.', "").lowercase()
        if (ext !in SUPPORTED_EXT) {
            throw ImportException("不支持的字体格式（.$ext），请选择 ttf / otf / ttc 文件")
        }

        val dir = fontsDir(context)
        val base = original.substringBeforeLast('.')
            .replace(Regex("[^A-Za-z0-9_\\-]"), "_")
            .take(40)
            .ifBlank { "font" }
        var target = File(dir, "$base.$ext")
        var index = 1
        while (target.exists() && index < 200) {
            target = File(dir, "${base}_$index.$ext")
            index++
        }

        val tmp = File(dir, ".$base.importing")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    var total = 0L
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        total += read
                        if (total > MAX_BYTES) throw ImportException("字体文件过大（上限 48MB）")
                        output.write(buffer, 0, read)
                    }
                }
            } ?: throw ImportException("无法读取所选文件")
        } catch (e: ImportException) {
            tmp.delete()
            throw e
        } catch (e: Exception) {
            tmp.delete()
            Log.w(TAG, "copy font failed", e)
            throw ImportException("复制字体文件失败")
        }

        // 同一字体被导入到多个槽位时复用同一份文件，避免重复占空间
        runCatching {
            val hash = sha256(tmp)
            dir.listFiles()?.firstOrNull { file ->
                file.isFile && file != tmp && file.length() == tmp.length() && sha256(file) == hash
            }
        }.getOrNull()?.let { existing ->
            tmp.delete()
            return Imported("fonts/${existing.name}")
        }

        if (!target.exists()) {
            try {
                Typeface.createFromFile(tmp)
            } catch (e: Exception) {
                tmp.delete()
                Log.w(TAG, "invalid font: $original", e)
                throw ImportException("这个文件不是有效字体")
            }
        }
        if (tmp.exists() && !tmp.renameTo(target)) {
            tmp.delete()
            throw ImportException("保存字体失败")
        }
        return Imported("fonts/${target.name}")
    }

    fun delete(context: Context, relativePath: String) {
        if (relativePath.isBlank()) return
        runCatching {
            val file = File(context.filesDir, "rime/$relativePath")
            if (file.exists()) file.delete()
        }
    }

    private fun sha256(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
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

    private fun queryDisplayName(context: Context, uri: Uri): String {
        runCatching {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
                ?.use { cursor ->
                    if (cursor.moveToFirst()) {
                        val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (index >= 0) cursor.getString(index)?.takeIf { it.isNotBlank() }?.let { return it }
                    }
                }
        }
        return uri.lastPathSegment ?: "font"
    }
}
