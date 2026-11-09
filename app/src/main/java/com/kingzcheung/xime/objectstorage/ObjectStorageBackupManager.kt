package com.kingzcheung.xime.objectstorage

import android.content.Context
import com.kingzcheung.xime.settings.BackupManager
import com.kingzcheung.xime.settings.RimeExportManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.SyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 内置对象存储备份：默认只备份用户词典，高级选项备份「所有方案完整形态」。
 *
 * 云端落点：
 * - `XimeGboard/backups/userdict/<ts>.zip`：仅自造词快照（[SyncManager] 产物）
 * - `XimeGboard/backups/full/<ts>.zip`：整包（[RimeExportManager] 产物：设置 + 方案 + 词库 + 插件 + 清单）
 *
 * 与剪切板同步共用同一套 [ObjectStorageConfig]。操作串行化，避免并发打包同一份 rime 数据。
 */
object ObjectStorageBackupManager {
    private val operationMutex = Mutex()

    const val PREF_FULL_SCHEMAS = "backup_full_schemas"
    const val PREF_LAST_BACKUP_AT = "os_last_backup_at"
    private const val PREFIX = "XimeGboard/backups"
    private const val SUB_USERDICT = "userdict"
    private const val SUB_FULL = "full"

    data class BackupEntry(val key: String, val size: Long, val lastModified: String, val full: Boolean) {
        val displayName: String get() = key.substringAfterLast('/')
    }

    sealed class Outcome {
        data class Success(val message: String, val key: String) : Outcome()
        data class Failure(val message: String) : Outcome()
    }

    /** 默认关闭（只备份用户词典）；开启即整包。 */
    fun isFullSchemasEnabled(context: Context): Boolean =
        SettingsPreferences.getPrefsPublic(context).getBoolean(PREF_FULL_SCHEMAS, false)

    fun setFullSchemasEnabled(context: Context, enabled: Boolean) {
        SettingsPreferences.getPrefsPublic(context).edit()
            .putBoolean(PREF_FULL_SCHEMAS, enabled).apply()
    }

    /** 上传一份备份。先刷新自造词快照（与现有插件备份同口径）。 */
    suspend fun backupNow(context: Context): Outcome = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val config = ObjectStorageConfigStore.of(context).load()
            if (!config.isConfigured) return@withContext Outcome.Failure("对象存储未配置")
            val client = ObjectStorageClient(config)
            val full = isFullSchemasEnabled(context)
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cacheDir = File(context.cacheDir, "os-backup").apply { mkdirs() }
            val archive = File(cacheDir, "${if (full) "full" else "userdict"}_$ts.zip")

            return@withContext try {
                // 两档都先刷新一次自造词快照，保证用户词典是最新状态
                val dictNote = if (SyncManager.syncNow(context).isSuccess) null else "（自造词快照未刷新）"
                val ok = if (full) {
                    RimeExportManager.buildArchiveToFile(context).getOrNull()?.let { (_, built) ->
                        built.copyTo(archive, overwrite = true)
                        built.delete()
                        true
                    } ?: false
                } else {
                    SyncManager.packSyncDirToFile(File(context.filesDir, "rime"), archive) > 0
                }
                if (!ok) return@withContext Outcome.Failure(
                    if (full) "整包构建失败（rime 目录不可用）" else "没有可备份的用户词典快照"
                )

                val key = "$PREFIX/${if (full) SUB_FULL else SUB_USERDICT}/${archive.name}"
                val put = client.putObject(key, archive.readBytes(), "application/zip")
                if (put !is ObjectStorageClient.Result.Success) {
                    return@withContext Outcome.Failure(
                        (put as ObjectStorageClient.Result.Failure).message
                    )
                }
                SettingsPreferences.getPrefsPublic(context).edit()
                    .putLong(PREF_LAST_BACKUP_AT, System.currentTimeMillis()).apply()
                Outcome.Success(
                    (if (full) "已备份全部方案完整形态" else "已备份用户词典") + (dictNote ?: ""),
                    key,
                )
            } catch (e: Exception) {
                Outcome.Failure(e.message ?: "备份失败")
            } finally {
                archive.delete()
            }
        }
    }

    /** 列出云端备份（新→旧）。 */
    suspend fun listBackups(context: Context): List<BackupEntry> = withContext(Dispatchers.IO) {
        val config = ObjectStorageConfigStore.of(context).load()
        if (!config.isConfigured) return@withContext emptyList()
        when (val r = ObjectStorageClient(config).listObjects("$PREFIX/")) {
            is ObjectStorageClient.Result.Success -> r.value
                .filter { it.key.endsWith(".zip") }
                .map {
                    BackupEntry(
                        key = it.key,
                        size = it.size,
                        lastModified = it.lastModified,
                        full = "/$SUB_FULL/" in it.key,
                    )
                }
            else -> emptyList()
        }
    }

    /** 下载并恢复：用户词典走 SyncManager 合并；整包走 BackupManager.restoreFromArchive。 */
    suspend fun restore(context: Context, entry: BackupEntry): Outcome = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val config = ObjectStorageConfigStore.of(context).load()
            if (!config.isConfigured) return@withContext Outcome.Failure("对象存储未配置")
            val client = ObjectStorageClient(config)
            val bytes = when (val r = client.getObject(entry.key)) {
                is ObjectStorageClient.Result.Success -> r.value
                is ObjectStorageClient.Result.Failure -> return@withContext Outcome.Failure(r.message)
            }
            val cacheDir = File(context.cacheDir, "os-restore").apply { mkdirs() }
            val file = File(cacheDir, entry.displayName)
            return@withContext try {
                file.writeBytes(bytes)
                if (entry.full) {
                    BackupManager.restoreFromArchive(context, file)
                        .fold(
                            onSuccess = { Outcome.Success("已恢复完整备份", entry.key) },
                            onFailure = { Outcome.Failure(it.message ?: "恢复失败") },
                        )
                } else {
                    SyncManager.mergeSnapshots(context, listOf(file))
                        .fold(
                            onSuccess = { Outcome.Success("已合并用户词典", entry.key) },
                            onFailure = { Outcome.Failure(it.message ?: "合并失败") },
                        )
                }
            } catch (e: Exception) {
                Outcome.Failure(e.message ?: "恢复失败")
            } finally {
                file.delete()
            }
        }
    }

    suspend fun deleteBackup(context: Context, entry: BackupEntry): Boolean = withContext(Dispatchers.IO) {
        val config = ObjectStorageConfigStore.of(context).load()
        if (!config.isConfigured) return@withContext false
        ObjectStorageClient(config).deleteObject(entry.key)
            .let { it is ObjectStorageClient.Result.Success }
    }
}
