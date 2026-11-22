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
import androidx.compose.foundation.border
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import com.kingzcheung.xime.settings.FrostedGlassConfig
import com.kingzcheung.xime.settings.FrostedGlassPreferences
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
internal fun Modifier.keyFaceBackground(color: Color, shape: Shape, textColor: Color? = null): Modifier {
    // 键面颜色走玻璃材质算法：玻璃开时键帽只做半透明（不透明度/提亮规则见 FrostedKeyColors））；
    // 液态玻璃代替视觉样式材质，与实现一致只叠一层。
    val radius = LocalKeyCornerRadius.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val isDarkSurface = MaterialTheme.colorScheme.surface.luminance() < .5f
    val config = if (GlassEffect.enabled) {
        FrostedGlassPreferences.read(context, isDarkSurface).copy(enabled = true)
    } else {
        FrostedGlassConfig(enabled = false)
    }
    // 没传文字色时按键面明暗取一个对比色：只影响玻璃提亮的"深浅面"判断与按下反馈
    val foreground = textColor ?: if (color.luminance() > .5f) Color.Black else Color.White
    val faceColor = frostedKeyColor(color, foreground, config)
    val materialStyle = VisualStyle.forKeyboard(VisualStyles.current)
    val overlay = if (config.enabled && LiquidGlass.enabled) {
        Modifier.liquidGlassCap(radius)
    } else {
        Modifier.visualMaterial(materialStyle, radius, level = MaterialLevel.BASE)
    }.then(
        // 玻璃键在白底上容易与背景糊在一起：补一道很淡的描边保证键位边界
        if (config.enabled) {
            Modifier.border(
                width = 1.dp,
                color = if (isDarkSurface) Color.White.copy(alpha = .18f) else Color.Black.copy(alpha = .10f),
                shape = shape,
            )
        } else {
            Modifier
        }
    )
    if (FrostedBackdropHolder.image == null) {
        return this
            .clip(shape)
            .background(faceColor)
            .then(overlay)
    }

    val position = remember { KeyFacePosition() }
    var laidOut by remember { mutableStateOf(false) }
    val glassColor = faceColor
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
                // 液态玻璃的图片折射（安卓 13+）：键帽外圈按底图弯折，保留轻微色散
                if (config.enabled && LiquidGlass.enabled &&
                    android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU &&
                    com.kingzcheung.xime.ui.theme.LiquidRefraction.available()
                ) {
                    val ppuX = image.width / size.width.toFloat()
                    val ppuY = image.height / size.height.toFloat()
                    com.kingzcheung.xime.ui.theme.LiquidRefraction.run {
                        drawRefraction(
                            image = image,
                            keyLeft = origin.x,
                            keyTop = origin.y,
                            radiusPx = radius.toPx(),
                            strength = LiquidGlass.strength,
                            imageLeft = (origin.x - root.x) * ppuX,
                            imageTop = (origin.y - root.y) * ppuY,
                            pixelsPerX = ppuX,
                            pixelsPerY = ppuY,
                        )
                    }
                }
            }
            drawRect(glassColor)
        }
        .then(overlay)
}
