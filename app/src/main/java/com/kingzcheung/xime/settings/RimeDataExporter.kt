package com.kingzcheung.xime.settings

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File

/**
 * 把私有 files/rime 镜像到文件管理器可见的 Download/XimeGboard/rime。
 *
 * Android 11+ 不允许应用把 /data/data 目录直接暴露给文件管理器；这里用
 * MediaStore.Downloads 写一份“可浏览镜像”，用户可以在文件管理器里查看、
 * 复制、编辑，再通过备份/导入入口导回。build/、userdb 等派生物不导出。
 */
data class RimeDataExportSummary(
    val fileCount: Int,
    val bytes: Long,
    val folder: String,
)

object RimeDataExporter {

    private const val TAG = "RimeDataExporter"
    private const val ROOT = "XimeGboard/rime"
    const val PUBLIC_FOLDER = "Download/XimeGboard/rime"

    @Synchronized
    fun export(context: Context): Result<RimeDataExportSummary> = runCatching {
        val rimeDir = SchemaManager.getRimeDir(context)
        if (!rimeDir.isDirectory) error("Rime 数据目录不存在")
        var count = 0
        var bytes = 0L
        rimeDir.walkTopDown().forEach { file ->
            if (!file.isFile) return@forEach
            val rel = file.toRelativeString(rimeDir).replace('\\', '/')
            if (!shouldExport(rel)) return@forEach
            if (writeFile(context, rel, file)) {
                count++
                bytes += file.length()
            }
        }
        RimeDataExportSummary(count, bytes, PUBLIC_FOLDER)
    }

    /** 纯函数：是否导出到可浏览目录（排除派生物与用户词库二进制）。 */
    internal fun shouldExport(relPath: String): Boolean {
        val p = relPath.replace('\\', '/')
        if (p.startsWith("build/") || p.contains("/build/")) return false
        if (p.contains(".userdb/") || p.endsWith(".userdb")) return false
        if (p.startsWith(".git/") || p.contains("/.git/")) return false
        if (p.endsWith(".bin") || p.endsWith(".gram") || p.endsWith(".db") || p.endsWith(".db-journal")) return false
        return true
    }

    private fun writeFile(context: Context, rel: String, source: File): Boolean {
        val parent = rel.substringBeforeLast('/', "").trim('/')
        val name = rel.substringAfterLast('/')
        val mime = mimeType(name)
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val relativeDir = Environment.DIRECTORY_DOWNLOADS + "/" + ROOT +
                    if (parent.isEmpty()) "/" else "/" + parent + "/"
                val resolver = context.contentResolver
                val existing = findExisting(context, name, relativeDir)
                var uri: Uri? = existing
                if (uri == null) {
                    val values = ContentValues().apply {
                        put(MediaStore.Downloads.DISPLAY_NAME, name)
                        put(MediaStore.Downloads.MIME_TYPE, mime)
                        put(MediaStore.Downloads.RELATIVE_PATH, relativeDir)
                    }
                    uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                }
                uri ?: return false
                resolver.openOutputStream(uri, "wt")?.use { output ->
                    source.inputStream().use { it.copyTo(output) }
                } ?: return false
                true
            } else {
                @Suppress("DEPRECATION")
                val base = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                val destDir = if (parent.isEmpty()) File(base, ROOT) else File(File(base, ROOT), parent)
                if (!destDir.exists() && !destDir.mkdirs()) return false
                source.copyTo(File(destDir, name), overwrite = true)
                true
            }
        } catch (e: Exception) {
            android.util.Log.w(TAG, "writeFile failed: " + rel, e)
            false
        }
    }

    private fun findExisting(context: Context, name: String, relativeDir: String): Uri? {
        val collection = MediaStore.Downloads.EXTERNAL_CONTENT_URI
        val projection = arrayOf(MediaStore.Downloads._ID)
        val selection = MediaStore.Downloads.DISPLAY_NAME + " = ? AND " +
            MediaStore.Downloads.RELATIVE_PATH + " = ?"
        context.contentResolver.query(
            collection, projection, selection, arrayOf(name, relativeDir), null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                return Uri.withAppendedPath(collection, id.toString())
            }
        }
        return null
    }

    // 统一用 octet-stream：MediaStore 不会按 MIME 给 .yaml/.lua 追加 .txt 后缀。
    private fun mimeType(@Suppress("UNUSED_PARAMETER") name: String): String = "application/octet-stream"
}
