package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.clipboard.ClipboardImageStore
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.db.ClipboardDao
import com.kingzcheung.xime.clipboard.db.ClipboardEntry
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
        private const val MIN_SYNC_INTERVAL_MS = 30_000L
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

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    @Volatile
    private var running = false
    @Volatile
    private var lastSyncAt = 0L
    private var changeCollector: Job? = null

    fun start() {
        if (running) return
        running = true
        // 复制完东西 → 增量触发（复用现有 SharedFlow，与插件桥同一数据源）
        changeCollector = clipboardManager.clipboardChanged
            .onEach { scope.launch { syncOnClipboardChange() } }
            .launchIn(scope)
        Log.d(TAG, "started")
    }

    fun release() {
        running = false
        changeCollector?.cancel()
        changeCollector = null
        scope.cancel()
    }

    /** 打开剪切板面板时调用（用户明确要求的同步时机）。 */
    fun syncOnClipboardOpen() {
        scope.launch { sync(throttle = true) }
    }

    private fun syncOnClipboardChange() {
        scope.launch { sync(throttle = false) }
    }

    /** 手动「立即同步」。 */
    fun syncNow() {
        scope.launch { sync(throttle = false) }
    }

    internal suspend fun sync(throttle: Boolean): SyncOutcome = mutex.withLock {
        if (!running) return@withLock SyncOutcome.Skipped
        val now = System.currentTimeMillis()
        if (throttle && now - lastSyncAt < MIN_SYNC_INTERVAL_MS) {
            return@withLock SyncOutcome.Skipped
        }
        val config = configStore.load()
        if (!config.isConfigured) {
            Log.d(TAG, "config incomplete, skip")
            return@withLock SyncOutcome.Failure("对象存储未配置")
        }
        val client = ObjectStorageClient(config)
        val prefs = SettingsPreferences.getPrefsPublic(context)

        lastSyncAt = now
        return@withLock runCatching {
            // 1) 本地清单（Room 全量历史）
            val localItems = buildLocalItems()
            val tombstones = TombstoneStore.decode(prefs.getString(TombstoneStore.PREF_KEY, null))
            val localIndex = ClipboardIndexCalculator.build(localItems, tombstones, now)

            // 2) 远端 index
            val remoteIndex = readRemoteIndex(client)
            val lastSha = prefs.getString(PREF_LAST_SHA, "").orEmpty()
            if (remoteIndex.sha256 == localIndex.sha256 && lastSha == localIndex.sha256) {
                Log.d(TAG, "index sha identical, nothing to do")
                return@withLock SyncOutcome.UpToDate
            }

            // 3) 计算差异（模式决定语义）
            val plan = when (mode(context)) {
                MODE_PUSH -> ClipboardIndexCalculator.pushOnly(localIndex, remoteIndex, now)
                else -> ClipboardIndexCalculator.merge(localIndex, remoteIndex, now)
            }

            // 4) 执行传输
            val itemMap = localItems.associateBy { it.uuid }
            var uploaded = 0
            var downloaded = 0
            var deleted = 0

            for (uuid in plan.toUpload) {
                val item = itemMap[uuid] ?: continue
                if (uploadContent(client, uuid, item)) uploaded++
            }
            for (uuid in plan.toDownload) {
                if (downloadContent(client, uuid)) downloaded++
            }
            for (uuid in plan.toDelete) {
                if (deleteContent(client, uuid)) deleted++
            }

            // 5) 回写 index（清单以合并结果为准）
            val finalIndex = ClipboardIndexCalculator.build(
                plan.mergedItems, plan.mergedTombstones, now
            )
            putIndex(client, finalIndex)
            prefs.edit()
                .putString(PREF_LAST_SHA, finalIndex.sha256)
                .putLong(PREF_LAST_UPDATED, now)
                .putString(TombstoneStore.PREF_KEY, TombstoneStore.encode(plan.mergedTombstones))
                .apply()

            Log.d(TAG, "sync done: up=$uploaded down=$downloaded del=$deleted")
            SyncOutcome.Success(uploaded, downloaded, deleted)
        }.getOrElse { e ->
            Log.e(TAG, "sync failed", e)
            SyncOutcome.Failure(e.message ?: "同步失败")
        }
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
                ClipboardIndex.fromJson(String(r.value, Charsets.UTF_8))
            else -> ClipboardIndex.empty()
        }
    }

    private suspend fun putIndex(client: ObjectStorageClient, index: ClipboardIndex): Boolean =
        client.putObject(INDEX_KEY, index.toJson().toString().toByteArray(Charsets.UTF_8), "application/json")
            .let { it is ObjectStorageClient.Result.Success }

    private suspend fun uploadContent(client: ObjectStorageClient, uuid: String, item: ClipboardIndexItem): Boolean {
        val entry = findEntryByUuid(uuid) ?: return false
        val json = contentJson(uuid, entry)
        val ok = client.putObject(
            "$CONTENT_PREFIX/$uuid.json",
            json.toString().toByteArray(Charsets.UTF_8),
            "application/json",
        ).let { it is ObjectStorageClient.Result.Success }
        if (!ok) return false
        // 图片：额外上传二进制到 files/img/
        if (entry.type == ClipboardEntry.TYPE_IMAGE) {
            val file = clipboardManager.imageFileOf(entry.toItem()) ?: return false
            val ext = file.extension.lowercase().ifEmpty {
                ClipboardImageStore.sniffExtension(file.readBytes()) ?: "png"
            }
            val imgKey = "$IMG_PREFIX/${entry.imageHash}.$ext"
            return client.putObject(imgKey, file.readBytes(), "image/$ext")
                .let { it is ObjectStorageClient.Result.Success }
        }
        return true
    }

    private suspend fun downloadContent(client: ObjectStorageClient, uuid: String): Boolean {
        val raw = when (val r = client.getObject("$CONTENT_PREFIX/$uuid.json")) {
            is ObjectStorageClient.Result.Success -> r.value
            else -> return false
        }
        val o = runCatching { JSONObject(String(raw, Charsets.UTF_8)) }.getOrNull() ?: return false
        val type = o.optString("type", "text")
        return if (type == "image") {
            downloadImage(client, o)
        } else {
            val text = o.optString("text", "")
            if (text.isBlank()) return false
            // 写回本地历史；同步引擎自身的写入由 clipboardChanged 回声抑制兜住
            clipboardManager.addItem(text)
            true
        }
    }

    private suspend fun downloadImage(client: ObjectStorageClient, o: JSONObject): Boolean {
        val imgRel = o.optString("imagePath", "")
        if (imgRel.isBlank()) return false
        val bytes = when (val r = client.getObject("XimeGboard/content/$imgRel")) {
            is ObjectStorageClient.Result.Success -> r.value
            else -> return false
        }
        val expected = o.optString("imageHash", "")
        if (expected.isNotBlank() && ObjectStorageHashes.sha256Hex(bytes) != expected) {
            Log.e(TAG, "remote image hash mismatch, discard")
            return false
        }
        clipboardManager.importImageFromSync(bytes, imgRel.substringAfterLast('/'))
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
        val list = TombstoneStore.decode(prefs.getString(TombstoneStore.PREF_KEY, null)) +
            ClipboardTombstone(uuid, System.currentTimeMillis())
        prefs.edit().putString(TombstoneStore.PREF_KEY, TombstoneStore.encode(list)).apply()
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
