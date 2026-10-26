package com.kingzcheung.xime.settings

import org.json.JSONObject
import java.io.File

/**
 * 方案包升级事务：升级前备份旧文件/旧 manifest/旧 registry；
 * 部署成功 commit，部署失败 rollback，避免升级失败后旧包文件已不可恢复。
 */
class UpgradeTransaction internal constructor(
    private val filesDir: File,
    private val rimeDir: File,
    val packageId: String,
    private val backupDir: File,
    private val oldFiles: List<String>,
    private val oldManifestText: String?,
    private val oldRegistryText: String?,
) {
    fun commit() {
        backupDir.deleteRecursively()
    }

    fun rollback(): Boolean {
        return try {
            val manifestFile = File(filesDir, ".manifests/$packageId.json")
            val newFiles = readManifestFiles(manifestFile)
            val oldSet = oldFiles.toSet()
            // 新包独有文件：删除（用户数据不删）
            for (fn in newFiles - oldSet) {
                if (SchemaManifestManager.isUserDataFile(fn)) continue
                File(rimeDir, fn).delete()
            }
            // 旧文件覆盖回 rime/
            for (fn in oldFiles) {
                val src = File(backupDir, "rime/$fn")
                if (!src.isFile) continue
                val dst = File(rimeDir, fn)
                dst.parentFile?.mkdirs()
                src.copyTo(dst, overwrite = true)
            }
            restore(File(filesDir, ".manifests/$packageId.json"), oldManifestText)
            restore(File(filesDir, ".registry.json"), oldRegistryText)
            backupDir.deleteRecursively()
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun readManifestFiles(manifest: File): Set<String> {
        if (!manifest.exists()) return emptySet()
        return runCatching {
            val files = JSONObject(manifest.readText()).optJSONObject("files") ?: JSONObject()
            files.keys().asSequence().toSet()
        }.getOrDefault(emptySet())
    }

    private fun restore(file: File, text: String?) {
        if (text == null) return
        file.parentFile?.mkdirs()
        file.writeText(text)
    }
}
