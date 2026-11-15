package com.kingzcheung.xime.ui.keyboard

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.CropFree
import androidx.compose.material.icons.filled.OpenInFull
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

/**
 * 单手模式的两个控制钮，画在键盘让出来的那条留白里（不占键盘高度）：
 *
 * - 上：四角箭头 = 退出单手模式，恢复双手全宽键盘；
 * - 下：左右箭头 = 换手（键盘靠哪一侧）。
 *
 * 按钮本身不做背景卡片，只在按压时透出一层很淡的圆底，避免留白里出现两个突兀的色块。
 */
@Composable
fun OneHandedControls(
    keyboardOnLeft: Boolean,
    tint: Color,
    onToggleSide: () -> Unit,
    onExit: () -> Unit,
    onResize: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 排布参照 Gboard：退出 → 换边 → 调大小（自上而下）
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        CircleIconButton(
            icon = Icons.Filled.OpenInFull,
            contentDescription = tr(R.string.s_tuichudanshou),
            tint = tint,
            onClick = onExit,
        )
        CircleIconButton(
            icon = if (keyboardOnLeft) {
                Icons.AutoMirrored.Filled.KeyboardArrowRight
            } else {
                Icons.AutoMirrored.Filled.KeyboardArrowLeft
            },
            contentDescription = tr(R.string.s_danshou_qiehuan),
            tint = tint,
            onClick = onToggleSide,
            onLongClick = onExit,
        )
        CircleIconButton(
            icon = Icons.Filled.CropFree,
            contentDescription = tr(R.string.s_danshou_tiaozhengdaxiao),
            tint = tint,
            onClick = onResize,
        )
    }
}

@Composable
private fun CircleIconButton(
    icon: ImageVector,
    contentDescription: String,
    tint: Color,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.10f))
            .pointerInput(icon, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = tint.copy(alpha = 0.85f),
            modifier = Modifier.size(26.dp),
        )
    }
}
