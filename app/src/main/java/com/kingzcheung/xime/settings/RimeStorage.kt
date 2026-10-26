package com.kingzcheung.xime.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import java.io.File
import java.nio.file.Files

/**
 * Rime 数据目录切换。
 *
 * 默认是私有 files/rime；自定义目录通过把 files/rime 换成指向真实目录的
 * 符号链接实现，这样现有全部文件操作和 librime 路径都无需改动。自定义目录
 * 在应用私有目录之外时，需要 MANAGE_EXTERNAL_STORAGE（所有文件访问权限）。
 */
object RimeStorage {

    private const val PREFS = "rime_storage"
    private const val KEY_CUSTOM_DIR = "custom_dir"

    fun defaultDir(context: Context): File = File(context.filesDir, "rime")

    fun customTarget(context: Context): File? {
        val path = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_CUSTOM_DIR, null).orEmpty()
        if (path.isBlank()) return null
        return File(path).takeIf { it.isAbsolute && it.isDirectory }
    }

    fun isCustom(context: Context): Boolean =
        Files.isSymbolicLink(defaultDir(context).toPath())

    fun currentPath(context: Context): String =
        customTarget(context)?.absolutePath ?: defaultDir(context).absolutePath

    fun hasAllFilesAccess(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.R || Environment.isExternalStorageManager()

    fun requestAllFilesAccess(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
        val intent = Intent(
            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
            Uri.parse("package:" + context.packageName),
        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
    }

    /** SAF 选中的目录转真实路径；只支持本地存储。 */
    fun pathFromTreeUri(uri: Uri): File? = runCatching {
        pathFromDocumentId(DocumentsContract.getTreeDocumentId(uri))
    }.getOrNull()

    internal fun pathFromDocumentId(docId: String): File? = runCatching {
        if (docId.startsWith("raw:", ignoreCase = true)) {
            return File(docId.removePrefix("raw:")).takeIf { it.isDirectory }
        }
        val parts = docId.split(':', limit = 2)
        if (parts.size != 2) return null
        val volume = parts[0]
        val relative = parts[1].trim('/')
        val root = if (volume.equals("primary", ignoreCase = true)) {
            Environment.getExternalStorageDirectory()
        } else {
            File("/storage", volume)
        }
        if (!root.isDirectory) return null
        File(root, relative)
    }.getOrNull()

    @Synchronized
    fun switchTo(context: Context, target: File): Result<Unit> = runCatching {
        val default = defaultDir(context)
        val dest = target.canonicalFile
        require(dest.isDirectory || dest.mkdirs()) { "无法创建目标目录" }
        if (isCustom(context) && customTarget(context)?.canonicalPath == dest.path) {
            return@runCatching
        }
        val privateRoot = context.filesDir.canonicalFile
        require(!dest.path.startsWith(privateRoot.path + File.separator) && dest != privateRoot) {
            "不能选择应用私有数据目录内部"
        }

        // 把现有数据合入目标目录；不覆盖目标里已有文件，避免破坏用户手动修改
        copyTree(default, dest)

        if (!isCustom(context)) {
            val backup = File(context.filesDir, "rime_default_backup_" + System.currentTimeMillis())
            if (!default.renameTo(backup)) error("无法暂存旧数据目录")
            try {
                Files.createSymbolicLink(default.toPath(), dest.toPath())
            } catch (e: Exception) {
                backup.renameTo(default)
                throw e
            }
        } else {
            val previousTarget = customTarget(context)
            Files.deleteIfExists(default.toPath())
            try {
                Files.createSymbolicLink(default.toPath(), dest.toPath())
            } catch (e: Exception) {
                previousTarget?.let { Files.createSymbolicLink(default.toPath(), it.toPath()) }
                throw e
            }
        }
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putString(KEY_CUSTOM_DIR, dest.absolutePath).apply()
    }

    @Synchronized
    fun restoreDefault(context: Context): Result<Unit> = runCatching {
        if (!isCustom(context)) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CUSTOM_DIR).apply()
            return@runCatching
        }
        val default = defaultDir(context)
        val target = customTarget(context) ?: error("自定义目录不可用")
        val temp = File(context.filesDir, "rime_restore_" + System.currentTimeMillis())
        temp.mkdirs()
        copyTree(target, temp)
        Files.deleteIfExists(default.toPath())
        if (!temp.renameTo(default)) error("恢复默认目录失败")
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY_CUSTOM_DIR).apply()
    }

    private fun copyTree(source: File, dest: File) {
        if (!source.isDirectory) return
        source.walkTopDown().forEach { file ->
            val rel = file.toRelativeString(source).replace('\\', '/')
            if (rel.isEmpty()) return@forEach
            if (rel == "build" || rel.startsWith("build/")) return@forEach
            val out = File(dest, rel)
            if (file.isDirectory) {
                out.mkdirs()
            } else {
                out.parentFile?.mkdirs()
                if (!out.exists()) file.copyTo(out, overwrite = false)
            }
        }
    }
}
