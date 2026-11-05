package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.twotone.LastPage
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.FirstPage
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 编辑/标记面板（仿小企鹅 fcitx5-android 的 TextEditingUi 布局）：
 *
 * 左区 = 十字方向键：←/→ 通高两条，↑/选择/↓ 居中列，底行 段首/段尾；
 * 右区 = 操作列（alt 底色）：全选/剪切/复制/粘贴，底行 退格/回车（fcitx5 的退格位）。
 *
 * 中心「选择」键进入选择态后方向键扩展选区（select_arrow_*）、
 * 段首/段尾扩展到文本两端（select_move_home / select_move_end）；
 * 长按方向键与退格支持连发（380ms 后 75ms 间隔，与原圆形方向盘手感一致）。
 */
@Composable
fun EditKeyboardLayout(
    onAction: (String) -> Unit,
    onBack: () -> Unit,
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    bottomPaddingDp: Int = 0,
    /** 与主键盘一致的主题按键圆角（LocalKeyCornerRadius），供复用的 KeyButton 读取 */
    keyCornerRadius: Dp = 8.dp,
    shadowEnabled: Boolean = true,
    shadowElevation: Dp = 1.dp,
    shadowShapeRadius: Dp = 8.dp,
    modifier: Modifier = Modifier,
    /** 编辑器存在选区：右列首行在「全选 / 剪切」间切换（仿 fcitx5 updateSelection）。 */
    hasSelection: Boolean = false,
) {
    val keyBg = keyBgColor
    // fcitx5 右列按键用 alt 底色与方向键区分：向键盘底色靠一档，深浅主题通用
    val altKeyBg = Color(
        red = keyBg.red * 0.84f + backgroundColor.red * 0.16f,
        green = keyBg.green * 0.84f + backgroundColor.green * 0.16f,
        blue = keyBg.blue * 0.84f + backgroundColor.blue * 0.16f,
        alpha = 1f,
    )
    var isSelecting by remember { mutableStateOf(false) }

    fun directionAction(base: String): String = if (isSelecting) "select_$base" else base

    // 布局按父容器真实宽度自适应（悬浮卡片/键盘收窄/分屏的容器宽 ≠ 屏幕宽），
    // 不读屏幕方向：宽容器（横屏全屏）用大边距，其余按竖屏形态
    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= WIDE_CONTAINER_WIDTH
        val sideGap = if (isWide) 50.dp else 8.dp

    CompositionLocalProvider(
        LocalKeyCornerRadius provides keyCornerRadius,
        LocalKeyVisualPadding provides PaddingValues(horizontal = 2.dp, vertical = 2.dp),
    ) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(start = sideGap, end = sideGap),
            contentAlignment = Alignment.CenterStart
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(keyBg)
                    .clickable { onBack() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = tr(R.string.s_fanhui),
                    tint = textColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = sideGap),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // 左区：小企鹅式十字方向键（←/→ 通高）+ 段首/段尾
            Column(
                modifier = Modifier
                    .weight(2.3f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    modifier = Modifier
                        .weight(3f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val (leftPress, leftRelease) = rememberRepeatAction {
                        onAction(directionAction("arrow_left"))
                    }
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowLeft),
                        onClick = {},
                        backgroundColor = keyBg,
                        iconColor = textColor,
                        onPress = leftPress,
                        onRelease = leftRelease,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        iconSize = 28.dp,
                        a11yDescription = tr(R.string.s_xiangzuo),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val (upPress, upRelease) = rememberRepeatAction {
                            onAction(directionAction("arrow_up"))
                        }
                        IconKeyButton(
                            icon = rememberVectorPainter(Icons.Filled.KeyboardArrowUp),
                            onClick = {},
                            backgroundColor = keyBg,
                            iconColor = textColor,
                            onPress = upPress,
                            onRelease = upRelease,
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                            iconSize = 28.dp,
                            a11yDescription = tr(R.string.s_xiangshang),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        KeyButton(
                            // fcitx5 选择键：文案恒为「选择」，选择态用激活高亮表达
                            text = tr(R.string.s_xuanze),
                            onClick = {
                                val newSelecting = !isSelecting
                                isSelecting = newSelecting
                                onAction(if (newSelecting) "select_begin" else "select_end")
                            },
                            backgroundColor = if (isSelecting) accentColor else keyBg,
                            textColor = if (isSelecting) readableOn(accentColor) else textColor,
                            isHighlighted = isSelecting || hasSelection,
                            fontSize = 14.sp,
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                        val (downPress, downRelease) = rememberRepeatAction {
                            onAction(directionAction("arrow_down"))
                        }
                        IconKeyButton(
                            icon = rememberVectorPainter(Icons.Filled.KeyboardArrowDown),
                            onClick = {},
                            backgroundColor = keyBg,
                            iconColor = textColor,
                            onPress = downPress,
                            onRelease = downRelease,
                            shadowEnabled = shadowEnabled,
                            shadowElevation = shadowElevation,
                            shadowShapeRadius = shadowShapeRadius,
                            iconSize = 28.dp,
                            a11yDescription = tr(R.string.s_xiangxia),
                            modifier = Modifier.weight(1f).fillMaxWidth()
                        )
                    }
                    val (rightPress, rightRelease) = rememberRepeatAction {
                        onAction(directionAction("arrow_right"))
                    }
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.Filled.KeyboardArrowRight),
                        onClick = {},
                        backgroundColor = keyBg,
                        iconColor = textColor,
                        onPress = rightPress,
                        onRelease = rightRelease,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        iconSize = 28.dp,
                        a11yDescription = tr(R.string.s_xiangyou),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.TwoTone.FirstPage),
                        onClick = { onAction(directionAction("home")) },
                        backgroundColor = keyBg,
                        iconColor = textColor,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        iconSize = 24.dp,
                        a11yDescription = tr(R.string.s_duanshou),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                    IconKeyButton(
                        icon = rememberVectorPainter(Icons.AutoMirrored.TwoTone.LastPage),
                        onClick = { onAction(directionAction("end")) },
                        backgroundColor = keyBg,
                        iconColor = textColor,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        iconSize = 24.dp,
                        a11yDescription = tr(R.string.s_duanwei),
                        modifier = Modifier.weight(1f).fillMaxHeight()
                    )
                }
            }

            // 右区：小企鹅式操作列（alt 底色）——4 行与左区行高对齐；
            // 首行随选区在「全选 / 剪切」间切换（fcitx5 updateSelection 语义）
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (hasSelection) {
                    KeyButton(
                        text = tr(R.string.s_jianqie),
                        onClick = { onAction("cut") },
                        backgroundColor = altKeyBg,
                        textColor = textColor,
                        fontSize = 14.sp,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                } else {
                    KeyButton(
                        text = tr(R.string.s_quanxuan),
                        onClick = { onAction("select_all") },
                        backgroundColor = altKeyBg,
                        textColor = textColor,
                        fontSize = 14.sp,
                        shadowEnabled = shadowEnabled,
                        shadowElevation = shadowElevation,
                        shadowShapeRadius = shadowShapeRadius,
                        modifier = Modifier.weight(1f).fillMaxWidth()
                    )
                }
                KeyButton(
                    text = tr(R.string.s_fuzhi),
                    onClick = { onAction("copy") },
                    backgroundColor = altKeyBg,
                    textColor = textColor,
                    fontSize = 14.sp,
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                KeyButton(
                    text = tr(R.string.s_zhantie),
                    onClick = { onAction("paste") },
                    backgroundColor = altKeyBg,
                    textColor = textColor,
                    fontSize = 14.sp,
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
                val (delPress, delRelease) = rememberRepeatAction { onAction("delete") }
                IconKeyButton(
                    icon = rememberVectorPainter(Icons.AutoMirrored.Filled.Backspace),
                    onClick = {},
                    backgroundColor = altKeyBg,
                    iconColor = textColor,
                    onPress = delPress,
                    onRelease = delRelease,
                    shadowEnabled = shadowEnabled,
                    shadowElevation = shadowElevation,
                    shadowShapeRadius = shadowShapeRadius,
                    iconSize = 22.dp,
                    a11yDescription = tr(R.string.keyboard_delete),
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )
            }
        }

        Spacer(
            modifier = Modifier.height(bottomPaddingDp.dp)
        )
    }
    }
    }
}

/** 选择态高亮底色上的文字色：按底色明度取黑/白，保证对比度。 */
private fun readableOn(background: Color): Color =
    if (background.luminance() > 0.5f) Color(0xDD000000) else Color.White

/**
 * 长按连发：按下立即触发一次，380ms 后每 75ms 连发一次（与原圆形方向盘一致）。
 * 返回（按下处理, 松开处理）对，接 IconKeyButton 的 onPress/onRelease；
 * 调用方不要再传 onClick，避免松手二次触发。
 */
@Composable
private fun rememberRepeatAction(action: () -> Unit): Pair<() -> Unit, () -> Unit> {
    val scope = rememberCoroutineScope()
    var job by remember { mutableStateOf<Job?>(null) }
    val currentAction by rememberUpdatedState(action)
    val onPress: () -> Unit = {
        currentAction()
        job?.cancel()
        job = scope.launch {
            delay(380)
            while (isActive) {
                currentAction()
                delay(75)
            }
        }
    }
    val onRelease: () -> Unit = {
        job?.cancel()
        job = null
    }
    return onPress to onRelease
}
