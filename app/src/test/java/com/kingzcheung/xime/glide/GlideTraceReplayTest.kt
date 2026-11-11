package com.kingzcheung.xime.glide

import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * 真实轨迹回放台（离线评测）。
 *
 * 把采集到的 glide_samples.jsonl 原样喂给真实的 [GlideGeometryDecoder]，
 * 输出 top-k / 几何可达性 / 平均编辑距离。用于验证解码器改动是否真的变好，
 * 而不是靠手感判断。
 *
 * 样本不在仓库里（属于用户数据）：
 *   1. 放到 app/src/test/resources/glide/samples.jsonl（已在 .gitignore），或
 *   2. 跑测试时用 -Dglide.replay=<绝对路径> 指定。
 * 两者都没有时整体跳过，CI 不会因为缺数据变红。
 */
class GlideTraceReplayTest {

    private fun sampleFile(): File {
        val prop = System.getProperty("glide.replay")
        if (!prop.isNullOrBlank()) return File(prop)
        return File("src/test/resources/glide/samples.jsonl")
    }

    private fun flypySpellings(): List<String> = FlypySpellings.all()

    private data class Sample(
        val id: String,
        val code: String,
        val expected: String,
        val points: List<GlidePoint>,
        val geometry: GlideGeometrySnapshot,
    )

    private fun load(file: File): List<Sample> {
        if (!file.isFile) return emptyList()
        val objs = file.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        val labels = HashMap<String, String>()
        for (o in objs) {
            if (o.optString("e") == "label") labels[o.optString("id")] = o.optString("expected")
        }
        return objs.filter { it.optString("e") == "trace" }.mapNotNull { o ->
            val keysArr = o.optJSONArray("keys") ?: return@mapNotNull null
            val keys = (0 until keysArr.length()).map { i ->
                val k = keysArr.getJSONObject(i)
                GlideKey(
                    id = k.getString("id"),
                    code = k.getString("code"),
                    rect = GlideKeyRect(
                        k.getDouble("l").toFloat(), k.getDouble("t").toFloat(),
                        k.getDouble("r").toFloat(), k.getDouble("b").toFloat(),
                    ),
                )
            }
            val ptsArr = o.optJSONArray("points") ?: return@mapNotNull null
            val points = (0 until ptsArr.length()).map { i ->
                val p = ptsArr.getJSONArray(i)
                GlidePoint(p.getDouble(0).toFloat(), p.getDouble(1).toFloat(), p.getLong(2), 1)
            }
            // v2 样本不再写 width/height：从键矩形外接框推出来
            val width = o.optDouble("width", 0.0).toFloat()
                .takeIf { it > 0f } ?: keys.maxOf { it.rect.right }
            val height = o.optDouble("height", 0.0).toFloat()
                .takeIf { it > 0f } ?: keys.maxOf { it.rect.bottom }
            val id = o.getString("id")
            Sample(
                id = id,
                code = o.optString("code"),
                expected = labels[id] ?: "",
                points = points,
                geometry = GlideGeometrySnapshot(
                    o.optString("geometryVersion", "replay"), keys, width, height,
                ),
            )
        }
    }

    private fun editDistance(a: String, b: String): Int {
        val dp = Array(a.length + 1) { IntArray(b.length + 1) }
        for (i in 0..a.length) dp[i][0] = i
        for (j in 0..b.length) dp[0][j] = j
        for (i in 1..a.length) for (j in 1..b.length) {
            dp[i][j] = minOf(
                dp[i - 1][j] + 1, dp[i][j - 1] + 1,
                dp[i - 1][j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1,
            )
        }
        return dp[a.length][b.length]
    }

    @Test
    fun 真实轨迹回放报告() {
        val all = load(sampleFile())
        if (all.isEmpty()) {
            println("[replay] 无回放样本，跳过（见类注释）")
            return
        }
        val usable = all.filter { it.expected.isNotEmpty() && it.expected != "无效内容" }
        assertTrue("样本应可解析", usable.isNotEmpty())

        val spellings = flypySpellings()
        var top1 = 0
        var reachable = 0
        var distSum = 0
        println("[replay] 轨迹=" + all.size + " 可用=" + usable.size + " 音节编码数=" + spellings.size)
        for (s in usable) {
            val hyps = GlideGeometryDecoder.decode(s.points, s.geometry, 16, spellings, 420f)
            val codes = hyps.map { it.letters }
            if (codes.firstOrNull() == s.expected) top1++
            if (s.expected in codes) reachable++
            val d = editDistance(s.code, s.expected)
            distSum += d
            val rank = codes.indexOf(s.expected)
            println(
                "[replay] " + s.id + " n=" + codes.size + " top=" + codes.firstOrNull() +
                    " exp=" + s.expected + " 可达=" + (s.expected in codes) +
                    " 位次=" + rank + " d=" + d
            )
        }
        val avg = distSum.toDouble() / usable.size
        println(
            "[replay] top-1=" + top1 + "/" + usable.size +
                " 几何可达=" + reachable + "/" + usable.size +
                " 平均距离=" + String.format("%.2f", avg)
        )
        // 可达但不在 top-1 → 只能靠重排救；根本不可达 → 只能靠改搜索
        println(
            "[replay] 诊断：可达但非 top-1=" + (reachable - top1) +
                "（重排可救），根本不可达=" + (usable.size - reachable) +
                "（必须改搜索）"
        )
    }
}
