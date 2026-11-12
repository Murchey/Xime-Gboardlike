package com.kingzcheung.xime.glide.neural

import android.content.Context
import android.os.Build
import android.os.Process
import android.util.Log
import org.pytorch.executorch.EValue
import org.pytorch.executorch.Module
import org.pytorch.executorch.Tensor
import java.io.File

/**
 * FUTO 编码器的 Android 运行时封装。
 *
 * 使用官方 executorch-android AAR（只带 arm64-v8a / x86_64 原生库），
 * bool mask 由 [FutoBoolTensor] 传入。模型在 assets，首次使用时复制到
 * filesDir 再交给 Module 加载。
 *
 * 任何失败都只记录一次并返回 null，由上层回退几何解码；不允许影响输入。
 */
object FutoSwipeEncoder {

    private const val TAG = "FutoSwipeEncoder"
    private const val ASSET_MODEL = "futo-swipe/honorable_sturgeon/model_fp32.pte"
    private const val MODEL_VERSION = 1
    private const val MODEL_SIZE = 2649856L

    private val lock = Any()

    @Volatile private var module: Module? = null
    @Volatile private var failed = false
    @Volatile private var lastError: String = ""

    val isReady: Boolean get() = module != null
    val error: String get() = lastError

    fun ensureInitialized(context: Context): Boolean {
        module?.let { return true }
        if (failed) return false
        synchronized(lock) {
            module?.let { return true }
            if (failed) return false
            if (!supportedAbi()) {
                failed = true
                lastError = "unsupported abi " + Build.SUPPORTED_ABIS.joinToString(",")
                return false
            }
            return try {
                val model = copyModel(context)
                val loaded = Module.load(model.absolutePath)
                module = loaded
                true
            } catch (t: Throwable) {
                failed = true
                lastError = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "FUTO encoder unavailable: " + lastError)
                false
            }
        }
    }

    /** 供测试/诊断：直接对已预处理的输入跑一次编码器。 */
    fun encode(prepared: PreparedSwipe): FutoEmissions? {
        val active = module ?: return null
        synchronized(lock) {
            return try {
                val outputs = active.forward(
                    EValue.from(Tensor.fromBlob(prepared.features, longArrayOf(1, 2, 64))),
                    EValue.from(Tensor.fromBlob(prepared.keys, longArrayOf(1, 64, 2))),
                    EValue.from(FutoBoolTensor(prepared.mask, longArrayOf(1, 64))),
                )
                if (outputs.size < 3) return null
                // copy：输出张量的底层 buffer 归运行时所有，可能在下次 forward 时复用
                val logEmissions = outputs[0].toTensor().dataAsFloatArray.copyOf()
                val intention = outputs[2].toTensor().dataAsFloatArray.copyOf()
                FutoEmissions(logEmissions, intention, prepared.alphabet, prepared.slotCodes)
            } catch (t: Throwable) {
                lastError = t.message ?: t.javaClass.simpleName
                Log.w(TAG, "FUTO encode failed: " + lastError)
                null
            }
        }
    }

    private fun supportedAbi(): Boolean {
        if (!Process.is64Bit()) return false
        val abis = Build.SUPPORTED_ABIS.toSet()
        return "arm64-v8a" in abis || "x86_64" in abis
    }

    private fun copyModel(context: Context): File {
        val dir = File(context.filesDir, "futo-swipe")
        val target = File(dir, "model_fp32.pte")
        val marker = File(dir, "version.txt")
        val current = if (marker.exists()) marker.readText().trim() else ""
        if (target.length() == MODEL_SIZE && current == MODEL_VERSION.toString()) return target
        dir.mkdirs()
        context.assets.open(ASSET_MODEL).use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        }
        marker.writeText(MODEL_VERSION.toString())
        return target
    }
}
