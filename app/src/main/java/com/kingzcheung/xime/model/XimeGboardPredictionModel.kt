package com.kingzcheung.xime.model

/**
 * 随 App 分发的自训练联想模型（单独标识，不与上游 small/base 混用）。
 *
 * 以前内置模型直接沿用上游 \`predictive-text-small\` 的 id，导致它在模型列表里
 * 被当成上游那一档，用户分不清哪个是 App 自带的、哪个是上游的。现在独立成一套 id，
 * 上游 small/base 仍保留为可导入项。
 */
object XimeGboardPredictionModel {
    /** 内置联想模型的模型 id。 */
    const val ID = "ximegboard-prediction"

    /** 模型列表里的展示名。 */
    const val NAME = "XimeGboard 内置联想模型"

    /** 一句话说明（列表副标题）。 */
    const val DESCRIPTION = "随 App 分发的自训练模型（v5，5000 字表 + 静态 int8）"

    /** 历史 id：内置模型曾与上游 small 共用。 */
    const val LEGACY_ID = "predictive-text-small"
}
