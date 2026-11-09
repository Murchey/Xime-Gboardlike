package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.clipboard.ClipboardImageStore
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.db.ClipboardDao
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 内置对象存储剪切板同步引擎（与插件 ClipboardSyncBridge 并列，互斥启用）。
 *
 * 数据落点（严格按用户设计）：
 * - `XimeGboard/index.json`：全量清单 + 最新更新时间 + 清单 SHA256
 * - `XimeGboard/content/<uuid>.json`：每条内容；图片条目的 json 里带 `files/img` 相对路径
 * - `XimeGboard/content/files/img/<hash>.<ext>`：图片二进制（内容寻址去重）
 *
 * uuid 由内容派生，无需改 Room schema：[ClipboardEntry] 文本用 sha256(text)，
 * 图片用既有 imageHash。跨设备同一内容天然同 uuid、天然去重。
 *
 * 触发：打开剪切板面板（[syncOnClipboardOpen]）+ 复制完东西（[clipboardChanged] 订阅）。
 * index SHA 与本地记录不一致才真正传输，避免每次都全量拉取。
 */
class ClipboardObjectStoreSync(
    private val context: Context,
    private val clipboardManager: ClipboardManager,
    private val dao: ClipboardDao,
    private val configStore: ObjectStorageConfigStore = ObjectStorageConfigStore.of(context),
    private val clientFactory: (com.kingzcheung.xime.objectstorage.ObjectStorageConfig) -> ObjectStorageClient = { ObjectStorageClient(it) },
) {
    companion object {
        private const val TAG = "ClipboardObjSync"
        const val PREF_ENGINE = "clipboard_sync_engine"
        const val ENGINE_BUILTIN = "builtin"
        const val ENGINE_PLUGIN = "plugin"
        const val PREF_MODE = "clipboard_sync_mode"
        const val MODE_PUSH = "push"
        const val MODE_BIDIRECTIONAL = "bidirectional"
        const val PREF_LAST_SHA = "os_last_clipboard_sha"
        const val PREF_LAST_UPDATED = "os_last_clipboard_updated"
        private const val INDEX_KEY = "XimeGboard/index.json"
        private const val CONTENT_PREFIX = "XimeGboard/content"
        private const val IMG_PREFIX = "XimeGboard/content/files/img"

        fun isBuiltinEnabled(context: Context): Boolean =
            SettingsPreferences.getPrefsPublic(context)
                .getString(PREF_ENGINE, ENGINE_PLUGIN) == ENGINE_BUILTIN

        fun mode(context: Context): String =
            SettingsPreferences.getPrefsPublic(context).getString(PREF_MODE, MODE_BIDIRECTIONAL)
                ?: MODE_BIDIRECTIONAL
    }

    // 旧公开 API 保留；自动和手动触发均交给应用级协调器。
    fun start() = ObjectStorageSyncCoordinator.of(context).startAutomatic()
    fun release() = ObjectStorageSyncCoordinator.of(context).stopAutomatic()
    fun syncOnClipboardOpen() = ObjectStorageSyncCoordinator.of(context).requestAutomatic(throttle = true)
    fun syncNow() = ObjectStorageSyncCoordinator.of(context).requestManual()

    internal suspend fun sync(throttle: Boolean): SyncOutcome =
        ObjectStorageSyncCoordinator.of(context).runSync(throttle)

    internal suspend fun performSync(): SyncOutcome = withContext(Dispatchers.IO) {
        val config = configStore.load()
        if (!config.isConfigured) return@withContext SyncOutcome.Failure("对象存储未配置")
        val client = clientFactory(config)
        val syncMode = mode(context)
        val prefs = SettingsPreferences.getPrefsPublic(context)
        val now = System.currentTimeMillis()
        val targetIdentity = SavedSyncTarget.identity(prefs)
        try {
            val localItems = buildLocalItems()
            val tombstones = TombstoneStore.decode(prefs.getString(TombstoneStore.PREF_KEY, null))
            val localIndex = ClipboardIndexCalculator.build(localItems, tombstones, now)
            val remoteIndex = readRemoteIndex(client)
            val plan = when (syncMode) {
                MODE_PUSH -> ClipboardIndexCalculator.pushOnly(localIndex, remoteIndex, now)
                else -> ClipboardIndexCalculator.merge(localIndex, remoteIndex, now)
            }
            val finalIndex = ClipboardIndexCalculator.build(plan.mergedItems, plan.mergedTombstones, now)
            val localDeletes = localItems.filter { l -> plan.mergedTombstones.any { it.uuid == l.uuid } }
            if (plan.isEmpty && localDeletes.isEmpty() && remoteIndex.sha256 == finalIndex.sha256) {
                currentCoroutineContext().ensureActive()
                check(configStore.load() == config && mode(context) == syncMode) { "同步配置已改变，请重试" }
                recordSuccess(finalIndex, System.currentTimeMillis(), targetIdentity)
                return@withContext SyncOutcome.UpToDate
            }
            val itemMap = localItems.associateBy { it.uuid }
            for (uuid in plan.toUpload) {
                currentCoroutineContext().ensureActive()
                check(uploadContent(client, uuid, itemMap.getValue(uuid))) { "上传失败：$uuid" }
            }
            val remoteItems = remoteIndex.items.associateBy { it.uuid }
            for (uuid in plan.toDownload) {
                currentCoroutineContext().ensureActive()
                check(downloadContent(client, remoteItems.getValue(uuid))) { "下载或内容校验失败：$uuid" }
            }
            // 先持久化墓碑，后续网络失败也不能让远端删除的条目复活。
            TombstoneStore.mergeInto(prefs, plan.mergedTombstones)
            for (item in localDeletes) {
                currentCoroutineContext().ensureActive()
                findEntryByUuid(item.uuid)?.let { dao.deleteClipboardById(it.id) }
            }
            for (uuid in plan.toDelete) {
                currentCoroutineContext().ensureActive()
                check(deleteContent(client, uuid)) { "删除远端内容失败：$uuid" }
            }
            currentCoroutineContext().ensureActive()
            check(configStore.load() == config && mode(context) == syncMode) { "同步配置已改变，请重试" }
            check(putIndex(client, finalIndex)) { "写入远端索引失败" }
            currentCoroutineContext().ensureActive()
            check(configStore.load() == config && mode(context) == syncMode) { "同步配置已改变，请重试" }
            recordSuccess(finalIndex, System.currentTimeMillis(), targetIdentity)
            SyncOutcome.Success(plan.toUpload.size, plan.toDownload.size, (plan.toDelete + localDeletes.map { it.uuid }).distinct().size)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "sync failed", e)
            SyncOutcome.Failure(e.message ?: "同步失败")
        }
    }

    private fun recordSuccess(index: ClipboardIndex, now: Long, targetIdentity: String) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        TombstoneStore.mergeInto(prefs, index.tombstones)
        check(SavedSyncTarget.identity(prefs) == targetIdentity) { "同步配置已改变，请重试" }
        prefs.edit().putString(PREF_LAST_SHA, index.sha256).putLong(PREF_LAST_UPDATED, now)
            .putString(SavedSyncTarget.PREF_KEY, targetIdentity).apply()
    }

    // ── 本地清单 ────────────────────────────────────────────

    /** 把 Room 里全部历史条目（排除快捷发送）映射成 index items。 */
    internal suspend fun buildLocalItems(): List<ClipboardIndexItem> = withContext(Dispatchers.IO) {
        val entries = dao.allClipboardOnce()
        entries.mapNotNull { e ->
            val uuid = uuidOf(e) ?: return@mapNotNull null
            val sha = contentShaOf(e)
            ClipboardIndexItem(uuid, sha, e.timestamp, e.type)
        }
    }

    internal fun uuidOf(e: ClipboardEntry): String? = when {
        e.type == ClipboardEntry.TYPE_IMAGE && e.imageHash.isNotBlank() -> "img_${e.imageHash}"
        e.text.isNotBlank() -> "txt_${ObjectStorageHashes.sha256Hex(e.text)}"
        else -> null
    }

    private fun contentShaOf(e: ClipboardEntry): String = when {
        e.type == ClipboardEntry.TYPE_IMAGE && e.imageHash.isNotBlank() -> e.imageHash
        else -> ObjectStorageHashes.sha256Hex(e.text)
    }

    // ── 远端交互 ────────────────────────────────────────────

    private suspend fun readRemoteIndex(client: ObjectStorageClient): ClipboardIndex {
        return when (val r = client.getObject(INDEX_KEY)) {
            is ObjectStorageClient.Result.Success ->
                ClipboardIndex.parseRemote(String(r.value, Charsets.UTF_8))
            is ObjectStorageClient.Result.Failure ->
                if (r.httpCode == 404 && r.message.contains("NoSuchKey") && !r.message.contains("NoSuchBucket"))
                    ClipboardIndex.empty() else error(r.message)
        }
    }

    private suspend fun putIndex(client: ObjectStorageClient, index: ClipboardIndex): Boolean =
        client.putObject(INDEX_KEY, index.toJson().toString().toByteArray(Charsets.UTF_8), "application/json")
            .let { it.valueOrThrow(); true }

    private suspend fun uploadContent(client: ObjectStorageClient, uuid: String, item: ClipboardIndexItem): Boolean {
        val entry = findEntryByUuid(uuid) ?: return false
        check(contentShaOf(entry) == item.sha256) { "本地内容已改变：$uuid" }
        val json = contentJson(uuid, entry)
        if (entry.type == ClipboardEntry.TYPE_IMAGE) {
            val file = clipboardManager.imageFileOf(entry.toItem()) ?: return false
            val bytes = file.readBytes()
            check(ObjectStorageHashes.sha256Hex(bytes) == item.sha256) { "本地图片校验失败：$uuid" }
            val ext = file.extension.lowercase().ifEmpty { ClipboardImageStore.sniffExtension(bytes) ?: "png" }
            json.put("imagePath", "files/img/${entry.imageHash}.$ext")
            client.putObject("$IMG_PREFIX/${entry.imageHash}.$ext", bytes,
                ClipboardImageStore.mimeForExtension(ext) ?: "application/octet-stream").valueOrThrow()
        }
        // 图片先落 blob，再发布内容对象；最后由 index 一并公布。
        client.putObject("$CONTENT_PREFIX/$uuid.json", json.toString().toByteArray(Charsets.UTF_8),
            "application/json").valueOrThrow()
        return true
    }

    private fun <T> ObjectStorageClient.Result<T>.valueOrThrow(): T = when (this) {
        is ObjectStorageClient.Result.Success -> value
        is ObjectStorageClient.Result.Failure -> error(message)
    }

    private suspend fun downloadContent(client: ObjectStorageClient, expected: ClipboardIndexItem): Boolean {
        val uuid = expected.uuid
        val raw = when (val r = client.getObject("$CONTENT_PREFIX/$uuid.json")) {
            is ObjectStorageClient.Result.Success -> r.value
            is ObjectStorageClient.Result.Failure -> error(r.message)
        }
        val o = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull() ?: return false
        val type = o.optString("type", "text")
        if (o.optString("uuid") != uuid || type != expected.type) return false
        return if (type == "image") {
            if (o.optString("imageHash") != expected.sha256 || uuid != "img_${expected.sha256}") return false
            downloadImage(client, o, expected)
        } else {
            val text = o.optString("text", "")
            if (text.isBlank() || ObjectStorageHashes.sha256Hex(text) != expected.sha256 ||
                uuid != "txt_${expected.sha256}") return false
            val existing = dao.findByText(text)
            dao.insert(ClipboardEntry(id = existing?.id ?: 0, text = text,
                timestamp = expected.updatedAt, isPinned = o.optBoolean("isPinned", false)))
            true
        }
    }

    private suspend fun downloadImage(client: ObjectStorageClient, o: JSONObject, item: ClipboardIndexItem): Boolean {
        val imgRel = o.optString("imagePath", "")
        if (!imgRel.matches(Regex("files/img/[a-f0-9]{64}\\.[a-z0-9]+"))) return false
        val bytes = when (val r = client.getObject("XimeGboard/content/$imgRel")) {
            is ObjectStorageClient.Result.Success -> r.value
            else -> return false
        }
        val expected = o.optString("imageHash", "")
        if (expected.isNotBlank() && ObjectStorageHashes.sha256Hex(bytes) != expected) {
            Log.e(TAG, "remote image hash mismatch, discard")
            return false
        }
        if (ClipboardImageStore.sniffExtension(bytes) == null) return false
        val importedPath = clipboardManager.importImageFromSync(bytes, imgRel.substringAfterLast('/')) ?: return false
        val importedFile = java.io.File(importedPath)
        if (!importedFile.isFile || ObjectStorageHashes.sha256Hex(importedFile.readBytes()) != item.sha256) return false
        val imported = dao.findImageByHash(item.sha256) ?: return false
        dao.updateTimestamp(imported.id, item.updatedAt)
        dao.updatePinned(imported.id, o.optBoolean("isPinned", false))
        return true
    }

    private suspend fun deleteContent(client: ObjectStorageClient, uuid: String): Boolean =
        client.deleteObject("$CONTENT_PREFIX/$uuid.json")
            .let { it is ObjectStorageClient.Result.Success }

    private suspend fun findEntryByUuid(uuid: String): ClipboardEntry? =
        dao.allClipboardOnce().firstOrNull { uuidOf(it) == uuid }

    private fun contentJson(uuid: String, e: ClipboardEntry): JSONObject = JSONObject()
        .put("uuid", uuid)
        .put("type", e.type)
        .put("text", e.text)
        .put("timestamp", e.timestamp)
        .put("isPinned", e.isPinned)
        .apply {
            if (e.type == ClipboardEntry.TYPE_IMAGE) {
                val ext = e.imagePath.substringAfterLast('.', "png")
                put("imageHash", e.imageHash)
                put("imagePath", "files/img/${e.imageHash}.$ext")
                put("mimeType", e.mimeType)
                put("sizeBytes", e.sizeBytes)
                put("width", e.width)
                put("height", e.height)
            }
        }

    /** 记一条本地删除墓碑（供服务层在删除条目时调用）。 */
    fun markDeleted(uuid: String) {
        val prefs = SettingsPreferences.getPrefsPublic(context)
        TombstoneStore.mergeInto(prefs, listOf(ClipboardTombstone(uuid, System.currentTimeMillis())))
    }

    sealed class SyncOutcome {
        object Skipped : SyncOutcome()
        object UpToDate : SyncOutcome()
        data class Success(val uploaded: Int, val downloaded: Int, val deleted: Int) : SyncOutcome()
        data class Failure(val message: String) : SyncOutcome()
    }
}

/** 把 Room entry 转成 ClipboardItem（仅供图片取文件用）。 */
private fun ClipboardEntry.toItem() = com.kingzcheung.xime.clipboard.ClipboardItem(
    id = id, text = text, timestamp = timestamp, isPinned = isPinned,
    type = type, imagePath = imagePath, imageHash = imageHash,
    mimeType = mimeType, sizeBytes = sizeBytes, width = width, height = height,
)