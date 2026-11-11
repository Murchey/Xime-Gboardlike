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
 * 真实轨迹采集器（用于训练/评估 glide 解码）。
 *
 * 只在显式开启时写文件（设置里默认关闭，避免记录日常输入）。
 * 每条记录包含：轨迹点序列、几何快照、以及用户确认后的期望文本。
 *
 * 数据落 `filesDir/glide_samples.jsonl`（每行一条 JSON），由开发者选项里的
 * 「滑动轨迹数据」页面导出（分享 / 保存到下载）。
 *
 * 采集到的数据用于：
 * 1. 评估当前解码准确率（真实数据而非合成轨迹）；
 * 2. 离线训练/拟合"停留 vs 经过"的判定参数。
 */
object GlideTraceRecorder {

    private const val TAG = "GlideTraceRecorder"
    const val FILE_NAME = "glide_samples.jsonl"
    private const val MAX_RECORDS = 2000

    /** 开关（由设置同步，见 XimeApplication / 开发者选项）。 */
    @Volatile
    var enabled: Boolean = false

    /** 采集文件；null 表示未初始化。 */
    @Volatile
    private var sampleFile: File? = null

    /** 待写样本：后台解码线程写，主线程确认时读，故用 @Volatile。 */
    @Volatile
    private var pending: Pending? = null

    private data class Pending(
        val points: List<GlidePoint>,
        val geometry: GlideGeometrySnapshot,
        val schemaFingerprint: String,
        /** 本次手势追加的前缀编码（有前缀时整串才是期望编码）。 */
        val prefix: String,
        /** 几何解码直接产出的候选文本（首选在前）。 */
        val candidates: List<String>,
        /** 实际装进组合的完整编码；确认上屏时据此校验，防止期望文本错位。 */
        val code: String,
    )

    /** 记录一次滑动结束时的轨迹。不会立即落盘——需要等用户确认候选后
     * 由 [attachExpected] 补上期望文本再写。
     *
     * @param prefix 本次手势追加的已有编码前缀
     * @param candidates 几何解码的假设文本（首选在前），用于分析"几何排序是否出错"
     */
    fun onTraceFinished(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        schemaFingerprint: String,
        prefix: String = "",
        candidates: List<String> = emptyList(),
    ) {
        if (!enabled) return
        if (points.size < 2) return
        pending = Pending(points, geometry, schemaFingerprint, prefix, candidates, prefix + candidates.firstOrNull().orEmpty())
    }

    /**
     * 候选真正装进组合后调用：记录实际编码，作为确认时的校验依据
     * （几何首选假设与安装的编码可能不同）。
     */
    fun onCandidatesInstalled(rawInput: String) {
        val p = pending ?: return
        pending = p.copy(code = rawInput)
    }

    /**
     * 用户确认了候选文本后调用，把期望结果补上并落盘。
     *
     * @param currentInput 确认时组合里的编码；与记录的编码不一致（用户中途改过编码）
     *   说明这条轨迹不再对应这次上屏，直接丢弃，避免污染评测集。
     */
    fun attachExpected(expectedText: String, currentInput: String? = null) {
        if (!enabled) return
        val p = pending ?: return
        pending = null
        if (currentInput != null && currentInput != p.code) return
        if (expectedText.isEmpty()) return
        write(p, expectedText)
    }

    /** 滑动被取消：丢弃待写记录。 */
    fun discard() {
        pending = null
    }

    fun init(context: Context) {
        if (sampleFile != null) return
        sampleFile = File(context.filesDir, FILE_NAME)
    }

    /** 采集文件（未初始化时为 null）。 */
    fun file(): File? = sampleFile

    /** 已采集条数（文件不存在或未初始化为 0）；读文件，勿在主线程对大文件调用。 */
    fun recordCount(): Int {
        val f = sampleFile ?: return 0
        return if (f.exists()) countLines(f) else 0
    }

    /** 文件体积（字节），用于导出页展示。 */
    fun sizeBytes(): Long = sampleFile?.takeIf { it.exists() }?.length() ?: 0L

    /** 清空采集数据。 */
    fun clearSamples() {
        pending = null
        runCatching { sampleFile?.delete() }
    }

    /** 复制一份导出用的临时文件；无数据时返回 null。调用方负责删除。 */
    fun exportCopy(): File? {
        val f = sampleFile ?: return null
        if (!f.exists() || f.length() == 0L) return null
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val dir = File(f.parentFile ?: return null, "exports").apply { mkdirs() }
        val out = File(dir, "xime_glide_$stamp.jsonl")
        runCatching { f.copyTo(out, overwrite = true) }
            .onFailure { Log.w(TAG, "导出复制失败: " + it.message) }
        return out.takeIf { it.exists() }
    }

    private fun write(p: Pending, expectedText: String) {
        val file = sampleFile ?: return
        try {
            file.parentFile?.mkdirs()
            if (file.exists() && countLines(file) >= MAX_RECORDS) return

            val obj = JSONObject().apply {
                put("v", 1)
                put("t", System.currentTimeMillis())
                put("expected", expectedText)
                put("prefix", p.prefix)
                put("schema", p.schemaFingerprint)
                put("geometryVersion", p.geometry.version)
                put("width", p.geometry.widthPx.toDouble())
                put("height", p.geometry.heightPx.toDouble())
                put("points", JSONArray().apply {
                    p.points.forEach { pt ->
                        put(JSONArray().apply {
                            put(pt.x.toDouble()); put(pt.y.toDouble()); put(pt.timeMs)
                        })
                    }
                })
                put("keys", JSONArray().apply {
                    p.geometry.keys.forEach { k ->
                        put(JSONObject().apply {
                            put("id", k.id); put("code", k.code)
                            put("l", k.rect.left.toDouble()); put("t", k.rect.top.toDouble())
                            put("r", k.rect.right.toDouble()); put("b", k.rect.bottom.toDouble())
                            put("row", k.row); put("col", k.col)
                        })
                    }
                })
                put("candidates", JSONArray().apply {
                    p.candidates.forEach { put(it) }
                })
            }
            file.appendText(obj.toString() + "\n")
        } catch (e: Throwable) {
            Log.w(TAG, "写入采集数据失败: " + e.message)
        }
    }

    private fun countLines(f: File): Int = try {
        f.useLines { it.count() }
    } catch (_: Throwable) { 0 }
}
