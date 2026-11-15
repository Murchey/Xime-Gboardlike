package com.kingzcheung.xime.association

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * 联想耗时剖析：把「IPC 一次推理」和「本地推理 ×3（首字 + 两次续写）」分开计时，
 * 用来判断 100ms 级别延迟是花在模型上还是花在跨进程来回上。
 */
@RunWith(AndroidJUnit4::class)
class AssociationLatencyDeviceTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun modelDir(): File {
        val unified = File(context.filesDir, "models/predictive-text-small")
        return if (File(unified, "model_int8_dynamic.onnx").exists()) unified else context.filesDir
    }

    private fun ensureEngine(): Boolean {
        NativeOnnxEngine.loadNativeLibrary(context)
        val dir = modelDir()
        val vocabFile = File(dir, "vocab.json")
        val modelFile = File(dir, "model_int8_dynamic.onnx")
        if (!vocabFile.exists() || !modelFile.exists()) return false
        if (!NativeOnnxEngine.isInitialized()) {
            NativeOnnxEngine.initialize(context, modelFile.absolutePath)
            val json = JSONObject(vocabFile.readText())
            val map = when {
                json.has("model") -> json.getJSONObject("model").getJSONObject("vocab")
                json.has("vocab") -> json.getJSONObject("vocab")
                else -> json
            }
            NativeOnnxEngine.initVocab(map.keys().asSequence().associateWith { map.getInt(it) })
        }
        return true
    }

    /** 直接测「3 次推理」的耗时：静态 64 版每次都跑满 64 步，动态版只跑真实长度。 */
    private fun timeThreeInferences(modelPath: String): Long {
        NativeOnnxEngine.release()
        if (!NativeOnnxEngine.initialize(context, modelPath)) return -1
        val ctx = "今天天气"
        NativeOnnxEngine.predict(ctx, 8)
        var total = 0L
        repeat(5) {
            val t0 = System.nanoTime()
            val seeds = NativeOnnxEngine.predict(ctx, 8)
            seeds.take(2).forEach { NativeOnnxEngine.predict(ctx + it.text, 8) }
            total += (System.nanoTime() - t0) / 1_000_000
        }
        return total / 5
    }

    @Test
    fun modelShapeLatencyComparison() {
        NativeOnnxEngine.loadNativeLibrary(context)
        val dir = modelDir()
        val fixed = File(dir, "model_int8_dynamic.onnx")
        val probe = File(dir, "model_dyn_probe.onnx")
        assumeTrue("静态模型缺失", fixed.exists())
        val a = timeThreeInferences(fixed.absolutePath)
        println("静态64 模型：3 次推理 " + a + "ms（fixedSeqLen=" + NativeOnnxEngine.fixedSeqLen() + "）")
        if (probe.exists()) {
            val b = timeThreeInferences(probe.absolutePath)
            println("动态模型：3 次推理 " + b + "ms（fixedSeqLen=" + NativeOnnxEngine.fixedSeqLen() + "）")
        }
        NativeOnnxEngine.release()
        assertTrue("静态模型应可测", a > 0)
    }

    @Test
    fun latencyProfile() = runBlocking {
        assumeTrue("模型缺失", ensureEngine())

        val ctx = "今天天气"
        // 本地：一次首字 + 两次续写，等价于 expand 的额外推理量
        NativeOnnxEngine.predict(ctx, 8)
        var localTotal = 0L
        repeat(5) {
            val t0 = System.nanoTime()
            val seeds = NativeOnnxEngine.predict(ctx, 8)
            seeds.take(2).forEach { NativeOnnxEngine.predict(ctx + it.text, 8) }
            localTotal += (System.nanoTime() - t0) / 1_000_000
        }
        println("本地 3 次推理平均 = " + (localTotal / 5) + "ms")

        // 生产路径：AssociationManager（每次 predict 内含 1 + N 次 IPC 推理）
        val ok = AssociationManager.initialize(context)
        assumeTrue("引擎未就绪", ok)
        AssociationManager.predict(ctx, 8)
        var ipcTotal = 0L
        repeat(5) {
            val t0 = System.nanoTime()
            val list = AssociationManager.predict(ctx, 8)
            ipcTotal += (System.nanoTime() - t0) / 1_000_000
            assertTrue(list.isNotEmpty())
        }
        println("AssociationManager 平均 = " + (ipcTotal / 5) + "ms")
        AssociationManager.release()
    }
}