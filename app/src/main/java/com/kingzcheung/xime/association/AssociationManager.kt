package com.kingzcheung.xime.association

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.content.Context
import android.util.Log
import com.kingzcheung.xime.model.ModelRuntime
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

object AssociationManager {
    private const val TAG = "AssociationManager"
    
    @Volatile
    private var isInitialized = false

    /** 「没装联想模型」只记一次日志：预测每次刷新都会走到这里，不能刷屏。 */
    @Volatile
    private var missingModelLogged = false

    private val mutex = Mutex()
    
    private lateinit var fusionEngine: NgramFusionEngine
    private var context: Context? = null
    
    suspend fun initialize(ctx: Context): Boolean = withContext(Dispatchers.IO) {
        context = ctx
        if (isInitialized) {
            FileLogger.d(TAG, "Already initialized")
            return@withContext true
        }
        
        mutex.withLock {
            if (isInitialized) {
                FileLogger.d(TAG, "Already initialized (in lock)")
                return@withContext true
            }
            
            try {
                fusionEngine = NgramFusionEngine(ctx)
                
                ModelRuntime.register(
                    id = "predictive_text",
                    loader = { OnnxAssociationEngine.initialize(ctx) },
                    releaser = { OnnxAssociationEngine.release() },
                    label = tr(R.string.s_zhinenglianxiangmoxing)
                )
                val modelLoaded = ModelRuntime.load("predictive_text")
                FileLogger.i(TAG, "ModelRuntime.load(predictive_text) result: $modelLoaded")

                if (modelLoaded) {
                    ModelRuntime.keepWarm("predictive_text")
                    OnnxAssociationEngine.startWarmup()
                }

                val cacheInit = fusionEngine.initialize()
                FileLogger.i(TAG, "NgramFusionEngine init result: $cacheInit")
                
                isInitialized = modelLoaded
                
                FileLogger.i(TAG, "AssociationManager initialized: model=$modelLoaded, cache=$cacheInit")
                isInitialized
                
            } catch (e: Exception) {
                FileLogger.e(TAG, "Failed to initialize AssociationManager: ${e.message}", e)
                false
            }
        }
    }
    
    /** 当前选中的联想模型两个必需文件是否都在（只做 stat，不加载）。 */
    private fun hasModelFiles(ctx: Context): Boolean {
        val modelDir = ModelStorage.getModelDir(ctx, SettingsPreferences.getPredictionSelectedModel(ctx))
        return File(modelDir, "vocab.json").isFile && File(modelDir, "model_int8_dynamic.onnx").isFile
    }

    suspend fun predict(contextText: String, topK: Int = 20): List<AssociationCandidate> = withContext(Dispatchers.Default) {
        if (!isInitialized) {
            val ctx = context
            if (ctx != null) {
                // 联想模型已不再随包：没装模型时若每次联想都走一遍初始化，
                // 会白发一次 NgramFusionEngine 的加载。这里先 stat，直接空返回。
                if (!hasModelFiles(ctx)) {
                    if (!missingModelLogged) {
                        missingModelLogged = true
                        FileLogger.w(TAG, "联想模型未安装，跳过初始化（在 设置 → 智能 → 智能联想 里下载或导入）")
                    }
                    return@withContext emptyList()
                }
                missingModelLogged = false
                val initSuccess = withContext(Dispatchers.IO) {
                    initialize(ctx)
                }
                if (!initSuccess) {
                    Log.e(TAG, "Initialization failed, returning empty list")
                    return@withContext emptyList()
                }
            } else {
                Log.e(TAG, "Context is null, cannot initialize")
                return@withContext emptyList()
            }
        }
        
        // :inference 进程被系统回收（空闲回收是设计内行为，同 AsrInferenceService 的
        // 空闲释放）后，OnnxAssociationEngine 检测到失联会重置自身状态，而本类的
        // isInitialized 仍为 true。必须在这里检测引擎状态并按需重载（重新 bind +
        // loadModel），否则所有预测入口都会被本方法上方的 isInitialized 守卫放行、
        // 撞上引擎的 "Engine not initialized" 空转，联想永远失效。
        if (!OnnxAssociationEngine.isInitialized()) {
            val reloaded = ModelRuntime.load("predictive_text")
            if (!reloaded) {
                FileLogger.e(TAG, "Reload predictive_text failed after service reclaim")
                return@withContext emptyList()
            }
        }

        try {
            val started = android.os.SystemClock.elapsedRealtime()
            val modelCandidates = OnnxAssociationEngine.predict(contextText, topK * 2)

            if (modelCandidates.isEmpty()) {
                return@withContext emptyList()
            }

            // 续写词已由推理进程在同一次 IPC 里算好（「不」→「不好」），这里只做融合与后置过滤。
            val fusedCandidates = AssociationPostFilter.groupPhrasesWithSeeds(
                AssociationPostFilter.dropPunctuation(
                    fusionEngine.fuseCandidates(modelCandidates, contextText)
                )
            )

            val cost = android.os.SystemClock.elapsedRealtime() - started
            val phrases = modelCandidates.count { it.text.length > 1 }
            FileLogger.d(
                TAG,
                "predict: seeds=" + (modelCandidates.size - phrases) +
                    " phrases=" + phrases +
                    " fused=" + fusedCandidates.size +
                    " cost=" + cost + "ms"
            )
            fusedCandidates.take(topK)
            
        } catch (e: Exception) {
            Log.e(TAG, "Prediction failed", e)
            emptyList()
        }
    }
    
    fun recordInput(text: String) {
        if (!isInitialized) return
        fusionEngine.recordUserInput(text)
    }
    
    suspend fun saveUserData() {
        if (!isInitialized) return
        fusionEngine.saveCache()
    }
    
    fun getCacheSize(): Int {
        return if (isInitialized) fusionEngine.getCacheSize() else 0
    }

    /** 学习数据条目（「学习数据」页展示用；未初始化时返回空表，调用方回退读文件）。 */
    fun getUserNgramEntries(): List<UserNgramCache.NgramEntry> =
        if (isInitialized) fusionEngine.getUserNgramEntries() else emptyList()

    /** 删除学习数据条目并落盘：作用于当前进程的内存缓存，然后立即写回 JSON。 */
    suspend fun removeUserNgramEntries(keys: Set<String>): Int = withContext(Dispatchers.IO) {
        if (!isInitialized) return@withContext 0
        val removed = fusionEngine.removeUserNgramEntries(keys)
        if (removed > 0) fusionEngine.saveCache()
        removed
    }
    
    fun isInitialized(): Boolean = isInitialized
    
    fun release() {
        if (isInitialized) {
            ModelRuntime.unload("predictive_text")
            isInitialized = false
            context = null
        }
    }
}