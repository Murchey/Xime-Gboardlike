package com.kingzcheung.xime.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown

/**
 * 液态玻璃（主题与定制 › 材质效果；默认关，开启时与「玻璃效果」一起用）。
 *
 * 键盘读不到背后应用的像素，所以这里做保守版：给按键画一道朝光的细亮边，
 * 按住时亮边变亮、键面略亮；高光路径按尺寸缓存，键盘不动不重画。
 * 图片背景的真实折射（AGSL 着色器）暂未接入——需要键盘背景的帧/窗口坐标，
 * 当前只保留强度里的折射参数。
 */
object LiquidGlass {
    var enabled by mutableStateOf(false)
        internal set
    var strength by mutableStateOf(LiquidGlassStrength.STANDARD)
        internal set
}

/**
 * 键帽上的液态玻璃亮边/厚度/高光：画在底色与材质之上、文字之下。
 * 只观察指针（不消费事件），按住时亮边变亮。
 */
/** 面板件上的同一道细亮边（不含厚度/高光），与参考实现的 liquidGlassEdge 同义。 */
fun Modifier.liquidGlassEdge(radius: Dp, alpha: () -> Float = { 1f }): Modifier = composed {
    if (!LiquidGlass.enabled || !GlassEffect.enabled) return@composed this
    val dark = MaterialTheme.colorScheme.surface.luminance() < .5f
    drawWithCache {
        val rim = liquidRimGeometry(radius, dark, LiquidGlassStrength.STANDARD, body = false)
        onDrawWithContent {
            val shown = alpha().coerceIn(0f, 1f)
            if (rim.visible && shown > 0f) clipPath(rim.path) { drawLiquidRim(rim, alpha = shown) }
            drawContent()
        }
    }
}

fun Modifier.liquidGlassCap(radius: Dp): Modifier = composed {
    if (!LiquidGlass.enabled) return@composed this
    val paletteSurface = MaterialTheme.colorScheme.surface
    val dark = paletteSurface.luminance() < .5f
    val strength = LiquidGlass.strength
    val held = remember { mutableStateOf(false) }
    val observed = pointerInput(Unit) {
        awaitEachGesture {
            // 只旁观，不消费：不影响点击、滑动和长按。
            awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
            held.value = true
            do {
                val event = awaitPointerEvent(PointerEventPass.Initial)
            } while (event.changes.any { it.pressed })
            held.value = false
        }
    }
    observed.then(
        drawWithCache {
            val rim = liquidRimGeometry(radius, dark, strength)
            val heldFill = Color.White.copy(alpha = if (dark) .04f else .08f)
            onDrawWithContent {
                val down = held.value
                if (rim.visible) {
                    clipPath(rim.path) {
                        if (down) drawPath(rim.path, heldFill)
                        drawLiquidRim(rim, held = down)
                    }
                }
                drawContent()
            }
        }
    )
}
