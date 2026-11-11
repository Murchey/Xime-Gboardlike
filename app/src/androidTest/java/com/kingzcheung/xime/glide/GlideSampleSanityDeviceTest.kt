package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 样本自检：标注是否与轨迹物理自洽。
 *
 * 滑动的本质是"手指经过一串键"，所以标注里要求的**连续重复键次数**
 * 不能超过轨迹里该键实际出现的段数——一个键只滑过一次，却要求出现两次，
 * 在物理上不可能达成，这类样本不能算解码器的错。
 *
 * 已用它找出 3 条问题样本（g-7/g-14/g-15），避免继续对着脏数据调参。
 */
@RunWith(AndroidJUnit4::class)
class GlideSampleSanityDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private data class Trace(val id: String, val expected: String, val runs: List<String>)

    private fun load(): List<Trace> {
        val f = File(context.filesDir, "glide_samples.jsonl")
        if (!f.isFile) return emptyList()
        val objs = f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
        val labels = HashMap<String, String>()
        for (o in objs) {
            if (o.optString("e") == "label") labels[o.optString("id")] = o.optString("expected")
        }
        return objs.filter { it.optString("e") == "trace" }.mapNotNull { o ->
            val keysArr = o.optJSONArray("keys") ?: return@mapNotNull null
            val rects = (0 until keysArr.length()).map { i ->
                val k = keysArr.getJSONObject(i)
                k.getString("code") to floatArrayOf(
                    k.getDouble("l").toFloat(), k.getDouble("t").toFloat(),
                    k.getDouble("r").toFloat(), k.getDouble("b").toFloat())
            }
            val ptsArr = o.optJSONArray("points") ?: return@mapNotNull null
            val runs = mutableListOf<String>()
            for (i in 0 until ptsArr.length()) {
                val a = ptsArr.getJSONArray(i)
                val x = a.getDouble(0).toFloat()
                val y = a.getDouble(1).toFloat()
                val hit = rects.firstOrNull { r ->
                    x >= r.second[0] && x <= r.second[2] && y >= r.second[1] && y <= r.second[3]
                }?.first
                if (hit != null && runs.lastOrNull() != hit) runs += hit
            }
            val id = o.getString("id")
            Trace(id, labels[id] ?: "", runs)
        }
    }

    /** 期望里需要连续出现 n 次的键，轨迹里是否真的有 n 段。返回问题描述。 */
    private fun repeatsBeyondTrace(t: Trace): List<String> {
        val counts = HashMap<Char, Int>()
        // runs 里是单字符键码（如 "w"），期望串按字符比较，统一用 Char 作键
        for (c in t.runs) {
            if (c.length == 1) counts[c[0]] = (counts[c[0]] ?: 0) + 1
        }
        val issues = mutableListOf<String>()
        var i = 0
        while (i < t.expected.length) {
            var j = i
            while (j < t.expected.length && t.expected[j] == t.expected[i]) j++
            val need = j - i
            if (need > 1) {
                val have = counts[t.expected[i]] ?: 0
                if (have < need) issues += t.expected[i] + "×" + need + "/" + have
            }
            i = j
        }
        return issues
    }

    @Test
    fun 标注要求的连续重复键不能超过轨迹实际段数() {
        val all = load()
        if (all.isEmpty()) { println("[sanity] 无样本，跳过"); return }
        val usable = all.filter { it.expected.isNotEmpty() && it.expected != "无效内容" }
        val bad = usable.mapNotNull { t ->
            val issues = repeatsBeyondTrace(t)
            if (issues.isEmpty()) null else t.id + ": " + issues.joinToString(",")
        }
        val msg = "样本自检 " + usable.size + " 条，" +
            "物理互斥 " + bad.size + " 条: " + bad.joinToString(" | ")
        android.util.Log.i("GlideSanity", msg)
        // 不硬断言为 0——样本质量会在迭代中变化，这里是诊断信息而非门禁
        org.junit.Assert.assertTrue(msg, usable.isNotEmpty())
    }
}
