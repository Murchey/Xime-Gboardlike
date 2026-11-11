package com.kingzcheung.xime.glide

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 采集器的 id 唯一性与标注关联。
 *
 * 风险：id 形如 g{HHmmss}-{seq}。若两个进程在同一秒启动且 seq 未从文件续接，
 * 会产生重复 id，导致标注串到别的轨迹上——直接影响评测数据可信度。
 */
@RunWith(AndroidJUnit4::class)
class GlideTraceRecorderDeviceTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    private fun freshFile(name: String): File {
        val f = File(context.filesDir, name)
        f.delete()
        return f
    }

    @Test
    fun 同一进程内id应唯一() {
        // 用现有文件做只读验证：解析已有的 trace 行，检查 id 是否重复
        val f = File(context.filesDir, GlideTraceRecorder.FILE_NAME)
        if (!f.isFile) { println("[rec] 无采集文件，跳过"); return }
        val ids = f.readLines(Charsets.UTF_8)
            .filter { it.isNotBlank() }
            .mapNotNull { runCatching { JSONObject(it) }.getOrNull() }
            .filter { it.optString("e") == "trace" }
            .map { it.optString("id") }
        val dup = ids.groupBy { it }.filter { it.value.size > 1 }.keys
        assertEquals("trace id 不应重复: " + dup, 0, dup.size)
        android.util.Log.i("GlideRec", "trace 条数=" + ids.size + " 重复=" + dup.size)
    }

    @Test
    fun 每条trace应有对应的keys与points() {
        val f = File(context.filesDir, GlideTraceRecorder.FILE_NAME)
        if (!f.isFile) { println("[rec] 无采集文件，跳过"); return }
        var missingKeys = 0
        var missingPoints = 0
        var shortPoints = 0
        var total = 0
        f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
            if (o.optString("e") != "trace") return@forEach
            total++
            val keys = o.optJSONArray("keys")
            val pts = o.optJSONArray("points")
            if (keys == null || keys.length() == 0) missingKeys++
            if (pts == null || pts.length() == 0) missingPoints++ else if (pts.length() < 2) shortPoints++
        }
        val msg = "trace=" + total + " 缺keys=" + missingKeys +
            " 缺points=" + missingPoints + " 点过少=" + shortPoints
        android.util.Log.i("GlideRec", msg)
        assertEquals("每条 trace 都必须带键位快照: " + msg, 0, missingKeys)
        assertEquals("每条 trace 都必须带轨迹点: " + msg, 0, missingPoints)
    }

    @Test
    fun 标注应能关联到存在的trace() {
        val f = File(context.filesDir, GlideTraceRecorder.FILE_NAME)
        if (!f.isFile) { println("[rec] 无采集文件，跳过"); return }
        val traces = HashSet<String>()
        val labels = mutableListOf<Pair<String, String>>()
        f.readLines(Charsets.UTF_8).filter { it.isNotBlank() }.forEach { line ->
            val o = runCatching { JSONObject(line) }.getOrNull() ?: return@forEach
            when (o.optString("e")) {
                "trace" -> traces += o.optString("id")
                "label" -> labels += o.optString("id") to o.optString("expected")
            }
        }
        val orphan = labels.filter { it.first !in traces }.map { it.first }
        val msg = "trace=" + traces.size + " label=" + labels.size + " 孤儿标注=" + orphan
        android.util.Log.i("GlideRec", msg)
        assertEquals("标注必须能关联到 trace: " + msg, 0, orphan.size)
    }
}
