package com.kingzcheung.xime.glide

import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 真实轨迹采集器（用于训练/评估 glide 解码）。
 *
 * 只在显式开启时写文件（设置里默认关闭，避免记录日常输入）。
 * 每条记录包含：轨迹点序列、几何快照、以及用户确认后的期望文本。
 *
 * 采集到的数据用于：
 * 1. 评估当前解码准确率（真实数据而非合成轨迹）；
 * 2. 离线训练/拟合"停留 vs 经过"的判定参数。
 */
object GlideTraceRecorder {

    private const val TAG = "GlideTraceRecorder"
    private const val PREF_ENABLED = "glide_trace_recording"
    private const val MAX_RECORDS = 2000

    @Volatile var enabled: Boolean = false

    /** 采集目录；null 表示未初始化。 */
    @Volatile var outputDir: File? = null

    private var pending: Pending? = null

    private data class Pending(
        val points: List<GlidePoint>,
        val geometry: GlideGeometrySnapshot,
        val schemaFingerprint: String,
    )

    /**
     * 记录一次滑动结束时的轨迹。不会立即落盘——需要等用户确认候选后
     * 由 [attachExpected] 补上期望文本再写。
     */
    fun onTraceFinished(
        points: List<GlidePoint>,
        geometry: GlideGeometrySnapshot,
        schemaFingerprint: String,
    ) {
        if (!enabled) return
        if (points.size < 2) return
        pending = Pending(points, geometry, schemaFingerprint)
    }

    /** 用户确认了候选文本后调用，把期望结果补上并落盘。 */
    fun attachExpected(expectedText: String) {
        if (!enabled) return
        val p = pending ?: return
        pending = null
        write(p, expectedText)
    }

    /** 滑动被取消：丢弃待写记录。 */
    fun discard() {
        pending = null
    }

    private fun write(p: Pending, expectedText: String) {
        val dir = outputDir ?: return
        try {
            if (!dir.exists()) dir.mkdirs()
            val file = File(dir, "glide_samples.jsonl")
            if (file.exists() && countLines(file) >= MAX_RECORDS) return

            val obj = JSONObject().apply {
                put("expected", expectedText)
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
