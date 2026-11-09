package com.kingzcheung.xime.clipboard.sync.objstore

import android.content.SharedPreferences

internal class SyncTestPreferences : SharedPreferences {
    val values = java.util.concurrent.ConcurrentHashMap<String, Any>()
    override fun getAll(): MutableMap<String, *> = values
    override fun getString(key: String, defValue: String?): String? = values[key] as? String ?: defValue
    override fun getLong(key: String, defValue: Long) = values[key] as? Long ?: defValue
    override fun getBoolean(key: String, defValue: Boolean) = values[key] as? Boolean ?: defValue
    override fun getInt(key: String, defValue: Int) = values[key] as? Int ?: defValue
    override fun getFloat(key: String, defValue: Float) = values[key] as? Float ?: defValue
    override fun getStringSet(key: String, defValue: MutableSet<String>?): MutableSet<String>? = defValue
    override fun contains(key: String) = values.containsKey(key)
    private val listeners = mutableSetOf<SharedPreferences.OnSharedPreferenceChangeListener>()
    override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listener?.let { listeners.add(it) } }
    override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) { listeners.remove(listener) }
    override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
        private val changed = mutableSetOf<String>()
        override fun putString(key: String, value: String?) = apply { if (values[key] != value) changed.add(key); if (value == null) values.remove(key) else values[key] = value }
        override fun putLong(key: String, value: Long) = apply { if (values[key] != value) changed.add(key); values[key] = value }
        override fun putBoolean(key: String, value: Boolean) = apply { if (values[key] != value) changed.add(key); values[key] = value }
        override fun putInt(key: String, value: Int) = apply { if (values[key] != value) changed.add(key); values[key] = value }
        override fun putFloat(key: String, value: Float) = apply { if (values[key] != value) changed.add(key); values[key] = value }
        override fun putStringSet(key: String, value: MutableSet<String>?) = this
        override fun remove(key: String) = apply { if (values.remove(key) != null) changed.add(key) }
        override fun clear() = apply { changed.addAll(values.keys); values.clear() }
        override fun commit(): Boolean { apply(); return true }
        override fun apply() {
            val keys = changed.toList()
            changed.clear()
            keys.forEach { key -> listeners.toList().forEach { it.onSharedPreferenceChanged(this@SyncTestPreferences, key) } }
        }
    }
}
