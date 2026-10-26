package com.kingzcheung.xime.model

import com.kingzcheung.xime.speech.models.AsrModelRegistry

/**
 * 可离线导入的内置模型清单。
 *
 * 远程模型索引不可用时仍提供这些目标，避免本地导入入口因网络状态为空。
 */
object ModelImportCatalog {
    val models: List<ModelInfo> = buildList {
        AsrModelRegistry.profiles.forEach { profile ->
            add(
                ModelInfo(
                    id = profile.id,
                    name = profile.name,
                    description = profile.description,
                    category = ModelCategory.ASR,
                    size = profile.size,
                    versions = listOf(
                        ModelVersion(
                            files = listOf(
                                ModelFile(profile.encoderFile, profile.downloadUrl),
                                ModelFile(profile.decoderFile, profile.downloadUrl),
                                ModelFile(profile.joinerFile, profile.downloadUrl),
                                ModelFile(profile.tokensFile, profile.downloadUrl),
                            ),
                            archiveUrl = profile.downloadUrl,
                        ),
                    ),
                ),
            )
        }
        add(
            ModelInfo(
                id = "ochwpro",
                name = "中文手写识别",
                description = "离线手写识别模型",
                category = ModelCategory.HANDWRITING,
                versions = listOf(
                    ModelVersion(
                        files = listOf(
                            ModelFile("ochwpro.onnx", ""),
                            ModelFile("char_index.json", ""),
                        ),
                    ),
                ),
            ),
        )
    }

    fun merge(remote: List<ModelInfo>): List<ModelInfo> {
        val remoteIds = remote.mapTo(HashSet()) { it.id }
        return remote + models.filterNot { it.id in remoteIds }
    }
}
