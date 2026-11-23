package com.kingzcheung.xime.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * 键盘顶部样式（固定键盘贴屏幕顶部的那一条边）。
 *
 * - [ROUNDED]：上沿两角圆角；
 * - [SQUARE]：直角（默认，保持原行为）；
 * - [SOFT]：上沿渐变过渡（背景图/渐变到顶部淡出）。
 */
enum class KeyboardTopStyle(val id: String, val title: String) {
    ROUNDED("rounded", "圆角"),
    SQUARE("square", "直角"),
    SOFT("soft", "柔化");

    /** 顶部裁剪形状：圆角档裁上两角，其它档不裁。 */
    fun clipShape(radius: Dp = TOP_ROUND_RADIUS): Shape =
        if (this == ROUNDED) RoundedCornerShape(topStart = radius, topEnd = radius) else SQUARE_SHAPE

    companion object {
        fun of(id: String?): KeyboardTopStyle = entries.firstOrNull { it.id == id } ?: ROUNDED
    }
}

/** 键盘整体边缘形状：上沿按 [top]，下沿按 [roundedBottom]（底部圆角 + 透明导航栏）。 */
fun keyboardEdgeShape(top: KeyboardTopStyle, roundedBottom: Boolean, radius: Dp = TOP_ROUND_RADIUS): Shape {
    val topRadius = if (top == KeyboardTopStyle.ROUNDED) radius else 0.dp
    val bottomRadius = if (roundedBottom) radius else 0.dp
    return RoundedCornerShape(
        topStart = topRadius,
        topEnd = topRadius,
        bottomEnd = bottomRadius,
        bottomStart = bottomRadius,
    )
}

/** 键盘上沿圆角半径（用户反馈原 14dp 偏小，加大到 24dp）。 */
private val TOP_ROUND_RADIUS = 24.dp
private val SQUARE_SHAPE = RoundedCornerShape(0.dp)

/** 当前生效的键盘顶部样式（Compose 状态）。 */
object KeyboardTopStyles {
    var current by mutableStateOf(KeyboardTopStyle.ROUNDED)
        internal set
}

/**
 * 上沿柔化：在背景之上叠一层从"键盘底色"到透明的竖向渐变，让上沿不像一条硬边。
 * 只在 [KeyboardTopStyle.SOFT] 下生效；[color] 用作渐变起始色（与键盘底色一致）。
 */
fun Modifier.keyboardTopSoftEdge(color: Color, height: Dp = 16.dp): Modifier =
    if (KeyboardTopStyles.current != KeyboardTopStyle.SOFT) this
    else drawWithContent {
        drawContent()
        // 与参考实现同义：用 DstIn 把上沿这段按竖向渐变"擦掉"，底图/渐变/纯色都直接渐隐到透明，
        // 不再叠一块兜底色（图片主题下那样会盖住真实底图）。
        val h = height.toPx().coerceAtMost(size.height * 0.5f)
        if (h > 0f) {
            drawRect(
                brush = Brush.verticalGradient(
                    listOf(Color.Transparent, Color.Black),
                    0f,
                    h,
                ),
                topLeft = Offset.Zero,
                size = androidx.compose.ui.geometry.Size(size.width, h),
                blendMode = androidx.compose.ui.graphics.BlendMode.DstIn,
            )
        }
    }
