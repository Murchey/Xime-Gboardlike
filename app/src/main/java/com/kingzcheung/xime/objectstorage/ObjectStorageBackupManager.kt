package com.kingzcheung.xime.objectstorage

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

import android.content.Context
import com.kingzcheung.xime.settings.BackupManager
import com.kingzcheung.xime.settings.RimeExportManager
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.SyncManager
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

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
            if (!config.isConfigured) return@withContext Outcome.Failure(tr(R.string.backup_ui_20))
            val client = ObjectStorageClient(config)
            val full = isFullSchemasEnabled(context)
            val ts = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
            val cacheDir = File(context.cacheDir, "os-backup").apply { mkdirs() }
            val archive = File(cacheDir, "${if (full) "full" else "userdict"}_$ts.zip")

            return@withContext try {
                // 两档都先刷新一次自造词快照，保证用户词典是最新状态
                SyncManager.syncNow(context).getOrThrow()
                val ok = if (full) {
                    RimeExportManager.buildArchiveToFile(context).getOrThrow().let { (_, built) ->
                        built.copyTo(archive, overwrite = true)
                        built.delete()
                        true
                    }
                } else {
                    SyncManager.packSyncDirToFile(File(context.filesDir, "rime"), archive) > 0
                }
                if (!ok) return@withContext Outcome.Failure(
                    if (full) tr(R.string.backup_ui_21) else tr(R.string.backup_ui_22)
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
                    if (full) tr(R.string.backup_ui_23) else tr(R.string.backup_ui_24),
                    key,
                )
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failure(e.message ?: tr(R.string.backup_ui_25))
            } finally {
                archive.delete()
            }
        }
    }

    /** 兼容旧调用方；失败抛出异常，不能伪装成空记录。 */
    suspend fun listBackups(context: Context): List<BackupEntry> = listBackupsResult(context).getOrThrow()

    suspend fun listBackupsResult(context: Context): Result<List<BackupEntry>> = withContext(Dispatchers.IO) {
        try {
            val config = ObjectStorageConfigStore.of(context).load()
            check(config.isConfigured) { tr(R.string.backup_ui_20) }
            mapBackupList(ObjectStorageClient(config).listObjects("$PREFIX/"))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    internal fun mapBackupList(result: ObjectStorageClient.Result<List<ObjectStorageClient.ObjectMeta>>): Result<List<BackupEntry>> =
        when (result) {
            is ObjectStorageClient.Result.Failure -> Result.failure(IllegalStateException(result.message))
            is ObjectStorageClient.Result.Success -> Result.success(result.value.mapNotNull { meta ->
                val type = backupType(meta.key) ?: return@mapNotNull null
                BackupEntry(meta.key, meta.size, meta.lastModified, type)
            }.sortedWith(compareByDescending<BackupEntry> { it.lastModified }.thenByDescending { it.displayName }))
        }

    /** 仅接受已知目录下的直接 ZIP 对象，类型由 key 推导。 */
    internal fun backupType(key: String): Boolean? {
        val relative = key.removePrefix("$PREFIX/")
        if (relative == key) return null
        val parts = relative.split('/')
        if (parts.size != 2 || !parts[1].endsWith(".zip", ignoreCase = true) || parts[1].contains('\\')) return null
        return when (parts[0]) {
            SUB_FULL -> true
            SUB_USERDICT -> false
            else -> null
        }
    }

    /** 解包并验证全部快照后才交给引擎，避免把 ZIP 当作快照传入。 */
    internal fun restoreDictionaryArchive(
        archive: File,
        staging: File,
        merge: (List<File>) -> Result<Int>,
    ): Result<Int> {
        return try {
            check(staging.mkdirs() || staging.isDirectory) { tr(R.string.backup_ui_26) }
            check(SyncManager.unpackArchive(staging, archive) > 0) { tr(R.string.backup_ui_27) }
            val snapshots = SyncManager.snapshotFilesUnder(staging)
            check(snapshots.isNotEmpty()) { tr(R.string.backup_ui_27) }
            snapshots.forEach { file ->
                val head = file.inputStream().use { input ->
                    val buffer = ByteArray(8192)
                    val size = input.read(buffer)
                    if (size > 0) buffer.copyOf(size) else ByteArray(0)
                }
                check(SyncManager.classifySnapshotFile(file.name, head) == SyncManager.SnapshotFileKind.SNAPSHOT &&
                    SyncManager.snapshotTargetName(file.name, head) != null) { tr(R.string.backup_snapshot_invalid, file.name) }
            }
            val count = merge(snapshots).getOrThrow()
            check(count > 0) { tr(R.string.backup_ui_28) }
            check(count == snapshots.size) { tr(R.string.backup_snapshot_partial, count, snapshots.size) }
            Result.success(count)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            staging.deleteRecursively()
        }
    }

    /** 用户词典以快照合并；完整备份复用本地恢复入口及其收尾。 */
    suspend fun restore(context: Context, entry: BackupEntry): Outcome = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            val cacheDir = File(context.cacheDir, "os-restore/${UUID.randomUUID()}")
            val file = File(cacheDir, "backup.zip")
            try {
                val full = backupType(entry.key) ?: error(tr(R.string.backup_ui_29))
                val config = ObjectStorageConfigStore.of(context).load()
                check(config.isConfigured) { tr(R.string.backup_ui_20) }
                val bytes = when (val result = ObjectStorageClient(config).getObject(entry.key)) {
                    is ObjectStorageClient.Result.Success -> result.value
                    is ObjectStorageClient.Result.Failure -> error(result.message)
                }
                check(cacheDir.mkdirs() || cacheDir.isDirectory) { tr(R.string.backup_ui_26) }
                file.writeBytes(bytes)
                if (full) {
                    BackupManager.restoreFromArchive(context, file).getOrThrow()
                    Outcome.Success(tr(R.string.backup_ui_30), entry.key)
                } else {
                    val count = restoreDictionaryArchive(file, File(cacheDir, "snapshots")) {
                        SyncManager.mergeSnapshots(context, it)
                    }.getOrThrow()
                    Outcome.Success(tr(R.string.backup_snapshot_merged, count), entry.key)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Outcome.Failure(e.message ?: tr(R.string.backup_ui_31))
            } finally {
                cacheDir.deleteRecursively()
            }
        }
    }

    suspend fun deleteBackupResult(context: Context, entry: BackupEntry): Result<Unit> = operationMutex.withLock {
        withContext(Dispatchers.IO) {
            try {
                check(backupType(entry.key) != null) { tr(R.string.backup_ui_29) }
                val config = ObjectStorageConfigStore.of(context).load()
                check(config.isConfigured) { tr(R.string.backup_ui_20) }
                when (val result = ObjectStorageClient(config).deleteObject(entry.key)) {
                    is ObjectStorageClient.Result.Success -> Result.success(Unit)
                    is ObjectStorageClient.Result.Failure -> Result.failure(IllegalStateException(result.message))
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    /** 兼容旧接口，新页面使用保留失败原因的 Result。 */
    suspend fun deleteBackup(context: Context, entry: BackupEntry): Boolean = deleteBackupResult(context, entry).isSuccess
}