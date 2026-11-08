package com.kingzcheung.xime.settings

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File

/**
 * 语法模型（.gram）文件管理：导入 / 校验 / 列表 / 删除 / 导出。
 *
 * 只做文件层面的事，不碰配置；启用写入见 [GramActivation]。
 * 文件放在 rime 目录根（语言名.gram），跟随 [RimeStorage] 自定义目录，
 * 用户把数据目录切到外部存储时模型自动跟随。
 *
 * 校验用文件头 magic「Rime::Grammar/」，与 librime-octagram GramDb::Load
 * 的格式检查一致，坏文件在导入时即拒绝，不用等运行时才失败。
 */
object GramImporter {

    /** GramDb::Save 写入的格式串前缀（gram_db.cc: kGrammarFormatPrefix）。 */
    private const val MAGIC = "Rime::Grammar/"
    /**
     * 单个 .gram 上限。
     *
     * 语法模型随语料规模增长：万象 LTS 的 gram 已达 418MB，旧的 128MB 上限会把这类
     * 正常模型判成"文件过大"直接拒绝导入（用户侧表现为"加载 gram 模型显示导入失败"）。
     * 这里放宽到 2GB，仍然拦截异常超大文件；真正的可用性由导入时的剩余空间检查兜底。
     */
    private const val MAX_BYTES = 2048L * 1024 * 1024
    private val NAME_REGEX = Regex("^[A-Za-z0-9._-]{1,64}$")

    /** 一条已导入的语法模型；name 形如 XimeGboard.gram。 */
    data class GramFile(val name: String, val sizeBytes: Long, val lastModified: Long)

    class ImportException(message: String) : Exception(message)

    fun gramDir(context: Context): File = SchemaManager.getRimeDir(context)

    /** 已导入的 .gram 文件列表（按文件名排序），供管理页展示。 */
    fun list(context: Context): List<GramFile> =
        gramDir(context).listFiles()
            ?.asSequence()
            ?.filter { it.isFile && it.extension.equals("gram", ignoreCase = true) }
            ?.sortedBy { it.name.lowercase() }
            ?.map { GramFile(it.name, it.length(), it.lastModified()) }
            ?.toList()
            ?: emptyList()

    /** 文件名合法（纯 ASCII、无路径分隔）；导入与按名操作前统一走这里。 */
    fun isValidName(name: String): Boolean =
        name.endsWith(".gram", ignoreCase = true) && NAME_REGEX.matches(name)

    /** 去掉扩展名的显示名，即 Octagram 的 language id（配置里引用这个名字）。 */
    fun displayName(name: String): String = name.removeSuffix(".gram").removeSuffix(".GRAM")

    fun fileOf(context: Context, name: String): File {
        require(isValidName(name)) { "invalid gram file name: $name" }
        return File(gramDir(context), name)
    }

    /** 头部 magic 校验：与 GramDb::Load 的格式检查同口径。 */
    fun isGramFile(file: File): Boolean {
        if (!file.isFile || file.length() < MAGIC.length) return false
        return try {
            file.inputStream().use { input ->
                val head = ByteArray(MAGIC.length)
                var read = 0
                while (read < head.size) {
                    val n = input.read(head, read, head.size - read)
                    if (n < 0) return false
                    read += n
                }
                String(head, Charsets.US_ASCII) == MAGIC
            }
        } catch (_: Exception) {
            false
        }
    }

    /**
     * 从 SAF uri 导入 .gram 到 rime 目录。同名文件直接覆盖（与字体导入一致）。
     * 校验不过抛 [ImportException]，落盘前不留下残余文件。
     */
    fun importFromUri(context: Context, uri: Uri): GramFile {
        val rawName = queryDisplayName(context, uri) ?: "imported.gram"
        val name = sanitizeName(rawName)
        if (!isValidName(name)) throw ImportException("bad name: $rawName")
        val dir = gramDir(context)
        if (!dir.exists()) dir.mkdirs()
        val target = File(dir, name)
        val tmp = File(dir, "$name.tmp")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                tmp.outputStream().use { output -> input.copyTo(output) }
            } ?: throw ImportException("cannot open $uri")
            when {
                tmp.length() == 0L -> throw ImportException("empty file")
                tmp.length() > MAX_BYTES -> throw ImportException("file too large")
                !isGramFile(tmp) -> throw ImportException("not a .gram file")
            }
            if (target.exists()) target.delete()
            if (!tmp.renameTo(target)) throw ImportException("cannot move into place")
        } finally {
            if (tmp.exists()) tmp.delete()
        }
        return GramFile(target.name, target.length(), target.lastModified())
    }

    /** 文件大小显示（管理页列表用）。 */
    fun formatSize(bytes: Long): String = when {
        bytes >= 1024 * 1024 -> String.format("%.1f MB", bytes / 1024.0 / 1024.0)
        bytes >= 1024 -> String.format("%.1f KB", bytes / 1024.0)
        else -> "$bytes B"
    }

    fun delete(context: Context, name: String): Boolean {
        val file = fileOf(context, name)
        return file.exists() && file.delete()
    }

    /** 导出到 SAF 目标（文件选择器 createDocument 出来的 uri）。 */
    fun exportToUri(context: Context, name: String, uri: Uri): Boolean {
        val src = fileOf(context, name)
        if (!src.isFile) return false
        return try {
            context.contentResolver.openOutputStream(uri)?.use { out ->
                src.inputStream().use { it.copyTo(out) }
            } != null
        } catch (_: Exception) {
            false
        }
    }

    /** 显示名 → 落盘名：只保留安全字符；改不动则让 [isValidName] 兜底拒绝。 */
    private fun sanitizeName(raw: String): String {
        val base = raw.substringAfterLast('/').substringAfterLast('\\')
        return base.filter { it.isLetterOrDigit() || it == '.' || it == '_' || it == '-' }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (idx >= 0 && cursor.moveToFirst()) cursor.getString(idx) else null
            }
        }.getOrNull()
}
