package com.kingzcheung.xime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp

/** 材质着色使用"它所在的表面"的颜色，而不是无关页面的调色板。 */
@Immutable
internal data class MaterialPalette(val surface: Color, val accent: Color)

internal val LocalMaterialPalette = staticCompositionLocalOf<MaterialPalette?> { null }

/**
 * 只画选中的键面材质，不改主题配色与可点区域。
 * [bottomRadius] 与 [radius] 不同，仅用于"贴在一个卡片顶部的栏"（如键盘工具栏）。
 */
internal fun Modifier.materialSurface(
    style: VisualStyle,
    radius: Dp,
    level: MaterialLevel,
    bottomRadius: Dp = radius,
): Modifier {
    if (style == VisualStyle.ORIGINAL) return this
    return composed {
        val colors = MaterialTheme.colorScheme
        val palette = LocalMaterialPalette.current ?: MaterialPalette(colors.surface, colors.primary)
        val dark = palette.surface.luminance() < .5f
        val recipe = MaterialRecipes.resolve(style, level)
        val shadow = lerp(palette.accent, Color.Black, if (dark) .75f else .30f)
        val edge = when (style) {
            VisualStyle.GLASS -> if (dark) Color.White else palette.accent
            VisualStyle.NEON -> palette.accent
            else -> if (dark) Color.White else palette.accent
        }
        drawWithCache {
            val inset = .5f * density
            val bounds = Rect(inset, inset, size.width - inset, size.height - inset)
            val corner = CornerRadius(radius.toPx().coerceAtMost(size.minDimension / 2).coerceAtLeast(0f))
            val bottom = CornerRadius(bottomRadius.toPx().coerceAtMost(size.minDimension / 2).coerceAtLeast(0f))
            val path = Path().apply { addRoundRect(RoundRect(bounds, corner, corner, bottom, bottom)) }
            val topReach = (size.height * recipe.topReach).coerceAtLeast(1f)
            val topColor = if (dark) Color.White else palette.accent
            val top = Brush.verticalGradient(
                listOf(topColor.copy(alpha = recipe.top * if (dark) 1f else .55f), Color.Transparent),
                0f, topReach,
            )
            val depth = Brush.verticalGradient(
                listOf(Color.Transparent, shadow.copy(alpha = recipe.depth)),
                size.height * .60f, size.height.coerceAtLeast(1f),
            )
            val facet = Path().apply {
                moveTo(size.width * .82f, 0f)
                lineTo(size.width, 0f)
                lineTo(size.width, size.height * .28f)
                close()
            }
            val glassReflection = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) .10f else .35f), Color.Transparent,
                    palette.accent.copy(alpha = .035f), Color.White.copy(alpha = .06f),
                ),
                Offset.Zero, Offset(size.width, size.height),
            )
            val glassRim = Brush.linearGradient(
                listOf(
                    Color.White.copy(alpha = if (dark) .42f else .85f),
                    edge.copy(alpha = .06f), edge.copy(alpha = .18f), Color.White.copy(alpha = .24f),
                ),
                Offset.Zero, Offset(size.width, size.height),
            )
            onDrawWithContent {
                if (bounds.width > 0 && bounds.height > 0) clipPath(path) {
                    if (recipe.shade > 0f) drawPath(path, Color.Black.copy(alpha = recipe.shade * if (dark) 1f else .35f))
                    if (recipe.tint > 0f) drawPath(path, palette.accent.copy(alpha = recipe.tint * if (dark) 1f else .7f))
                    if (recipe.matte > 0f) drawPath(path, (if (dark) Color.White else Color.Black).copy(alpha = recipe.matte * if (dark) 1f else .35f))
                    if (recipe.top > 0f) drawRect(top, size = Size(size.width, topReach))
                    if (recipe.depth > 0f) drawRect(depth, topLeft = Offset(0f, size.height * .60f), size = Size(size.width, size.height * .40f))
                    if (recipe.facet > 0f) drawPath(facet, palette.accent.copy(alpha = recipe.facet * if (dark) 1f else .7f))
                    if (recipe.glow > 0) drawPath(path, palette.accent.copy(alpha = recipe.glow), style = Stroke(3f * density))
                    if (style == VisualStyle.GLASS) {
                        drawPath(path, glassReflection)
                        drawPath(path, glassRim, style = Stroke(density))
                    } else if (recipe.border > 0f) {
                        drawPath(path, edge.copy(alpha = recipe.border), style = Stroke(density))
                    }
                }
                drawContent()
            }
        }
    }
}
