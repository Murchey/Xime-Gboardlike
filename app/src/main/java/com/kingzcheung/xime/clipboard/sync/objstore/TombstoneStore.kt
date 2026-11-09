package com.kingzcheung.xime.clipboard.sync.objstore

import org.json.JSONArray
import org.json.JSONObject

/** 本机删除墓碑的持久化（纯 JSON，存在 kime_settings 里，不动 Room schema）。 */
object TombstoneStore {
    const val PREF_KEY = "os_clipboard_tombstones"

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
