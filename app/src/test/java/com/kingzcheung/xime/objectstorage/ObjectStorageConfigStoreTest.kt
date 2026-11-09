package com.kingzcheung.xime.objectstorage

import android.content.SharedPreferences
import com.kingzcheung.xime.plugin.ValueCipher
import org.junit.Assert.*
import org.junit.Test

class ObjectStorageConfigStoreTest {
    private class Prefs : SharedPreferences {
        val values = mutableMapOf<String, Any>()
        var failNextCommit = false
        var editors = 0
        override fun getAll(): MutableMap<String, *> = values.toMutableMap()
        override fun getString(k: String, d: String?) = values[k] as? String ?: d
        override fun getBoolean(k: String, d: Boolean) = values[k] as? Boolean ?: d
        override fun getInt(k: String, d: Int) = values[k] as? Int ?: d
        override fun getLong(k: String, d: Long) = values[k] as? Long ?: d
        override fun getFloat(k: String, d: Float) = values[k] as? Float ?: d
        override fun getStringSet(k: String, d: MutableSet<String>?) = d
        override fun contains(k: String) = values.containsKey(k)
        override fun registerOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(l: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun edit(): SharedPreferences.Editor {
            editors++
            return object : SharedPreferences.Editor {
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
                    if (failNextCommit) { failNextCommit = false; return false }
                    return true
                }
                override fun apply() { commit() }
            }
        }
    }
    private val cipher = object : ValueCipher {
        override fun encrypt(plain: String) = "encrypted:" + plain.reversed()
        override fun decrypt(stored: String) = stored.removePrefix("encrypted:").reversed()
    }
    private val config = ObjectStorageConfig(endpoint = "old.example.com", accessKeyId = "id", secretKey = "old-secret")

    @Test fun savesEncryptedSecretAndFieldsInOneCommit() {
        val prefs = Prefs()
        prefs.values[ObjectStorageConfigStore.KEY_SK] = "legacy"
        val store = ObjectStorageConfigStore(prefs, cipher)
        assertEquals(ObjectStorageConfigStore.SaveResult.Success, store.save(config))
        assertEquals(1, prefs.editors)
        assertEquals(config, store.load())
        assertFalse(prefs.contains(ObjectStorageConfigStore.KEY_SK))
        assertFalse(prefs.values[ObjectStorageConfigStore.KEY_SK_ENC].toString().contains("old-secret"))
    }

    @Test fun encryptionFailureNeverChangesAnyPreference() {
        val prefs = Prefs()
        val store = ObjectStorageConfigStore(prefs, cipher)
        store.save(config)
        val previous = prefs.values.toMap()
        val editors = prefs.editors
        val broken = object : ValueCipher {
            override fun encrypt(plain: String): String = error("keystore unavailable")
            override fun decrypt(stored: String) = stored
        }
        assertEquals(ObjectStorageConfigStore.SaveResult.EncryptionFailure,
            ObjectStorageConfigStore(prefs, broken).save(config.copy(endpoint = "new.example.com")))
        assertEquals(previous, prefs.values)
        assertEquals(editors, prefs.editors)
    }

    @Test fun failedDiskCommitRestoresPreviousFieldsAndSecretWithoutTouchingOtherSettings() {
        val prefs = Prefs()
        val store = ObjectStorageConfigStore(prefs, cipher)
        store.save(config)
        prefs.values["unrelated"] = "keep"
        val previous = prefs.values.toMap()
        prefs.failNextCommit = true
        assertEquals(ObjectStorageConfigStore.SaveResult.PersistenceFailure,
            store.save(config.copy(endpoint = "new.example.com", secretKey = "new-secret", privateAuth = false)))
        assertEquals(previous, prefs.values)
        assertEquals(config, store.load())
    }

    @Test fun legacyMigrationEncryptionFailureStillLoadsAndKeepsLegacySecret() {
        val prefs = Prefs()
        prefs.values[ObjectStorageConfigStore.KEY_SK] = "legacy"
        val broken = object : ValueCipher {
            override fun encrypt(plain: String): String = error("keystore unavailable")
            override fun decrypt(stored: String) = stored
        }
        assertEquals("legacy", ObjectStorageConfigStore(prefs, broken).load().secretKey)
        assertEquals("legacy", prefs.values[ObjectStorageConfigStore.KEY_SK])
        assertFalse(prefs.contains(ObjectStorageConfigStore.KEY_SK_ENC))
    }
}
