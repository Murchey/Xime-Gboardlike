package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp

import kotlin.math.roundToInt

private val DRAG_BAR_HEIGHT_DP = FloatingCardGeometry.DRAG_BAR_HEIGHT_DP

/**
 * 悬浮键盘卡片容器：卡片宽 = [FloatingCardGeometry.widthFraction] × 窗口宽，
 * 高 = 父容器（服务层内容 Box）高，位置由 offsetX/offsetY 控制
 * （offsetY = 卡片底边离窗口底边的距离）。
 *
 * [onDrag] 上报**原始屏幕位移**（dp，+x 向右、+y 向下），不做任何方向变换，
 * 位置语义（offsetY 与屏幕 y 相反）由回调侧（ImeKeyboardCallbacks）决定——
 * 与 KeyboardResizeOverlay 的手势约定一致。
 *
 * 卡片实测矩形（窗口坐标）经 [onCardPositioned] 回传，是触摸区与拖动钳制的唯一真源。
 */
@Composable
fun FloatingKeyboardContainer(
    isFloatingMode: Boolean,
    scaleFactor: Float,
    fontScaleFactor: Float = scaleFactor,
    cardHeightDp: Int,
    offsetX: Int,
    offsetY: Int,
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onRestore: () -> Unit,
    onAdjust: () -> Unit,
    onCardPositioned: (left: Int, top: Int, right: Int, bottom: Int) -> Unit = { _: Int, _: Int, _: Int, _: Int -> },
    keyboardContent: @Composable () -> Unit,
) {
    if (!isFloatingMode) {
        keyboardContent()
        return
    }

    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomCenter
    ) {
        val cardTotalHeight = cardHeightDp.dp.coerceAtMost(maxHeight)
        Box(
            modifier = Modifier
                .fillMaxWidth(scaleFactor)
                .height(cardTotalHeight)
                .offset(x = offsetX.dp, y = (-offsetY).dp)
                .onGloballyPositioned { coords ->
                    val pos = coords.positionInWindow()
                    val size = coords.size
                    onCardPositioned(
                        pos.x.roundToInt(),
                        pos.y.roundToInt(),
                        (pos.x + size.width).roundToInt(),
                        (pos.y + size.height).roundToInt()
                    )
                }
        ) {
            Column {
                Box(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .shadow(12.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                ) {
                    CompositionLocalProvider(
                        LocalDensity provides Density(density = density.density, fontScale = density.fontScale * fontScaleFactor)
                    ) {
                        keyboardContent()
                    }
                }
                DragBar(
                    onDrag = { change, dragAmount ->
                        change.consume()
                        val dxDp = with(density) { dragAmount.x.toDp().value }
                        val dyDp = with(density) { dragAmount.y.toDp().value }
                        onDrag(dxDp, dyDp)
                    },
                    onDragEnd = onDragEnd,
                    onRestore = onRestore,
                    onAdjust = onAdjust
                )
            }
        }
    }
}

@Composable
private fun DragBar(
    onDrag: (change: androidx.compose.ui.input.pointer.PointerInputChange, dragAmount: androidx.compose.ui.geometry.Offset) -> Unit,
    onDragEnd: () -> Unit,
    onRestore: () -> Unit,
    onAdjust: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(DRAG_BAR_HEIGHT_DP.dp),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FloatingControlLabel(
            label = tr(R.string.s_huifu),
            onClick = onRestore,
            modifier = Modifier.weight(1f),
        )
        Box(
            modifier = Modifier
                .size(48.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(24.dp))
                .pointerInput(Unit) {
                    detectDragGestures(
                        onDrag = onDrag,
                        onDragEnd = onDragEnd,
                        onDragCancel = onDragEnd,
                    )
                },
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Default.OpenWith,
                contentDescription = tr(R.string.s_xuanfumoshi),
                tint = Color.White,
                modifier = Modifier.size(24.dp),
            )
        }
        FloatingControlLabel(
            label = tr(R.string.s_tiaojie),
            onClick = onAdjust,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun FloatingControlLabel(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .height(48.dp)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .background(Color(0xFF292B2F), RoundedCornerShape(24.dp))
                .padding(horizontal = 16.dp, vertical = 8.dp),
            maxLines = 1,
        )
    }
}
