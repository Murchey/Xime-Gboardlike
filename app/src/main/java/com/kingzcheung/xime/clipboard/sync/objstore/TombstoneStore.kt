package com.kingzcheung.xime.clipboard.sync.objstore

import org.json.JSONArray
import org.json.JSONObject

/** 本机删除墓碑的持久化（纯 JSON，存在 kime_settings 里，不动 Room schema）。 */
object TombstoneStore {
    const val PREF_KEY = "os_clipboard_tombstones"

    /** 与本地删除并发时合并当前值，禁止同步完成覆盖新墓碑。 */
    @Synchronized
    fun mergeInto(prefs: android.content.SharedPreferences, incoming: List<ClipboardTombstone>) {
        val merged = decode(prefs.getString(PREF_KEY, null)) + incoming
        prefs.edit().putString(PREF_KEY, encode(merged)).apply()
    }

    fun decode(raw: String?): List<ClipboardTombstone> = runCatching {
        if (raw.isNullOrBlank()) return emptyList()
        val arr = JSONArray(raw)
        (0 until arr.length()).mapNotNull { i ->
            runCatching {
                val o = arr.getJSONObject(i)
                ClipboardTombstone(o.optString("uuid", ""), o.optLong("deletedAt", 0L))
            }.getOrNull()
        }.filter { it.uuid.isNotBlank() }
    }.getOrElse { emptyList() }

    fun encode(list: List<ClipboardTombstone>): String = JSONArray().apply {
        ClipboardIndexCalculator.mergeTombstones(list).forEach { put(it.toJson()) }
    }.toString()
}
