package com.kingzcheung.xime.glide

import android.content.Context
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 真实轨迹采集器（用于评估/调试 glide 解码）。
 *
 * **滑动一结束就落盘，不等候选确认**：解码出错时用户确认的恰恰是错文本，
 * 只有先无损保留轨迹，事后才能标记"我实际想打出什么"。
 *
 * 文件 `filesDir/glide_samples.jsonl`，每行一条 JSON，按 id 关联：
 *  - `e=trace`：轨迹本体（点序列、键位几何快照、编码前缀、几何候选），expected 初始为空
 *  - `e=label`：期望文本标注（`src=mark` 开发者手动标记，`src=commit` 确认候选自动记录）；
 *    同一 id 后写的 label 覆盖先前的，导出时按 id 合并
 *
 * 读写策略：只在 [enabled] 时写；最近若干条同时在内存里留摘要，
 * 供「滑动标记」面板列表展示（不为此重复解析整份文件）。
 */
object GlideTraceRecorder {

    private const val TAG = "GlideTraceRecorder"
    const val FILE_NAME = "glide_samples.jsonl"

    /** 文件最多保留的轨迹条数（超出后不再写新的）。 */
    private const val MAX_RECORDS = 2000
    /** 内存里保留、供标记面板展示的最近条数。 */
    private const val RECENT_LIMIT = 20

    /** 采集开关（由设置同步，见 XimeApplication / 滑动轨迹数据页 / 工具栏按钮）。 */
    @Volatile
    var enabled: Boolean = false

    @Volatile
    private var sampleFile: File? = null

    private val lock = Any()

    /** 最近记录摘要（新记录在前），供标记面板展示。 */
    private val recent = ArrayDeque<TraceEntry>()

    /** 本次进程内的记录序号；与启动时间拼成跨进程唯一 id。 */
    private var seq = 0

    /** 进程内 id 前缀：避免重启后 id 重复导致标注串到别的轨迹上。 */
    private val idPrefix: String by lazy {
        val now = SimpleDateFormat("HHmmss", Locale.US).format(Date())
        "g" + now
    }

    /** 一条轨迹的摘要（不含轨迹点，供列表展示与标记）。 */
    data class TraceEntry(
        val id: String,
        val timeMs: Long,
        /** 实际装入组合的完整编码；空表示本次未解出可用编码。 */
        val code: String,
        /** 几何首选候选文本。 */
        val topCandidate: String,
        /** 已标注的期望文本；空表示未标注。 */
        val expected: String = "",
    )

    fun init(context: Context) {
        if (sampleFile != null) return
        sampleFile = File(context.filesDir, FILE_NAME)
        // 冷启动后内存摘要为空，但文件里还有上次进程写的轨迹：
        // 从文件尾部回填最近若干条（面板要能看到重启前的记录）。
        runCatching { loadRecentFromFile() }
    }

    /** 只读文件尾部若干行做回填，不解析整份文件。 */
    private fun loadRecentFromFile() {
        val f = sampleFile ?: return
        if (!f.exists() || f.length() == 0L) return
        val tail = ArrayDeque<String>()
        f.bufferedReader(Charsets.UTF_8).useLines { lines ->
            lines.forEach { line ->
                if (line.isBlank()) return@forEach
                tail.addLast(line)
                while (tail.size > RECENT_LIMIT * 6) tail.removeFirst()
            }
        }
        val entries = parseRecent(tail)
        synchronized(lock) {
            recent.clear()
            entries.forEach { recent.addLast(it) }
            // 新记录继续用递增序号：接续文件里已存在的最大序号，避免 id 重复
            seq = maxOf(seq, entries.mapNotNull { it.id.substringAfterLast('-').toIntOrNull() }.maxOrNull() ?: 0)
        }
    }

    /** trace 行建摘要、label 行回填 expected（后写覆盖先写）；按时间倒序返回最近若干条。 */
    private fun parseRecent(lines: Collection<String>): List<TraceEntry> {
        val byId = LinkedHashMap<String, TraceEntry>()
        for (line in lines) {
            if (line.isBlank()) continue
            val obj = runCatching { JSONObject(line) }.getOrNull() ?: continue
            val id = obj.optString("id")
            if (id.isEmpty()) continue
            when (obj.optString("e")) {
                "trace" -> byId[id] = TraceEntry(
                    id = id,
                    timeMs = obj.optLong("t"),
                    code = obj.optString("code"),
                    topCandidate = obj.optJSONArray("candidates")?.optString(0).orEmpty(),
                )
                "label" -> byId[id]?.let { byId[id] = it.copy(expected = obj.optString("expected")) }
            }
        }
        return byId.values.sortedByDescending { it.timeMs }.take(RECENT_LIMIT)
    }

    /** 仅供单测：解析一段 jsonl 得到最近记录。 */
    internal fun readRecentForTest(file: File): List<TraceEntry> =
        parseRecent(file.readLines(Charsets.UTF_8))

    /** 采集文件（未初始化时为 null）。 */
    fun file(): File? = sampleFile

    /**
     * 记录一次滑动轨迹（立即落盘，无标注）。仅在 [enabled] 时写。
     *
     * @param prefix 本次手势追加的已有编码前缀
     * @param code 实际装入组合的完整编码（未解出时为空串）
     * @param candidates 几何解码候选文本（首选在前），用于区分"几何排序错"与"语言模型排序错"
     */
    fun record(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        schemaFingerprint: String,
        prefix: String,
        code: String,
        candidates: List<String>,
    ): String {
        if (!enabled) return ""
        if (points.size < 2) return ""
        val id = synchronized(lock) { idPrefix + "-" + (++seq) }
        val now = System.currentTimeMillis()
        val entry = TraceEntry(
            id = id,
            timeMs = now,
            code = code,
            topCandidate = candidates.firstOrNull().orEmpty(),
        )
        synchronized(lock) {
            recent.addFirst(entry)
            while (recent.size > RECENT_LIMIT) recent.removeLast()
        }
        write {
            JSONObject().apply {
                put("v", 2)
                put("e", "trace")
                put("id", id)
                put("t", now)
                put("schema", schemaFingerprint)
                put("prefix", prefix)
                put("code", code)
                put("expected", "")
                put("points", JSONArray().apply {
                    points.forEach { pt ->
                        put(JSONArray().apply {
                            put(pt.x.toDouble()); put(pt.y.toDouble()); put(pt.timeMs)
                        })
                    }
                })
                put("keys", JSONArray().apply {
                    geometry.keys.forEach { k ->
                        put(JSONObject().apply {
                            put("id", k.id); put("code", k.code)
                            put("l", k.rect.left.toDouble()); put("t", k.rect.top.toDouble())
                            put("r", k.rect.right.toDouble()); put("b", k.rect.bottom.toDouble())
                            put("row", k.row); put("col", k.col)
                        })
                    }
                })
                put("candidates", JSONArray().apply { candidates.forEach { put(it) } })
            }
        }
        return id
    }

    /**
     * 给某条轨迹标注期望文本（追加一行 label，导出时按 id 后写覆盖）。
     *
     * @param src 标注来源：`mark` 开发者手动标记；`commit` 确认候选自动记录
     */
    fun label(id: String, expected: String, src: String) {
        if (id.isEmpty() || expected.isEmpty()) return
        synchronized(lock) {
            val idx = recent.indexOfFirst { it.id == id }
            if (idx >= 0) recent[idx] = recent[idx].copy(expected = expected)
        }
        write {
            JSONObject().apply {
                put("v", 2)
                put("e", "label")
                put("id", id)
                put("expected", expected)
                put("src", src)
                put("t", System.currentTimeMillis())
            }
        }
    }

    /**
     * 确认上屏时的自动标注：只有确认的编码与这条轨迹记录的编码一致才算同一次滑动。
     *
     * 注意这只是**该次滑动的自动猜测**——解码错时它记下的正是错文本，
     * 所以在「滑动标记」里手动标记会覆盖它（后写覆盖先写）。
     *
     * @param traceId 本次上屏对应的轨迹 id（[recentEntries] 里可见）
     * @param currentInput 确认时组合里的编码
     */
    fun labelConfirmed(traceId: String, expected: String, currentInput: String) {
        if (!enabled || expected.isEmpty()) return
        val target = synchronized(lock) {
            recent.firstOrNull { it.id == traceId } ?: return
        }
        if (target.code.isNotEmpty() && target.code != currentInput) return
        label(target.id, expected, "commit")
    }

    /** 最近记录（新在前）的只读快照。 */
    fun recentEntries(): List<TraceEntry> = synchronized(lock) { recent.toList() }

    /** 未标注的最近条数（面板提示用）。 */
    fun unlabeledCount(): Int = synchronized(lock) { recent.count { it.expected.isEmpty() } }

    /** 已记录的轨迹条数（读文件；勿在主线程对大文件调用）。 */
    fun recordCount(): Int {
        val f = sampleFile ?: return 0
        return if (f.exists()) countLines(f) else 0
    }

    /** 文件体积（字节），用于导出页展示。 */
    fun sizeBytes(): Long = sampleFile?.takeIf { it.exists() }?.length() ?: 0L

    /** 清空采集数据（文件与内存摘要）。 */
    fun clearSamples() {
        synchronized(lock) {
            recent.clear()
            seq = 0
        }
        runCatching { sampleFile?.delete() }
    }

    /** 复制一份导出用的临时文件；无数据时返回 null。调用方负责删除。 */
    fun exportCopy(): File? {
        val f = sampleFile ?: return null
        if (!f.exists() || f.length() == 0L) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(f.parentFile ?: return null, "exports").apply { mkdirs() }
        val out = File(dir, "xime_glide_" + stamp + ".jsonl")
        runCatching { f.copyTo(out, overwrite = true) }
            .onFailure { Log.w(TAG, "导出复制失败: " + it.message) }
        return out.takeIf { it.exists() }
    }

    private fun write(block: () -> JSONObject) {
        val file = sampleFile ?: return
        try {
            val json = block().toString()
            // 容量上限只挡新轨迹，不丢标注（小文件，行数统计代价可接受）
            if (json.contains("\"e\":\"trace\"") && file.exists() && countLines(file) >= MAX_RECORDS) return
            file.parentFile?.mkdirs()
            file.appendText(json + "\n")
        } catch (e: Throwable) {
            Log.w(TAG, "写入采集数据失败: " + e.message)
        }
    }

    private fun countLines(f: File): Int = try {
        f.useLines { it.count() }
    } catch (_: Throwable) { 0 }
}
