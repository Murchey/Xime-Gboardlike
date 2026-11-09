package com.kingzcheung.xime.ui.settings

import android.app.Application
import android.content.SharedPreferences
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.clipboard.sync.objstore.ClipboardObjectStoreSync
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StorageOverviewState(
    val config: ObjectStorageConfig = ObjectStorageConfig(),
    val autoSync: Boolean = false,
    val builtin: Boolean = false,
    val pushOnly: Boolean = false,
    val fullBackup: Boolean = false,
    val lastSyncAt: Long = 0,
    val backingUp: Boolean = false,
    val backupMessage: String? = null,
    val backupError: Boolean = false,
)

class ObjectStorageOverviewViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = SettingsPreferences.getPrefsPublic(app)
    private val store = ObjectStorageConfigStore.of(app)
    private val mutableState = MutableStateFlow(StorageOverviewState())
    val state = mutableState.asStateFlow()
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> refresh() }
    init { refresh(); prefs.registerOnSharedPreferenceChangeListener(listener) }
    private fun refresh() {
        mutableState.update { it.copy(
            config = store.load(),
            autoSync = SettingsPreferences.isClipboardSyncEnabled(getApplication()),
            builtin = ClipboardObjectStoreSync.isBuiltinEnabled(getApplication()),
            pushOnly = ClipboardObjectStoreSync.mode(getApplication()) == ClipboardObjectStoreSync.MODE_PUSH,
            fullBackup = ObjectStorageBackupManager.isFullSchemasEnabled(getApplication()),
            lastSyncAt = prefs.getLong(ClipboardObjectStoreSync.PREF_LAST_UPDATED, 0),
        ) }
    }
    fun setAutoSync(enabled: Boolean) {
        if (enabled && !state.value.config.isConfigured) return
        // 同一批偏好通知看到的是完整选择，关闭不擅自切换原服务。
        prefs.edit().apply {
            if (enabled) putString(ClipboardObjectStoreSync.PREF_ENGINE, ClipboardObjectStoreSync.ENGINE_BUILTIN)
            putBoolean(SettingsPreferences.KEY_CLIPBOARD_SYNC_ENABLED, enabled)
        }.apply()
    }
    fun setPluginAutoSync(enabled: Boolean) {
        prefs.edit().putString(ClipboardObjectStoreSync.PREF_ENGINE, ClipboardObjectStoreSync.ENGINE_PLUGIN)
            .putBoolean(SettingsPreferences.KEY_CLIPBOARD_SYNC_ENABLED, enabled).apply()
    }
    fun setPushOnly(push: Boolean) {
        prefs.edit().putString(ClipboardObjectStoreSync.PREF_MODE,
            if (push) ClipboardObjectStoreSync.MODE_PUSH else ClipboardObjectStoreSync.MODE_BIDIRECTIONAL).apply()
    }
    fun setFullBackup(full: Boolean) {
        ObjectStorageBackupManager.setFullSchemasEnabled(getApplication(), full)
        mutableState.update { it.copy(backupMessage = null, backupError = false) }
    }
    fun backupNow() {
        if (state.value.backingUp || !state.value.config.isConfigured) return
        mutableState.update { it.copy(backingUp = true, backupMessage = null, backupError = false) }
        viewModelScope.launch {
            try {
                when (val result = ObjectStorageBackupManager.backupNow(getApplication())) {
                    is ObjectStorageBackupManager.Outcome.Success -> mutableState.update { it.copy(backupMessage = result.message) }
                    is ObjectStorageBackupManager.Outcome.Failure -> mutableState.update { it.copy(backupMessage = result.message, backupError = true) }
                }
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { mutableState.update { it.copy(backupMessage = e.message, backupError = true) } }
            finally { mutableState.update { it.copy(backingUp = false) } }
        }
    }
    override fun onCleared() { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
}
