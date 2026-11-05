package com.kingzcheung.xime.settings

import android.content.Context
import com.kingzcheung.xime.model.ModelCategory
import com.kingzcheung.xime.model.ModelFile
import com.kingzcheung.xime.model.ModelInfo
import com.kingzcheung.xime.model.ModelVersion
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * 用户自建的预测模型。
 *
 * 与远程索引模型不同，自定义模型完全由用户描述：id、显示名、标签（tag）、
 * 模型目录。导入后出现在「智能联想 → 选择模型」列表，可被选中使用。
 */
data class CustomPredictionModel(
    val id: String,
    val name: String,
    val tag: String,
    val description: String = "",
    val createdAtMs: Long = System.currentTimeMillis(),
) {
    /** 转成统一模型描述，供模型管理/联想页复用同一条渲染链路。 */
    fun toModelInfo(): ModelInfo = ModelInfo(
        id = id,
        name = name,
        description = description.ifBlank { tag },
        category = ModelCategory.PREDICTION,
        versions = listOf(
            ModelVersion(
                version = "custom",
                files = listOf(
                    ModelFile("vocab.json", ""),
                    ModelFile("model_int8_dynamic.onnx", ""),
                ),
            ),
        ),
    )
}

object CustomPredictionModelStore {

    private const val FILE_NAME = "custom_prediction_models.json"
    private const val FORMAT = "xime-custom-prediction-models"
    private const val VERSION = 1

    private fun file(context: Context): File = File(context.filesDir, FILE_NAME)

    /** 生成的模型 id：custom_<8 位随机>，避免与远程索引 id 冲突。 */
    fun newId(): String =
        "custom_" + java.util.UUID.randomUUID().toString().replace("-", "").take(10)

    fun load(context: Context): List<CustomPredictionModel> {
        val f = file(context)
        if (!f.isFile) return emptyList()
        return runCatching {
            val root = JSONObject(f.readText())
            val arr = root.optJSONArray("models") ?: JSONArray()
            (0 until arr.length()).mapNotNull { i ->
                val o = arr.optJSONObject(i) ?: return@mapNotNull null
                val id = o.optString("id", "").ifBlank { return@mapNotNull null }
                CustomPredictionModel(
                    id = id,
                    name = o.optString("name", id),
                    tag = o.optString("tag", "自定义"),
                    description = o.optString("description", ""),
                    createdAtMs = o.optLong("created_at_ms", 0L),
                )
            }
        }.getOrDefault(emptyList())
    }

    fun saveAll(context: Context, models: List<CustomPredictionModel>) {
        val root = JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("models", JSONArray().apply {
                models.forEach { m ->
                    put(JSONObject().apply {
                        put("id", m.id)
                        put("name", m.name)
                        put("tag", m.tag)
                        put("description", m.description)
                        put("created_at_ms", m.createdAtMs)
                    })
                }
            })
        }
        file(context).writeText(root.toString(2))
    }

    fun upsert(context: Context, model: CustomPredictionModel): List<CustomPredictionModel> {
        val current = load(context).toMutableList()
        val index = current.indexOfFirst { it.id == model.id }
        if (index >= 0) current[index] = model else current.add(model)
        saveAll(context, current)
        return current
    }

    fun delete(context: Context, id: String): List<CustomPredictionModel> {
        val current = load(context).filterNot { it.id == id }
        saveAll(context, current)
        return current
    }

    /** 自定义模型转为 ModelInfo 列表，供列表与联想页合并展示。 */
    fun asModelInfos(context: Context): List<ModelInfo> =
        load(context).map { it.toModelInfo() }
}
