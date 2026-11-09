package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.Context
import android.content.SharedPreferences
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.db.ClipboardDatabase
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

// 顶级类型供设置页、IME 共用。
enum class SyncPhase { Idle, Running, Success, UpToDate, Error }
data class SyncStatus(
    val phase: SyncPhase = SyncPhase.Idle,
    val lastSuccessAt: Long? = null,
    val message: String? = null,
)

/** 应用级唯一同步入口。手动调用只执行一轮，不注册剪贴板监听。 */
class ObjectStorageSyncCoordinator internal constructor(
    private val context: Context,
    private val perform: (suspend () -> ClipboardObjectStoreSync.SyncOutcome)? = null,
) {
    companion object {
        @Volatile private var instance: ObjectStorageSyncCoordinator? = null
        fun of(context: Context): ObjectStorageSyncCoordinator = instance ?: synchronized(this) {
            instance ?: ObjectStorageSyncCoordinator(context.applicationContext).also { instance = it }
        }
    }

    private val clipboardManager by lazy { ClipboardManager.getInstance(context) }
    internal val engine by lazy {
        ClipboardObjectStoreSync(context, clipboardManager, ClipboardDatabase.getInstance(context).clipboardDao())
    }
    private val mutex = Mutex()
    private val requestScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    @Volatile private var automaticScope: CoroutineScope? = null
    private val prefs = SettingsPreferences.getPrefsPublic(context)
    private val configurationLock = Any()
    private var configurationGeneration = 0L
    private val mutableStatus = MutableStateFlow(SyncStatus(lastSuccessAt =
        prefs.getLong(ClipboardObjectStoreSync.PREF_LAST_UPDATED, 0L).takeIf {
            it > 0 && prefs.getString(SavedSyncTarget.PREF_KEY, null) == SavedSyncTarget.identity(prefs)
        }))
    private val configurationListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == null || key in SavedSyncTarget.CONFIG_KEYS) resetForSavedConfiguration()
    }

    init {
        if (prefs.contains(ClipboardObjectStoreSync.PREF_LAST_UPDATED) &&
            prefs.getString(SavedSyncTarget.PREF_KEY, null) != SavedSyncTarget.identity(prefs)) {
            resetForSavedConfiguration()
        }
        prefs.registerOnSharedPreferenceChangeListener(configurationListener)
    }

    private fun resetForSavedConfiguration() = synchronized(configurationLock) {
        configurationGeneration++
        mutableStatus.value = SyncStatus()
        lastAutomaticSuccess = 0L
        prefs.edit().remove(ClipboardObjectStoreSync.PREF_LAST_SHA)
            .remove(ClipboardObjectStoreSync.PREF_LAST_UPDATED).remove(SavedSyncTarget.PREF_KEY).apply()
    }
    val status: StateFlow<SyncStatus> = mutableStatus.asStateFlow()
    @Volatile private var lastAutomaticSuccess = 0L

    @Synchronized
    fun startAutomatic() {
        if (automaticScope != null || !automaticEnabled()) return
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        automaticScope = scope
        val changes = Channel<Unit>(Channel.CONFLATED)
        clipboardManager.onEntryDeleted = { uuid ->
            engine.markDeleted(uuid)
            changes.trySend(Unit)
        }
        // 采集与执行分离：下载图片会 emit 变更，不能阻塞自己正在消费的 SharedFlow。
        scope.launch { clipboardManager.clipboardChanged.collect { changes.trySend(Unit) } }
        scope.launch {
            try {
                for (ignored in changes) runSync(throttle = false, automatic = true)
            } finally { changes.close() }
        }
    }

    @Synchronized
    fun stopAutomatic() {
        automaticScope?.cancel()
        automaticScope = null
        lastAutomaticSuccess = 0L
        clipboardManager.onEntryDeleted = null
    }

    @Synchronized
    fun requestAutomatic(throttle: Boolean = true) {
        if (!automaticEnabled()) return
        automaticScope?.launch { runSync(throttle, automatic = true) }
    }

    fun requestManual() { requestScope.launch { syncNow() } }
    suspend fun syncNow(): ClipboardObjectStoreSync.SyncOutcome = runSync(throttle = false)
    suspend fun syncNow(context: Context): ClipboardObjectStoreSync.SyncOutcome = of(context).syncNow()

    internal suspend fun runSync(throttle: Boolean, automatic: Boolean = false): ClipboardObjectStoreSync.SyncOutcome = mutex.withLock {
        if (automatic && (!automaticEnabled() || automaticScope == null)) return@withLock ClipboardObjectStoreSync.SyncOutcome.Skipped
        if (throttle && System.currentTimeMillis() - lastAutomaticSuccess < 30_000L) return@withLock ClipboardObjectStoreSync.SyncOutcome.Skipped
        val (generation, previous) = synchronized(configurationLock) {
            val previous = mutableStatus.value
            mutableStatus.value = previous.copy(phase = SyncPhase.Running, message = null)
            configurationGeneration to previous
        }
        try {
            val result = perform?.invoke() ?: engine.performSync()
            synchronized(configurationLock) {
                if (generation != configurationGeneration) return@withLock ClipboardObjectStoreSync.SyncOutcome.Failure("同步配置已改变，请重试")
                mutableStatus.value = when (result) {
                    is ClipboardObjectStoreSync.SyncOutcome.Success -> previous.copy(phase = SyncPhase.Success, lastSuccessAt = System.currentTimeMillis(), message = null)
                    ClipboardObjectStoreSync.SyncOutcome.UpToDate -> previous.copy(phase = SyncPhase.UpToDate, lastSuccessAt = System.currentTimeMillis(), message = null)
                    is ClipboardObjectStoreSync.SyncOutcome.Failure -> previous.copy(phase = SyncPhase.Error, message = result.message)
                    ClipboardObjectStoreSync.SyncOutcome.Skipped -> previous
                }
                if (result is ClipboardObjectStoreSync.SyncOutcome.Success || result == ClipboardObjectStoreSync.SyncOutcome.UpToDate) {
                    lastAutomaticSuccess = System.currentTimeMillis()
                }
            }
            result
        } catch (e: CancellationException) {
            synchronized(configurationLock) {
                if (generation == configurationGeneration) mutableStatus.value = previous.copy(phase = SyncPhase.Idle, message = null)
            }
            throw e
        } catch (e: Exception) {
            synchronized(configurationLock) {
                if (generation == configurationGeneration) mutableStatus.value = previous.copy(phase = SyncPhase.Error, message = e.message ?: "同步失败")
            }
            ClipboardObjectStoreSync.SyncOutcome.Failure(e.message ?: "同步失败")
        }
    }

    private fun automaticEnabled() = SettingsPreferences.isClipboardSyncEnabled(context) &&
        ClipboardObjectStoreSync.isBuiltinEnabled(context)
}

/** 成功记录绑定已保存配置，避免重启后把旧桶的成功时间展示给新桶。 */
internal object SavedSyncTarget {
    const val PREF_KEY = "os_last_clipboard_target"
    val CONFIG_KEYS = setOf("os_protocol", "os_endpoint", "os_bucket", "os_ak", "os_sk", "os_sk_enc", "os_private")
    fun identity(prefs: SharedPreferences): String = ObjectStorageHashes.sha256Hex(
        CONFIG_KEYS.sorted().joinToString("\n") { key -> key + "=" + prefs.all[key]?.toString().orEmpty() }
    )
}
