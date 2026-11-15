package com.kingzcheung.xime.association

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 真机验证「单字 → 双字」联想扩展：用真实 int8 模型跑 NativeOnnxEngine，
 * 再把首字交给 AssociationExpander 续写，检查候选栏拿到的确实是词且排在首字之后。
 */
@RunWith(AndroidJUnit4::class)
class AssociationExpanderDeviceTest {

    private lateinit var context: Context
    private var hasModel = false

    private fun modelDir(): File {
        val unified = File(context.filesDir, "models/predictive-text-small")
        return if (File(unified, "model_int8_dynamic.onnx").exists()) unified else context.filesDir
    }

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        val dir = modelDir()
        val vocabFile = File(dir, "vocab.json")
        val modelFile = File(dir, "model_int8_dynamic.onnx")
        hasModel = vocabFile.exists() && modelFile.exists()
        org.junit.Assume.assumeTrue("模型缺失，跳过：" + dir.absolutePath, hasModel)
        if (!hasModel) return
        val json = JSONObject(vocabFile.readText())
        val map = when {
            json.has("model") -> json.getJSONObject("model").getJSONObject("vocab")
            json.has("vocab") -> json.getJSONObject("vocab")
            else -> json
        }
        val vocab = map.keys().asSequence().associateWith { map.getInt(it) }
        NativeOnnxEngine.initialize(context, modelFile.absolutePath)
        NativeOnnxEngine.initVocab(vocab)
    }

    @After
    fun tearDown() {
        if (hasModel) NativeOnnxEngine.release()
    }

    /** 生产路径：AssociationManager → IPC(:inference 进程) → 融合，验证候选里确实有双字词。 */
    @Test
    fun managerPredictReturnsWordCandidates() = runBlocking {
        org.junit.Assume.assumeTrue("模型缺失", hasModel)
        NativeOnnxEngine.release()
        val ok = AssociationManager.initialize(context)
        println("AssociationManager.initialize=" + ok)
        org.junit.Assume.assumeTrue("引擎未就绪", ok)
        val list = AssociationManager.predict("今天天气", 8)
        println("manager 候选=" + list.joinToString("|") { it.text })
        assertTrue("候选不应为空", list.isNotEmpty())
        assertTrue("应至少包含一个双字候选", list.any { it.text.length >= 2 })
        assertTrue(
            "联想栏不该出现纯标点候选",
            list.none { AssociationPostFilter.isPunctuationOnly(it.text) }
        )
        AssociationManager.release()
    }

    @Test
    fun expandProducesTwoCharCandidates() = runBlocking {
        var total = 0
        for (ctx in listOf("今天天气", "我想", "这个")) {
            val seeds = NativeOnnxEngine.predict(ctx, 8)
            val t0 = System.nanoTime()
            val phrases = AssociationExpander.expand(ctx, seeds) { prefix ->
                NativeOnnxEngine.predict(prefix, AssociationExpander.DEFAULT.continuationTopK)
            }
            println("  扩展耗时=" + (System.nanoTime() - t0) / 1_000_000 + "ms")
            println("ctx=" + ctx + " 首字=" + seeds.take(3).joinToString("|") { it.text })
            println("  扩展=" + phrases.joinToString("|") { it.text + "(" + it.score + ")" })
            assertTrue("首字不应为空", seeds.isNotEmpty())
            total += phrases.size
            for (p in phrases) {
                assertEquals("扩展结果必须是双字", 2, p.text.length)
                val seedScore = seeds.first { it.text == p.text.substring(0, 1) }.score
                assertTrue("词必须排在首字之后", p.score < seedScore)
            }
        }
        assertTrue("三个上下文里应至少扩出一个双字候选", total > 0)
    }
}
