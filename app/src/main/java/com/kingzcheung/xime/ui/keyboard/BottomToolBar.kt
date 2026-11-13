package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.EmojiEmotions
import androidx.compose.material.icons.outlined.Send
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.BottomBarAction

/**
 * 键盘底部的嵌入式工具条。
 *
 * 参考雨燕输入法的「全面屏键盘优化」，但**不做额外占位**：图标直接画在系统
 * 手势条/导航栏让出的那一条留白里，键盘高度与关闭时完全一致。
 *
 * 布局要点：
 * - 只有左右两个图标，贴两侧但不顶到屏幕边缘（留 20dp，避开手势边缘滑动）；
 * - 图标 18dp、圆形按压底色 30dp，视觉上是“嵌在留白里的小按钮”而非工具栏；
 * - 中间整片区域不挂任何 pointerInput，系统返回/上滑手势照常可用。
 */
@Composable
fun BottomToolBar(
    left: BottomBarAction,
    right: BottomBarAction,
    tint: Color,
    onAction: (BottomBarAction) -> Unit,
    modifier: Modifier = Modifier,
    barHeightDp: Float = 24f,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .fillMaxHeight()
            .padding(horizontal = 18.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BottomBarButton(left, tint, onAction)
        BottomBarButton(right, tint, onAction)
    }
}

@Composable
private fun BottomBarButton(
    action: BottomBarAction,
    tint: Color,
    onAction: (BottomBarAction) -> Unit,
) {
    val icon = iconOf(action) ?: return
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = Modifier
            .size(30.dp)
            .clip(CircleShape)
            .background(if (pressed) tint.copy(alpha = 0.16f) else tint.copy(alpha = 0.07f))
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = { onAction(action) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = action.name,
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(19.dp),
        )
    }
}

private fun iconOf(action: BottomBarAction): ImageVector? = when (action) {
    BottomBarAction.SWITCH_IME -> Icons.Filled.SwapHoriz
    BottomBarAction.SWITCH_LANGUAGE -> Icons.Filled.Language
    BottomBarAction.CLIPBOARD -> Icons.Filled.ContentPaste
    BottomBarAction.QUICK_PHRASE -> Icons.Outlined.Send
    BottomBarAction.EMOJI -> Icons.Outlined.EmojiEmotions
    BottomBarAction.HIDE_KEYBOARD -> Icons.Filled.KeyboardArrowDown
    BottomBarAction.SETTINGS -> Icons.Filled.Settings
    BottomBarAction.NONE -> null
}
