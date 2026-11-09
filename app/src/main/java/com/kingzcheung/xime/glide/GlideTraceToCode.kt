package com.kingzcheung.xime.glide

/**
 * 阶段B最小可跑链路：把轨迹经过的合法键序列转换成可查询编码串。
 *
 * 计划书阶段C会替换为真正的"轨迹→拼写格→整句 beam"；本对象接口保持
 * (features, geometry) -> String，方便无缝替换。
 *
 * 当前规则（保守，避免误把回摆/抖动变成多个字符）：
 * - 只保留不同物理键/编码的转移；连续同一键不重复计数；
 * - 起点必录，终点必录，中间按键转移必录；
 * - 转角/驻留后续阶段用于分词，这里仅保留编码序列。
 */
object GlideTraceToCode {

    fun toRawInput(points: List<GlidePoint>, geometry: GlideGeometrySnapshot): String {
        if (points.isEmpty() || !geometry.isValid) return ""
        val features = GlideTraceSampler.extractFeatures(points, geometry, Float.MAX_VALUE)
        val codes = mutableListOf<String>()
        for (f in features) {
            val code = f.code ?: continue
            if (codes.isEmpty() || codes.last() != code) codes += code
        }
        // 终点键若与最后一个不同，补录，保证 ing 等尾韵母不丢
        val last = points.last()
        geometry.keyAt(last.x, last.y)?.code?.let { endCode ->
            if (codes.isEmpty() || codes.last() != endCode) codes += endCode
        }
        return codes.joinToString("")
    }
}
