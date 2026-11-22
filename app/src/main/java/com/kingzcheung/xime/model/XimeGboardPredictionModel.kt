package com.kingzcheung.xime.model

/**
 * 本分支自训练的联想模型（单独标识，不与上游 small/base 混用）。
 *
 * 模型权重**不再随 APK 分发**：装好后需在 设置 → 智能 → 智能联想 里下载或导入。
 * id 保持不变，已装过该模型的用户升级后仍可直接用。
 * 独立 id 的原因：以前它沿用上游 predictive-text-small 的 id，在模型列表里被当成上游那一档。
 */
object XimeGboardPredictionModel {
    /** 本分支联想模型的模型 id。 */
    const val ID = "ximegboard-prediction"

    /** 模型列表里的展示名。 */
    const val NAME = "XimeGboard 自训联想模型"

    /** 一句话说明（列表副标题）。 */
    const val DESCRIPTION = "自训练模型（v5，5000 字表 + 静态 int8），不再随包，按需下载或导入"

    /** 历史 id：内置模型曾与上游 small 共用。 */
    const val LEGACY_ID = "predictive-text-small"
}
