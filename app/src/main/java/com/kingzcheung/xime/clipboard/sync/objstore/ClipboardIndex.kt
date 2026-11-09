package com.kingzcheung.xime.clipboard.sync.objstore

import org.json.JSONArray
import org.json.JSONObject

/**
 * 剪切板同步索引：云端 `XimeGboard/index.json` 的内容模型。
 *
 * [sha256] 覆盖「规范化后的 items 清单」（不含 updatedAt 本身），这样
 * 本机与远端只需比对一个哈希就能判断清单是否一致；不一致再走 [diff]。
 *
 * 删除用墓碑：条目被本机删掉时把 uuid 记入 [tombstones]（带 deletedAt），
 * 同步时据此 DELETE 远端对象，并避免「远端还有 → 又被拉回来」的复活问题。
 */
data class ClipboardIndexItem(
    val uuid: String,
    val sha256: String,
    val updatedAt: Long,
    /** text / image */
    val type: String = "text",
) {
    fun toJson(): JSONObject = JSONObject()
        .put("uuid", uuid)
        .put("sha256", sha256)
        .put("updatedAt", updatedAt)
        .put("type", type)

    companion object {
        fun fromJson(o: JSONObject): ClipboardIndexItem = ClipboardIndexItem(
            uuid = o.optString("uuid", ""),
            sha256 = o.optString("sha256", ""),
            updatedAt = o.optLong("updatedAt", 0L),
            type = o.optString("type", "text"),
        )
    }
}

data class ClipboardTombstone(val uuid: String, val deletedAt: Long) {
    fun toJson(): JSONObject = JSONObject().put("uuid", uuid).put("deletedAt", deletedAt)
    companion object {
        fun fromJson(o: JSONObject): ClipboardTombstone =
            ClipboardTombstone(o.optString("uuid", ""), o.optLong("deletedAt", 0L))
    }
}

data class ClipboardIndex(
    val updatedAt: Long = 0L,
    val sha256: String = "",
    val items: List<ClipboardIndexItem> = emptyList(),
    val tombstones: List<ClipboardTombstone> = emptyList(),
) {
    fun toJson(): JSONObject = JSONObject()
        .put("updatedAt", updatedAt)
        .put("sha256", sha256)
        .put("items", JSONArray().apply { items.forEach { put(it.toJson()) } })
        .put("tombstones", JSONArray().apply { tombstones.forEach { put(it.toJson()) } })

    companion object {
        /** 空索引（首次配置 / 云端无对象时）。 */
        fun empty(): ClipboardIndex = ClipboardIndex()

        fun fromJson(text: String): ClipboardIndex = runCatching {
            val o = JSONObject(text)
            val itemsArr = o.optJSONArray("items")
            val tombsArr = o.optJSONArray("tombstones")
            val items = (0 until (itemsArr?.length() ?: 0)).mapNotNull { i ->
                runCatching { ClipboardIndexItem.fromJson(itemsArr!!.getJSONObject(i)) }.getOrNull()
            }.filter { it.uuid.isNotBlank() }
            val tombstones = (0 until (tombsArr?.length() ?: 0)).mapNotNull { i ->
                runCatching { ClipboardTombstone.fromJson(tombsArr!!.getJSONObject(i)) }.getOrNull()
            }.filter { it.uuid.isNotBlank() }
            ClipboardIndex(
                updatedAt = o.optLong("updatedAt", 0L),
                sha256 = o.optString("sha256", ""),
                items = items,
                tombstones = tombstones,
            )
        }.getOrElse { empty() }
    }
}

/**
 * 同步计划：双向合并 / 增量推送共用的差异结果（纯数据）。
 *
 * @param toUpload 本机需要 PUT 的 uuid（新增或内容变更）
 * @param toDownload 远端需要 GET 的 uuid（本机缺失或远端较新）
 * @param toDelete 需要 DELETE 的 uuid（本机墓碑 / 远端墓碑）
 */
data class SyncPlan(
    val toUpload: List<String> = emptyList(),
    val toDownload: List<String> = emptyList(),
    val toDelete: List<String> = emptyList(),
    val mergedItems: List<ClipboardIndexItem> = emptyList(),
    val mergedTombstones: List<ClipboardTombstone> = emptyList(),
) {
    val isEmpty: Boolean
        get() = toUpload.isEmpty() && toDownload.isEmpty() && toDelete.isEmpty()
}

object ClipboardIndexCalculator {
    /**
     * 规范化清单 JSON（排序 + 固定字段顺序），供 SHA-256 计算。
     * 不含 updatedAt / tombstones，避免「时间变了但内容没变」导致无谓传输。
     */
    fun canonicalItemsJson(items: List<ClipboardIndexItem>): String {
        val arr = JSONArray()
        items.sortedBy { it.uuid }.forEach { item ->
            arr.put(JSONObject()
                .put("sha256", item.sha256)
                .put("type", item.type)
                .put("updatedAt", item.updatedAt)
                .put("uuid", item.uuid))
        }
        return arr.toString()
    }

    /** 由清单构造带哈希的索引（纯函数）。 */
    fun build(items: List<ClipboardIndexItem>, tombstones: List<ClipboardTombstone> = emptyList(), now: Long): ClipboardIndex {
        val merged = mergeByUuid(items)
        return ClipboardIndex(
            updatedAt = now,
            sha256 = ObjectStorageHashes.sha256Hex(canonicalItemsJson(merged)),
            items = merged,
            tombstones = mergeTombstones(tombstones),
        )
    }

    /** 同一 uuid 只保留 updatedAt 最新的一条。 */
    internal fun mergeByUuid(items: List<ClipboardIndexItem>): List<ClipboardIndexItem> =
        items.groupBy { it.uuid }
            .mapValues { (_, group) -> group.maxByOrNull { it.updatedAt }!! }
            .values
            .sortedBy { it.uuid }
            .toList()

    internal fun mergeTombstones(tombstones: List<ClipboardTombstone>): List<ClipboardTombstone> =
        tombstones.groupBy { it.uuid }
            .mapValues { (_, group) -> group.maxByOrNull { it.deletedAt }!! }
            .values
            .sortedBy { it.uuid }
            .toList()

    /**
     * 双向合并（用户选「同步储存筒内容」）：按 uuid 合并，同 uuid 取 updatedAt 较新者。
     *
     * 规则：
     * - 远端有、本机无 → 下载
     * - 本机有、远端无 且 无远端墓碑 → 上传
     * - 两边都有但 sha 不同 → 取 updatedAt 较新的一方（下载或上传）
     * - 墓碑：任一侧的墓碑都导致 DELETE，且该 uuid 不进入合并清单（不复活）
     * - 本地删除减量：本机墓碑里且远端仍存在的 uuid → DELETE 远端对象
     */
    fun merge(
        local: ClipboardIndex,
        remote: ClipboardIndex,
        now: Long,
    ): SyncPlan {
        val localMap = mergeByUuid(local.items).associateBy { it.uuid }
        val remoteMap = mergeByUuid(remote.items).associateBy { it.uuid }
        val allTombstones = mergeTombstones(local.tombstones + remote.tombstones)
        val tombstoneUuids = allTombstones.map { it.uuid }.toSet()

        val upload = mutableListOf<String>()
        val download = mutableListOf<String>()
        val merged = mutableListOf<ClipboardIndexItem>()

        val allUuids = (localMap.keys + remoteMap.keys).toSortedSet()
        for (uuid in allUuids) {
            if (uuid in tombstoneUuids) continue // 墓碑优先：不复活
            val l = localMap[uuid]
            val r = remoteMap[uuid]
            when {
                l == null && r != null -> { download += uuid; merged += r }
                l != null && r == null -> { upload += uuid; merged += l }
                l != null && r != null -> {
                    when {
                        l.sha256 == r.sha256 -> merged += l
                        r.updatedAt > l.updatedAt -> { download += uuid; merged += r }
                        else -> { upload += uuid; merged += l }
                    }
                }
            }
        }

        // 删除：远端仍在、但已被任一侧标记删除的对象
        val delete = (tombstoneUuids intersect remoteMap.keys).toList()

        return SyncPlan(
            toUpload = upload.sorted(),
            toDownload = download.sorted(),
            toDelete = delete.sorted(),
            mergedItems = merged,
            mergedTombstones = allTombstones,
        )
    }

    /**
     * 增量推送（用户选「本机为主上传」）：只算本机相对远端需要 PUT / DELETE 的条目，
     * **不产生 toDownload**（不拉远端改动）。
     */
    fun pushOnly(local: ClipboardIndex, remote: ClipboardIndex, now: Long): SyncPlan {
        val remoteMap = mergeByUuid(remote.items).associateBy { it.uuid }
        val tombstoneUuids = mergeTombstones(local.tombstones).map { it.uuid }.toSet()
        val active = mergeByUuid(local.items).filter { it.uuid !in tombstoneUuids }

        val upload = active.filter { l ->
            val r = remoteMap[l.uuid]
            r == null || r.sha256 != l.sha256 || r.updatedAt < l.updatedAt
        }.map { it.uuid }
        val delete = (tombstoneUuids intersect remoteMap.keys).toList()

        return SyncPlan(
            toUpload = upload.sorted(),
            toDownload = emptyList(),
            toDelete = delete.sorted(),
            mergedItems = active,
            mergedTombstones = mergeTombstones(local.tombstones),
        )
    }
}
