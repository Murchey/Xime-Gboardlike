package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.rime.RimeEngine
import java.io.File

/**
 * 真实样本端到端回放：读设备上的 glide_samples.jsonl，用真实 controller
 * （真实几何 + 真实 Rime 引擎 + 真实排序）逐条回放。
 *
 * 注意样本标注的是**双拼编码**（如 vhgr），不是中文文本——
 * 所以这里比较的是"解码出的编码"，与 GlideTraceReplayTest 口径一致；
 * 区别在于本测试跑完整链路（含 Rime 查询与候选排序）。
 */
@RunWith(AndroidJUnit4::class)
class GlideE2eReplayDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun sampleFile(): File = File(context.filesDir, "glide_samples.jsonl")

    private data class Sample(
        val id: String,
        val expected: String,
        val points: List<GlidePoint>,
        val keys: List<GlideKey>,
        val width: Float,
        val height: Float,
    )

    private fun load(): List<Sample> {
        val f = sampleFile()
        if (!f.isFile) return emptyList()
        val objs = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        val labels = HashMap<String, String>()
        for (o in objs) {
            if (o.optString("e") == "label") labels[o.optString("id")] = o.optString("expected")
        }
        return objs.filter { it.optString("e") == "trace" }.mapNotNull { o ->
            val keysArr = o.optJSONArray("keys") ?: return@mapNotNull null
            val keys = (0 until keysArr.length()).map { i ->
                val k = keysArr.getJSONObject(i)
                GlideKey(k.getString("id"), k.getString("code"),
                    GlideKeyRect(k.getDouble("l").toFloat(), k.getDouble("t").toFloat(),
                        k.getDouble("r").toFloat(), k.getDouble("b").toFloat()))
            }
            val ptsArr = o.optJSONArray("points") ?: return@mapNotNull null
            val pts = mutableListOf<GlidePoint>()
            for (i in 0 until ptsArr.length()) {
                val a = ptsArr.getJSONArray(i)
                pts += GlidePoint(a.getDouble(0).toFloat(), a.getDouble(1).toFloat(), a.getLong(2), 1)
            }
            val id = o.getString("id")
            Sample(id, labels[id] ?: "", pts, keys,
                keys.maxOf { it.rect.right }, keys.maxOf { it.rect.bottom })
        }
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private data class Result(val raw: String, val cands: List<String>, val hyps: List<String>)

    private fun replay(s: Sample): Result {
        val geo = GlideGeometrySnapshot("e2e", s.keys, s.width, s.height)
        val registry = GlideGeometryRegistry()
        registry.beginFrame(geo.widthPx, geo.heightPx)
        geo.keys.forEach { registry.register(it) }
        val ctl = GlideKeyboardController(
            registry,
            GlideCoordinator(RimeGlideSentenceLookup(), RimeGlideCommitter()),
            glideEnabled = { true },
            schemaSpellings = { schema ->
                RimeEngine.getInstance().getGlideSpellings(schema, GlideSyllableTrie.syllables())
            },
            densityDpi = { 420f },
        )
        val pts = s.points
        if (pts.size < 2) return Result("", emptyList(), emptyList())
        ctl.onDown(1, pts.first().x, pts.first().y, "e2e", "double_pinyin_flypy",
            false, pts.first().timeMs)
        for (p in pts.drop(1)) ctl.onMove(1, p.x, p.y, false, p.timeMs)
        ctl.onUp(1, "e2e")
        return Result(ctl.rawInputForDisplay(), ctl.candidateTexts().take(5), emptyList())
    }

    @Test
    fun 真实样本端到端回放() {
        val (u, s) = RimeConfigHelper.initializeRimeData(context)
        RimeEngine.getInstance().initialize(u, s)
        RimeEngine.getInstance().switchSchema("double_pinyin_flypy")

        val all = load()
        if (all.isEmpty()) { println("[e2e-replay] 无样本，跳过"); return }
        val usable = all.filter { it.expected.isNotEmpty() && it.expected != "无效内容" }

        var rawExact = 0
        var hasCands = 0
        var distSum = 0
        val rows = mutableListOf<String>()
        for ((idx, sample) in usable.withIndex()) {
            if (idx > 0) RimeEngine.getInstance().switchSchema("double_pinyin_flypy")
            val r = replay(sample)
            val exact = r.raw == sample.expected
            if (exact) rawExact++
            if (r.cands.isNotEmpty()) hasCands++
            distSum += editDistance(r.raw, sample.expected)
            rows += sample.id + " exp=" + sample.expected + " raw=" + r.raw +
                " cands=" + r.cands.take(2) + (if (exact) " OK" else "")
        }
        // 平均编辑距离比"完全一致率"更敏感：能反映"从差 5 键变成差 1 键"这类进步。
        // 历史基线：采样时的解码器 1.94（见 docs 第十五轮）。
        val avg = if (usable.isEmpty()) 0.0 else distSum.toDouble() / usable.size
        val summary = "端到端回放 编码完全一致=" + rawExact + "/" + usable.size +
            " 平均距离=" + String.format("%.2f", avg) +
            " 有候选=" + hasCands + "/" + usable.size
        println("[e2e-replay] " + summary)
        rows.forEach { println("[e2e-replay] " + it) }
        // 记录基线：编码完全一致 6/16（JVM 回放口径为 5/16，差 1 条来自候选排序阶段）。
        // 这里只断言链路通，不断言具体数字——避免方案词典变化导致测试脆弱。
        rows.forEach { println("[e2e-replay] " + it) }
        // 把摘要写进断言消息：通过时不显示，失败时可见。
        // 需要读数时临时把条件改成 false（见 git 历史）。
        org.junit.Assert.assertEquals(summary, usable.size, hasCands)
        // 同时写进 logcat，便于 adb logcat 抓取
        android.util.Log.i("GlideE2e", summary)
        rows.forEach { android.util.Log.i("GlideE2e", it) }
    }
}
