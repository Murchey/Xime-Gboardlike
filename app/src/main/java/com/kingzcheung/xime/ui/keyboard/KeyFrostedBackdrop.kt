package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.ui.theme.GlassEffect
import com.kingzcheung.xime.ui.theme.LiquidGlass
import com.kingzcheung.xime.ui.theme.liquidGlassCap
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.VisualStyle
import com.kingzcheung.xime.ui.theme.VisualStyles
import com.kingzcheung.xime.ui.theme.visualMaterial
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 当前生效的「键帽毛玻璃」底图：与键盘内容区同尺寸，已按底图填充/平移规则渲染并模糊。
 *
 * 用进程内状态而不是 CompositionLocal：键帽由多个页面/布局在不同层级绘制，逐层提供会牵动
 * 整棵组合树；同一时刻只有一块键盘（或一处预览）在显示，由显示方写入、键帽读取即可。
 *
 * [image]/[size] 是 Compose 状态（切换开关或改尺寸时重绘）；[rootOrigin] 是普通字段，
 * 只在布局回调里更新——键盘整体拖动时它与键帽位置同步平移，差值不变，不必触发重绘。
 */
internal object FrostedBackdropHolder {
    var image: ImageBitmap? by mutableStateOf(null)
    var size: IntSize by mutableStateOf(IntSize.Zero)

    /** 键盘内容区在窗口 root 坐标里的左上角。 */
    var rootOrigin: Offset = Offset.Zero

    fun clear() {
        image = null
        size = IntSize.Zero
        rootOrigin = Offset.Zero
    }
}

/** 键帽自己记住的位置（普通字段，避免拖动时每帧重组）。 */
private class KeyFacePosition {
    var topLeft: Offset = Offset.Unspecified
}

/**
 * 键帽底色。
 *
 * - 未开启毛玻璃：等价于 `clip(shape).background(color)`；
 * - 开启后：先在键帽形状内画「模糊底图」的对应区域，再把底色压上去。
 *   底色 alpha 自动收窄到 0.65，否则不透明键帽会把磨砂层完全盖住。
 */
@Composable
internal fun Modifier.keyFaceBackground(color: Color, shape: Shape): Modifier {
    // 键面材质与玻璃效果：玻璃开时压到 62% 不透明（键面半透明），并画玻璃高光/亮边
    val radius = LocalKeyCornerRadius.current
    val glass = GlassEffect.enabled
    val materialStyle = if (glass) VisualStyle.GLASS else VisualStyle.forKeyboard(VisualStyles.current)
    val faceColor = if (glass) color.copy(alpha = min(color.alpha, 0.62f)) else color

    // 液态玻璃：在底色/材质之上再补一道亮边（按住变亮），文字仍然在最上面
    val liquid = if (glass && LiquidGlass.enabled) Modifier.liquidGlassCap(radius) else Modifier
    if (FrostedBackdropHolder.image == null) {
        return this
            .clip(shape)
            .background(faceColor)
            .visualMaterial(materialStyle, radius, level = MaterialLevel.BASE)
            .then(liquid)
    }

    val position = remember { KeyFacePosition() }
    var laidOut by remember { mutableStateOf(false) }
    val glassColor = if (glass) faceColor.copy(alpha = min(faceColor.alpha, 0.62f)) else faceColor.copy(alpha = min(faceColor.alpha, 0.65f))
    return this
        .onGloballyPositioned { coordinates ->
            position.topLeft = coordinates.positionInRoot()
            if (!laidOut) laidOut = true
        }
        .clip(shape)
        .drawBehind {
            // 首次布局后补一次重绘（此前还不知道自己在哪，只能先画纯色）
            laidOut
            val image = FrostedBackdropHolder.image
            val size = FrostedBackdropHolder.size
            val origin = position.topLeft
            if (image != null && size.width > 0 && origin != Offset.Unspecified) {
                val root = FrostedBackdropHolder.rootOrigin
                drawImage(
                    image = image,
                    dstOffset = IntOffset(
                        -(origin.x - root.x).roundToInt(),
                        -(origin.y - root.y).roundToInt(),
                    ),
                    dstSize = size,
                )
            }
            drawRect(glassColor)
        }
        .visualMaterial(materialStyle, radius, level = MaterialLevel.BASE)
        .then(liquid)
}
