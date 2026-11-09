package com.kingzcheung.xime.ui.settings

import android.app.Application
import android.content.SharedPreferences
import com.kingzcheung.xime.clipboard.sync.objstore.ClipboardObjectStoreSync
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.settings.SettingsPreferences
import org.junit.Assert.*
import org.junit.Test
import org.mockito.Mockito.*

class ObjectStorageOverviewViewModelTest {
    private class Prefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(k: String, d: String?) = values[k] as? String ?: d
        override fun getBoolean(k: String, d: Boolean) = values[k] as? Boolean ?: d
        override fun getLong(k: String, d: Long) = values[k] as? Long ?: d
        override fun getInt(k: String, d: Int) = values[k] as? Int ?: d
        override fun getFloat(k: String, d: Float) = values[k] as? Float ?: d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun contains(k: String) = values.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.add(l) }
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener) { listeners.remove(l) }
        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            val pending = mutableMapOf<String, Any?>()
            override fun putString(k: String, v: String?) = apply { pending[k] = v }
            override fun putBoolean(k: String, v: Boolean) = apply { pending[k] = v }
            override fun putInt(k: String, v: Int) = apply { pending[k] = v }
            override fun putLong(k: String, v: Long) = apply { pending[k] = v }
            override fun putFloat(k: String, v: Float) = apply { pending[k] = v }
            override fun putStringSet(k: String, v: MutableSet<String>?) = apply { pending[k] = v }
            override fun remove(k: String) = apply { pending[k] = null }
            override fun clear() = apply { values.keys.forEach { pending[it] = null } }
            override fun commit(): Boolean {
                pending.forEach { (k, v) -> if (v == null) values.remove(k) else values[k] = v }
                pending.keys.forEach { k -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@Prefs, k) } }
                return true
            }
            override fun apply() { commit() }
        }
    }
    private fun model(prefs: Prefs): ObjectStorageOverviewViewModel {
        val app = mock(Application::class.java)
        doReturn(prefs).`when`(app).getSharedPreferences(anyString(), anyInt())
        return ObjectStorageOverviewViewModel(app)
    }
    @Test fun modeAndBackupScopeImmediatelyUpdateObservableState() {
        val vm = model(Prefs())
        assertFalse(vm.state.value.pushOnly)
        assertFalse(vm.state.value.fullBackup)
        vm.setPushOnly(true); vm.setFullBackup(true)
        assertTrue(vm.state.value.pushOnly)
        assertTrue(vm.state.value.fullBackup)
    }
    @Test fun configuredBucketChangesRefreshOverviewWithoutRecreatingPage() {
        val prefs = Prefs()
        val vm = model(prefs)
        prefs.edit().putString(ObjectStorageConfigStore.KEY_ENDPOINT, "bucket.example.com").apply()
        assertEquals("bucket.example.com", vm.state.value.config.host)
    }
    @Test fun enablingBeforeConfigurationDoesNotSilentlyEnableSync() {
        val vm = model(Prefs())
        vm.setAutoSync(true)
        assertFalse(vm.state.value.autoSync)
    }
    @Test fun disablingSyncKeepsExistingServiceSelection() {
        val prefs = Prefs()
        prefs.values[ClipboardObjectStoreSync.PREF_ENGINE] = ClipboardObjectStoreSync.ENGINE_PLUGIN
        prefs.values[SettingsPreferences.KEY_CLIPBOARD_SYNC_ENABLED] = true
        val vm = model(prefs)
        vm.setAutoSync(false)
        assertFalse(vm.state.value.autoSync)
        assertFalse(vm.state.value.builtin)
    }
    @Test fun configuredEnableSelectsObjectStorageAndOffStopsWithoutChangingMode() {
        val prefs = Prefs()
        prefs.values[ObjectStorageConfigStore.KEY_ENDPOINT] = "http://127.0.0.1:18765"
        prefs.values[ObjectStorageConfigStore.KEY_PRIVATE] = false
        val vm = model(prefs)
        vm.setPushOnly(true)
        vm.setAutoSync(true)
        assertTrue(vm.state.value.autoSync)
        assertTrue(vm.state.value.builtin)
        vm.setAutoSync(false)
        assertFalse(vm.state.value.autoSync)
        assertTrue(vm.state.value.pushOnly)
    }
    @Test fun pluginPageEnableSelectsPluginAndIsReflectedImmediately() {
        val vm = model(Prefs())
        vm.setPluginAutoSync(true)
        assertTrue(vm.state.value.autoSync)
        assertFalse(vm.state.value.builtin)
    }
}
