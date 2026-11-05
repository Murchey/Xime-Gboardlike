package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.keyboard.isBuiltinToolbarButtonAvailable
import com.kingzcheung.xime.ui.keyboard.ToolbarButtonIcon
import com.kingzcheung.xime.ui.keyboard.WIDE_CONTAINER_WIDTH

/**
 * 定制工具栏（Gboard 拖动式定制栏）：
 *
 * 「已启用」区 = 当前工具栏按钮，长按拖动排序、点 × 移除；
 * 「更多按钮」区 = 可添加按钮，点按添加。
 * 顺序即候选栏工具栏的显示顺序（toolbar_buttons 偏好，写入即持久化）。
 */
@Composable
fun ToolbarCustomizeView(
    toolbarButtons: List<String>,
    pluginButtons: List<ToolbarButtonItem.Plugin> = emptyList(),
    /** 方案开关按钮（含导入方案自带开关），同样参与拖动排布。 */
    switchItems: List<ToolbarButtonItem.SchemaSwitch> = emptyList(),
    isSttEnabled: Boolean = true,
    keyTextColor: Color,
    backgroundColor: Color,
    accentColor: Color,
    keyBgColor: Color,
    onUpdateToolbarButtons: ((List<String>) -> Unit)?,
    onDismiss: () -> Unit,
    bottomPaddingDp: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    // 与候选栏显示同一口径：关掉语音转文本、未装手写模型时，定制列表里也不该出现
    val builtinButtons = ToolbarButton.entries
        .filter {
            isBuiltinToolbarButtonAvailable(
                it,
                isSttEnabled = isSttEnabled,
                hasHandwritingModel = { com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(context) },
            )
        }
        .map { ToolbarButtonItem.Builtin(it) }
    val allButtons = builtinButtons + switchItems + pluginButtons
    val itemById = remember(allButtons) { allButtons.associateBy { it.id } }

    // 本地顺序即时生效，同步写回偏好；外部变更（理论上无）再对齐
    var items by remember { mutableStateOf(toolbarButtons) }
    LaunchedEffect(toolbarButtons) {
        if (toolbarButtons != items) items = toolbarButtons
    }

    // 拖动排序状态：拖动项所在槽位 + 指尖偏移；槽位中心按位置记录（槽位尺寸固定不漂移）
    val slotCenters = remember { mutableStateMapOf<Int, Offset>() }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val swapThresholdPx = with(LocalDensity.current) { 34.dp.toPx() }

    fun commit(newList: List<String>) {
        items = newList
        onUpdateToolbarButtons?.invoke(newList)
    }

    fun removeItem(id: String) {
        commit(items - id)
    }

    fun addItem(id: String) {
        if (id !in items) commit(items + id)
    }

    // 与键盘图标按钮同款容器色（按键背景与强调色的混合色调）
    val iconButtonContainer = androidx.compose.ui.graphics.lerp(
        keyBgColor,
        accentColor,
        0.25f
    )

    // 布局按父容器真实宽度自适应（悬浮卡片/键盘收窄/分屏的容器宽 ≠ 屏幕宽），
    // 不读屏幕方向：宽容器（横屏全屏）用大边距，其余按竖屏形态
    BoxWithConstraints(modifier = modifier) {
        val isWide = maxWidth >= WIDE_CONTAINER_WIDTH

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(backgroundColor),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
                .padding(horizontal = if (isWide) 50.dp else 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(iconButtonContainer)
                    .clickable { onDismiss() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = tr(R.string.s_queding),
                    tint = keyTextColor,
                    modifier = Modifier.size(24.dp)
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = if (isWide) 50.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CustomizeSectionHeader(
                title = tr(R.string.s_yiqiyong),
                hint = tr(R.string.s_changantuodongpaixu),
                textColor = keyTextColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(keyBgColor)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                if (items.isEmpty()) {
                    Text(
                        text = tr(R.string.s_zanwushuju),
                        color = keyTextColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        items.forEachIndexed { slot, id ->
                            val item = itemById[id] ?: return@forEachIndexed
                            Box(modifier = Modifier.width(60.dp)) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .onGloballyPositioned { coords ->
                                            slotCenters[slot] = coords.positionInRoot() + Offset(
                                                coords.size.width / 2f,
                                                coords.size.height / 2f,
                                            )
                                        }
                                        .graphicsLayer {
                                            if (slot == dragSlot) {
                                                translationX = dragOffset.x
                                                translationY = dragOffset.y
                                                scaleX = 1.08f
                                                scaleY = 1.08f
                                            }
                                        }
                                        // 槽位（位置）持有拖动手势：交换后键值不变、手势不中断，
                                        // 数学全部走 dragSlot 状态，重排后仍指向拖动项所在槽位
                                        .pointerInput(items.size) {
                                            detectDragGesturesAfterLongPress(
                                                onDragStart = {
                                                    dragSlot = slot
                                                    dragOffset = Offset.Zero
                                                },
                                                onDrag = { change, amount ->
                                                    change.consume()
                                                    dragOffset += amount
                                                    val base = slotCenters[dragSlot]
                                                    if (base != null) {
                                                        val draggedCenter = base + dragOffset
                                                        var target = -1
                                                        var best = Float.MAX_VALUE
                                                        slotCenters.forEach { (i, c) ->
                                                            if (i == dragSlot) return@forEach
                                                            val d = (c - draggedCenter).getDistance()
                                                            if (d < best) {
                                                                best = d
                                                                target = i
                                                            }
                                                        }
                                                        if (target >= 0 && best < swapThresholdPx) {
                                                            val from = dragSlot
                                                            val fromCenter = slotCenters[from]
                                                            val toCenter = slotCenters[target]
                                                            val newList = items.toMutableList()
                                                            val moved = newList.removeAt(from)
                                                            newList.add(target, moved)
                                                            commit(newList)
                                                            // 槽位互换后拖动项换了座标系，补偿偏移保持贴手
                                                            if (fromCenter != null && toCenter != null) {
                                                                dragOffset += (fromCenter - toCenter)
                                                            }
                                                            dragSlot = target
                                                        }
                                                    }
                                                },
                                                onDragEnd = {
                                                    dragSlot = -1
                                                    dragOffset = Offset.Zero
                                                },
                                                onDragCancel = {
                                                    dragSlot = -1
                                                    dragOffset = Offset.Zero
                                                },
                                            )
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Box(
                                            modifier = Modifier
                                                .size(44.dp)
                                                .clip(CircleShape)
                                                .background(iconButtonContainer),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            ToolbarButtonIcon(
                                                item = item,
                                                tint = keyTextColor.copy(alpha = 0.75f),
                                                modifier = Modifier.size(22.dp),
                                            )
                                        }
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .size(16.dp)
                                                .clip(CircleShape)
                                                .background(keyTextColor.copy(alpha = 0.75f))
                                                .clickable { removeItem(item.id) },
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = tr(R.string.s_shanchu),
                                                tint = keyBgColor,
                                                modifier = Modifier.size(11.dp)
                                            )
                                        }
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = item.label,
                                        fontSize = 10.sp,
                                        color = keyTextColor.copy(alpha = 0.8f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }
                }
            }

            CustomizeSectionHeader(
                title = tr(R.string.s_gengduoanniu),
                hint = tr(R.string.s_dianjiantianjiahuoyichu),
                textColor = keyTextColor
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(keyBgColor)
                    .padding(horizontal = 10.dp, vertical = 12.dp)
            ) {
                val availableButtons = allButtons.filter { it.id !in items }
                if (availableButtons.isEmpty()) {
                    Text(
                        text = tr(R.string.s_zanwushuju),
                        color = keyTextColor.copy(alpha = 0.5f),
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        availableButtons.forEach { button ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.width(60.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(44.dp)
                                        .clip(CircleShape)
                                        .background(Color.Transparent)
                                        .border(
                                            width = 1.dp,
                                            color = keyTextColor.copy(alpha = 0.15f),
                                            shape = CircleShape
                                        )
                                        .clickable { addItem(button.id) },
                                    contentAlignment = Alignment.Center
                                ) {
                                    ToolbarButtonIcon(
                                        item = button,
                                        tint = keyTextColor.copy(alpha = 0.8f),
                                        modifier = Modifier.size(22.dp),
                                    )
                                }
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = button.label,
                                    fontSize = 10.sp,
                                    color = keyTextColor.copy(alpha = 0.8f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(if (isWide) 15.dp else bottomPaddingDp.dp))
    }
    }
}

/** 分区标题：小号标题 + 右侧浅色操作提示。 */
@Composable
private fun CustomizeSectionHeader(
    title: String,
    hint: String,
    textColor: Color,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            color = textColor.copy(alpha = 0.7f),
            maxLines = 1
        )
        Text(
            text = hint,
            fontSize = 10.sp,
            color = textColor.copy(alpha = 0.45f),
            maxLines = 1
        )
    }
}
