package com.kingzcheung.xime.ui.theme

/** 材质层级：同一套配方在基础/浮起/悬浮三种高度上的强度。 */
enum class MaterialLevel { BASE, RAISED, FLOATING }

/**
 * 一种材质的绘制参数：压暗、着色、顶部高光、顶部高光范围、描边、底部厚度、
 * 折面、哑光、发光。
 */
data class SurfaceOptics(
    val shade: Float,
    val tint: Float,
    val top: Float,
    val topReach: Float,
    val border: Float,
    val depth: Float,
    val facet: Float,
    val matte: Float,
    val glow: Float,
)

internal object MaterialRecipes {
    fun resolve(style: VisualStyle, level: MaterialLevel): SurfaceOptics {
        val material = when (style) {
            VisualStyle.NEON -> SurfaceOptics(0.11f, 0.025f, 0.0f, 0.1f, 0.36f, 0.02f, 0.0f, 0.0f, 0.07f)
            VisualStyle.GLASS -> SurfaceOptics(0.0f, 0.05f, 0.2f, 0.38f, 0.2f, 0.1f, 0.0f, 0.0f, 0.0f)
            VisualStyle.FACET -> SurfaceOptics(0.025f, 0.02f, 0.03f, 0.15f, 0.07f, 0.12f, 0.1f, 0.0f, 0.0f)
            VisualStyle.FROST -> SurfaceOptics(0.0f, 0.0f, 0.08f, 0.85f, 0.025f, 0.0f, 0.0f, 0.12f, 0.0f)
            VisualStyle.ORIGINAL -> SurfaceOptics(0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f, 0.0f)
        }
        val intensity = when (level) {
            MaterialLevel.BASE -> 1.0f
            MaterialLevel.RAISED -> 1.15f
            MaterialLevel.FLOATING -> 1.25f
        }
        return material.copy(
            shade = material.shade * intensity,
            tint = material.tint * intensity,
            top = material.top * intensity,
            border = material.border * intensity,
            depth = material.depth * intensity,
            facet = material.facet * intensity,
            matte = material.matte * intensity,
            glow = if (style == VisualStyle.NEON && level == MaterialLevel.FLOATING) material.glow * intensity else 0f,
        )
    }
}
