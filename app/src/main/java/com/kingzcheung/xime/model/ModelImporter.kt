package com.kingzcheung.xime.model

import android.content.Context
import android.net.Uri
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import java.io.File
import java.io.FileOutputStream
import java.util.zip.ZipInputStream

/** 从文件选择器导入模型文件或 ZIP/TAR.BZ2 模型包。 */
object ModelImporter {
    suspend fun importFromUri(context: Context, model: ModelInfo, uri: Uri): Result<Unit> = runCatching {
        val targetDir = ModelStorage.getModelDir(context, model.id).apply { mkdirs() }
        val name = queryName(context, uri).lowercase()
        context.contentResolver.openInputStream(uri)?.use { input ->
            when {
                name.endsWith(".zip") -> extractZip(input, targetDir)
                name.endsWith(".tar.bz2") || name.endsWith(".tbz2") -> {
                    val temp = File(context.cacheDir, "model-import-${System.nanoTime()}.tar.bz2")
                    try {
                        FileOutputStream(temp).use { output -> input.copyTo(output) }
                        ModelArchiveExtractor.extractTarBz2(temp, targetDir)
                    } finally {
                        temp.delete()
                    }
                }
                else -> {
                    val fileName = name.substringAfterLast('/')
                    val expected = model.files.firstOrNull { it.name.equals(fileName, true) }
                        ?: model.files.singleOrNull()
                        ?: error(tr(R.string.s_moxingwenjianmingbupipei))
                    FileOutputStream(File(targetDir, expected.name)).use { output -> input.copyTo(output) }
                }
            }
        } ?: error(tr(R.string.s_wufadakaimoxingwenjian))
        if (!ModelManager.isModelDownloaded(context, model)) error(tr(R.string.s_moxingwenjianbuwanzheng))
    }

    private fun queryName(context: Context, uri: Uri): String =
        context.contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor -> if (cursor.moveToFirst()) cursor.getString(0).orEmpty() else "" }
            .orEmpty().ifBlank { uri.lastPathSegment.orEmpty() }

    private fun extractZip(input: java.io.InputStream, targetDir: File) {
        ZipInputStream(input.buffered()).use { zip ->
            var entry = zip.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val name = entry.name.replace('\\', '/').substringAfterLast('/')
                    require(name.isNotBlank() && name != "." && name != "..") { "模型包包含无效文件名" }
                    FileOutputStream(File(targetDir, name)).use { zip.copyTo(it) }
                }
                zip.closeEntry()
                entry = zip.nextEntry
            }
        }
    }
}
