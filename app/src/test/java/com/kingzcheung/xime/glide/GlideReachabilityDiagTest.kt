package com.kingzcheung.xime.glide

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import org.json.JSONObject

/**
 * 诊断「几何不可达」的真实含义：正解是被 limit/beam 截掉，还是从未生成。
 *
 * 用很大的 limit(500) 解码。若正解仍不出现，说明搜索路径根本不存在
 * （对齐约束或代价模型把它排除了），放宽候选数无济于事。
 */
class GlideReachabilityDiagTest {

    private fun sampleFile(): File = File(System.getProperty("glide.replay")
        ?: "src/test/resources/glide/samples.jsonl")

    private fun load(): List<Pair<String, Pair<List<GlidePoint>, GlideGeometrySnapshot>>> {
        val f = sampleFile()
        if (!f.isFile) return emptyList()
        val objs = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        val labels = HashMap<String, String>()
        for (o in objs) if (o.optString("e") == "label") labels[o.optString("id")] = o.optString("expected")
        return objs.filter { it.optString("e") == "trace" }.mapNotNull { o ->
            val keysArr = o.optJSONArray("keys") ?: return@mapNotNull null
            val keys = (0 until keysArr.length()).map { i ->
                val k = keysArr.getJSONObject(i)
                GlideKey(k.getString("id"), k.getString("code"),
                    GlideKeyRect(k.getDouble("l").toFloat(), k.getDouble("t").toFloat(),
                        k.getDouble("r").toFloat(), k.getDouble("b").toFloat()))
            }
            val ptsArr = o.optJSONArray("points") ?: return@mapNotNull null
            val pts = (0 until ptsArr.length()).map { i ->
                val a = ptsArr.getJSONArray(i)
                GlidePoint(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getLong(2), 1)
            }
            val id = o.getString("id")
            val exp = labels[id] ?: return@mapNotNull null
            if (exp == "无效内容") return@mapNotNull null
            exp to (pts to GlideGeometrySnapshot("diag", keys,
                keys.maxOf { it.rect.right }, keys.maxOf { it.rect.bottom }))
        }
    }

    @Test
    fun 大limit下正解是否出现() {
        val all = load()
        if (all.isEmpty()) { println("[diag] 无样本"); return }
        val spellings = FlypySpellings.all()
        val rows = mutableListOf<String>()
        var in16 = 0
        var in500 = 0
        for ((exp, data) in all) {
            val (pts, geo) = data
            val h16 = GlideGeometryDecoder.decode(pts, geo, 16, spellings, 420f).map { it.letters }
            val h500 = GlideGeometryDecoder.decode(pts, geo, 500, spellings, 420f).map { it.letters }
            val a = exp in h16
            val b = exp in h500
            if (a) in16++
            if (b) in500++
            if (!a) rows += "  exp=" + exp + " in16=false in500=" + b +
                " n500=" + h500.size
        }
        val msg = "可达: limit16=" + in16 + "/" + all.size +
            " limit500=" + in500 + "/" + all.size + "\n" + rows.joinToString("\n")
        println("[diag] " + msg)
        assertTrue(msg, all.isNotEmpty())
    }
}
