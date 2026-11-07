package com.kingzcheung.xime.settings.declarative

import android.content.SharedPreferences

/**
 * 声明式设置的读写入口。
 *
 * 生产环境走 [SharedPreferencesSettingsStore]，单元测试用 [MapSettingsStore]。
 * 把"读默认值 / 写持久化"收敛到一处，设置声明只描述类型与文案。
 */
interface SettingsStore {
    fun flag(key: String, default: Boolean): Boolean
    fun number(key: String, default: Int): Int
    fun text(key: String, default: String): String

    fun putFlag(key: String, value: Boolean)
    fun putNumber(key: String, value: Int)
    fun putText(key: String, value: String)
}

/** 内存实现：单测使用，也作为 Compose 渲染层的本地快照兜底。 */
class MapSettingsStore(initial: Map<String, Any> = emptyMap()) : SettingsStore {
    val values: MutableMap<String, Any> = LinkedHashMap(initial)

    override fun flag(key: String, default: Boolean): Boolean = values[key] as? Boolean ?: default
    override fun number(key: String, default: Int): Int = values[key] as? Int ?: default
    override fun text(key: String, default: String): String = values[key] as? String ?: default

    override fun putFlag(key: String, value: Boolean) { values[key] = value }
    override fun putNumber(key: String, value: Int) { values[key] = value }
    override fun putText(key: String, value: String) { values[key] = value }
}

/** SharedPreferences 实现：键名与 SettingsPreferences 共用，保证读写同一份数据。 */
class SharedPreferencesSettingsStore(
    private val prefs: SharedPreferences,
) : SettingsStore {
    override fun flag(key: String, default: Boolean): Boolean = prefs.getBoolean(key, default)
    override fun number(key: String, default: Int): Int = prefs.getInt(key, default)
    override fun text(key: String, default: String): String = prefs.getString(key, default) ?: default

    override fun putFlag(key: String, value: Boolean) {
        prefs.edit().putBoolean(key, value).apply()
    }

    override fun putNumber(key: String, value: Int) {
        prefs.edit().putInt(key, value).apply()
    }

    override fun putText(key: String, value: String) {
        prefs.edit().putString(key, value).apply()
    }
}
