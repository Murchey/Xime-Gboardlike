package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.keyboard.isBuiltinToolbarButtonAvailable
import com.kingzcheung.xime.viewmodel.SchemaSwitchUiState
import com.kingzcheung.xime.viewmodel.currentIcon
import com.kingzcheung.xime.viewmodel.currentLabel

/** 工具面板中的一个工具项（统一渲染单元）。 */
data class MenuTool(
    val id: String,
    val label: String,
    val icon: Painter? = null,
    val textIcon: String? = null,
    val action: () -> Unit,
)

data class MenuBarState(
    val isVisible: Boolean,
    val isDarkTheme: Boolean,
    val darkMode: Int = 2,
    val backgroundColor: Color,
    val keyBgColor: Color = Color.White,
    val keyTextColor: Color = Color(0xFF202124),
    val isFloatingMode: Boolean = false,
    val schemaSwitches: List<SchemaSwitchUiState> = emptyList(),
    val canResizeKeyboard: Boolean = true,
    val canFloatKeyboard: Boolean = true,
    /** 当前工具条顺序（toolbar_buttons 偏好）。 */
    val toolbarButtons: List<String> = emptyList(),
    /** 插件 / 导入方案声明的自定义工具按钮。 */
    val toolbarPluginButtons: List<ToolbarButtonItem.Plugin> = emptyList(),
    val isSttEnabled: Boolean = true,
)

data class MenuBarCallbacks(
    val onDismiss: () -> Unit,
    val onClipboard: () -> Unit,
    val onQuickSend: () -> Unit,
    val onKeyboardResize: () -> Unit,
    val onEmoji: () -> Unit,
    val onReloadConfig: () -> Unit,
    val onSettings: () -> Unit,
    val onSchemaList: () -> Unit,
    val onToggleDarkMode: () -> Unit,
    val onFloatingModeToggle: (() -> Unit)? = null,
    val onToolbarCustomize: () -> Unit = {},
    val onToggleSchemaSwitch: ((SchemaSwitchUiState) -> Unit)? = null,
    /** 编辑态提交新的工具条顺序（写回 toolbar_buttons 偏好）。 */
    val onUpdateToolbarButtons: (List<String>) -> Unit = {},
    /** 工具条按钮点击分发（与候选栏同一套语义）。 */
    val onToolbarButtonClick: (String) -> Unit = {},
)

/**
 * Gboard 式工具面板（参考 01/02.jpg）：
 * - 常态：顶部一行圆形小图标（当前工具条）+ 右下角圆形铅笔；下方 2 列大工具卡网格；
 * - 编辑态：顶部可拖动图标卡（当前工具条，长按排序 / 向下拖出移除）+ 下方大卡片池
 *   （点按添加，含 Rime 导入方案自带的方案开关按钮与插件按钮）。
 * 排序结果即时写回 toolbar_buttons 偏好。
 */
@Composable
fun MenuBar(
    state: MenuBarState,
    callbacks: MenuBarCallbacks,
    modifier: Modifier = Modifier
) {
    if (!state.isVisible) return
    val textColor = state.keyTextColor
    val itemBgColor = state.keyBgColor
    var isEditing by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 工具池：内置按钮 + 插件按钮 + 导入方案自带的方案开关
    val builtinTools = ToolbarButton.entries.filter {
        isBuiltinToolbarButtonAvailable(
            it,
            isSttEnabled = state.isSttEnabled,
            hasHandwritingModel = { com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(context) },
        )
    }.map { button ->
        MenuTool(
            id = button.id,
            label = button.label,
            icon = rememberVectorPainter(button.icon),
            action = { callbacks.onToolbarButtonClick(button.id) },
        )
    }
    val switchTools = state.schemaSwitches.map { sw ->
        MenuTool(
            id = "switch:" + (sw.name.ifEmpty {
                sw.options.joinToString("+").ifEmpty { sw.states.joinToString("+") }
            }),
            label = sw.currentLabel().ifEmpty { sw.currentIcon() },
            textIcon = sw.currentIcon(),
            action = { callbacks.onToggleSchemaSwitch?.invoke(sw) },
        )
    }
    val pluginTools = state.toolbarPluginButtons.map { p ->
        MenuTool(
            id = p.id,
            label = p.label,
            textIcon = p.label.take(1),
            action = { callbacks.onToolbarButtonClick(p.id) },
        )
    }
    val allTools = builtinTools + switchTools + pluginTools
    val toolById = remember(allTools) { allTools.associateBy { it.id } }

    // 系统功能项（不参与工具条排布）：菜单里的设置类动作
    val darkLabel = when (state.darkMode) {
        0 -> tr(R.string.s_shensemoshi)
        1 -> tr(R.string.s_qiansemoshi)
        else -> tr(R.string.s_gensuixitong)
    }
    val floatLabel = if (state.isFloatingMode) tr(R.string.s_tuichuxuanfu) else tr(R.string.s_xuanfumoshi)
    val systemTools = buildList {
        add(MenuTool("sys_clipboard", tr(R.string.s_jiantieban), action = callbacks.onClipboard))
        add(MenuTool("sys_quick_send", tr(R.string.s_kuaijiefasong), action = callbacks.onQuickSend))
        add(MenuTool("sys_schema", tr(R.string.s_shurufangan), action = callbacks.onSchemaList))
        add(MenuTool("sys_emoji", tr(R.string.s_biaoqing), action = callbacks.onEmoji))
        if (!state.isFloatingMode && state.canResizeKeyboard) {
            add(MenuTool("sys_resize", tr(R.string.s_jianpandiaojie), action = callbacks.onKeyboardResize))
        }
        add(MenuTool("sys_dark", darkLabel, action = callbacks.onToggleDarkMode))
        if (state.canFloatKeyboard) {
            add(MenuTool("sys_float", floatLabel, action = callbacks.onFloatingModeToggle ?: {}))
        }
        add(MenuTool("sys_deploy", tr(R.string.s_bushufangan), action = callbacks.onReloadConfig))
    }

    val activeIds = state.toolbarButtons.filter { toolById.containsKey(it) }
    val activeTools = activeIds.mapNotNull(toolById::get)
    val availableTools = allTools.filter { it.id !in activeIds }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(state.backgroundColor)
    ) {
        val sideGap = if (maxWidth >= WIDE_CONTAINER_WIDTH) 50.dp else 16.dp
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = sideGap, vertical = 8.dp)
        ) {
            // 顶栏：返回（编辑态为返回常态）/ 标题 / 设置
            Row(
                modifier = Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(itemBgColor)
                        .clickable { if (isEditing) isEditing = false else callbacks.onDismiss() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = tr(R.string.s_fanhui),
                        tint = textColor,
                        modifier = Modifier.size(22.dp)
                    )
                }
                if (isEditing) {
                    Text(
                        text = tr(R.string.s_dingzhigongjulan),
                        color = textColor,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(itemBgColor)
                        .clickable { callbacks.onSettings() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        painter = rememberVectorPainter(Icons.TwoTone.Settings),
                        contentDescription = tr(R.string.s_shezhi),
                        tint = textColor,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (isEditing) {
                ToolbarEditMode(
                    activeTools = activeTools,
                    availableTools = availableTools,
                    textColor = textColor,
                    itemBgColor = itemBgColor,
                    onCommit = callbacks.onUpdateToolbarButtons,
                    onDone = { isEditing = false },
                )
            } else {
                ToolbarNormalMode(
                    activeTools = activeTools,
                    systemTools = systemTools,
                    textColor = textColor,
                    itemBgColor = itemBgColor,
                    onEdit = { isEditing = true },
                    onToolClick = { tool -> tool.action() },
                )
            }
        }
    }
}

/** 常态（01.jpg）：顶部圆形图标行 + 右下角圆形铅笔 + 2 列大工具卡。 */
@Composable
private fun ToolbarNormalMode(
    activeTools: List<MenuTool>,
    systemTools: List<MenuTool>,
    textColor: Color,
    itemBgColor: Color,
    onEdit: () -> Unit,
    onToolClick: (MenuTool) -> Unit,
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            activeTools.forEach { tool ->
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(itemBgColor)
                        .clickable { onToolClick(tool) },
                    contentAlignment = Alignment.Center
                ) {
                    ToolIcon(tool, textColor, 22.dp)
                }
            }
            Spacer(modifier = Modifier.weight(1f))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            systemTools.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { tool ->
                        ToolCard(
                            tool = tool,
                            textColor = textColor,
                            itemBgColor = itemBgColor,
                            modifier = Modifier.weight(1f).height(64.dp),
                            onClick = { onToolClick(tool) },
                        )
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        // 右下角圆形铅笔（Gboard 编辑入口）
        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(itemBgColor)
                    .clickable { onEdit() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    painter = rememberVectorPainter(Icons.TwoTone.Edit),
                    contentDescription = tr(R.string.s_dingzhigongjulan),
                    tint = textColor,
                    modifier = Modifier.size(26.dp)
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

/** 编辑态（02.jpg）：可拖动图标行 + 提示 + 下方卡片池（点按添加）。 */
@Composable
private fun ToolbarEditMode(
    activeTools: List<MenuTool>,
    availableTools: List<MenuTool>,
    textColor: Color,
    itemBgColor: Color,
    onCommit: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    var order by remember(activeTools) { mutableStateOf(activeTools.map { it.id }) }
    val slotCenters = remember { mutableStateMapOf<Int, Offset>() }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val density = LocalDensity.current
    val swapThreshold = with(density) { 44.dp.toPx() }
    val pool = activeTools + availableTools
    // 当前工具条图标行的屏幕 y 范围（卡片池拖入判定）
    var activeRowTop by remember { mutableStateOf(0f) }
    var activeRowBottom by remember { mutableStateOf(0f) }
    val poolCardTop = remember { mutableStateMapOf<String, Float>() }

    fun commit(list: List<String>) {
        order = list
        onCommit(list)
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = tr(R.string.s_yiqiyong),
            color = textColor.copy(alpha = 0.7f),
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(bottom = 6.dp)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .onGloballyPositioned { c ->
                    activeRowTop = c.positionInRoot().y
                    activeRowBottom = activeRowTop + c.size.height
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            order.forEachIndexed { slot, id ->
                val tool = pool.firstOrNull { it.id == id } ?: return@forEachIndexed
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .onGloballyPositioned { c ->
                            slotCenters[slot] = c.positionInRoot() + Offset(
                                c.size.width / 2f, c.size.height / 2f
                            )
                        }
                        .graphicsLayer {
                            if (slot == dragSlot) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.1f
                                scaleY = 1.1f
                            }
                        }
                        .pointerInput(order.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragSlot = slot; dragOffset = Offset.Zero },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val base = slotCenters[dragSlot]
                                    if (base != null) {
                                        val center = base + dragOffset
                                        var target = -1
                                        var best = Float.MAX_VALUE
                                        slotCenters.forEach { (i, c) ->
                                            if (i == dragSlot) return@forEach
                                            val d = (c - center).getDistance()
                                            if (d < best) { best = d; target = i }
                                        }
                                        if (target >= 0 && best < swapThreshold) {
                                            val from = dragSlot
                                            val fromC = slotCenters[from]
                                            val toC = slotCenters[target]
                                            val newList = order.toMutableList()
                                            val moved = newList.removeAt(from)
                                            newList.add(target, moved)
                                            commit(newList)
                                            if (fromC != null && toC != null) {
                                                dragOffset += (fromC - toC)
                                            }
                                            dragSlot = target
                                        }
                                        // 向下拖出图标行 → 移除
                                        if (dragOffset.y > 120f) {
                                            commit(order - id)
                                            dragSlot = -1
                                            dragOffset = Offset.Zero
                                        }
                                    }
                                },
                                onDragEnd = { dragSlot = -1; dragOffset = Offset.Zero },
                                onDragCancel = { dragSlot = -1; dragOffset = Offset.Zero },
                            )
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(itemBgColor),
                    contentAlignment = Alignment.Center
                ) {
                    ToolIcon(tool, textColor, 26.dp)
                    // 右上角 × 角标：点按移除（Gboard 编辑态）
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(3.dp)
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = 0.75f))
                            .clickable { commit(order - id) },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            painter = rememberVectorPainter(Icons.Default.Close),
                            contentDescription = tr(R.string.s_shanchu),
                            tint = itemBgColor,
                            modifier = Modifier.size(10.dp)
                        )
                    }
                }
            }
        }

        Text(
            text = tr(R.string.s_changantuodongpaixu),
            color = textColor.copy(alpha = 0.5f),
            fontSize = 11.sp,
            modifier = Modifier.padding(vertical = 8.dp)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            availableTools.chunked(2).forEach { row ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    row.forEach { tool ->
                        // 卡片池：点按或向上拖入图标行均可添加
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .height(64.dp)
                                .onGloballyPositioned { c ->
                                    poolCardTop[tool.id] = c.positionInRoot().y
                                }
                                .clip(RoundedCornerShape(16.dp))
                                .background(itemBgColor)
                                .pointerInput(Unit) {
                                    var totalDy = 0f
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { totalDy = 0f },
                                        onDrag = { change, amount ->
                                            change.consume()
                                            totalDy += amount.y
                                        },
                                        onDragEnd = {
                                            // 卡片向上拖入图标行区域 → 加入工具条
                                            val cardTop = poolCardTop[tool.id] ?: 0f
                                            val endY = cardTop + totalDy
                                            if (tool.id !in order && endY < activeRowBottom) {
                                                commit(order + tool.id)
                                            }
                                        },
                                        onDragCancel = {},
                                    )
                                }
                                .clickable { if (tool.id !in order) commit(order + tool.id) },
                            contentAlignment = Alignment.CenterStart
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 16.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                ToolIcon(tool, textColor, 22.dp)
                                Text(
                                    text = tool.label,
                                    color = textColor,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                    if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
            horizontalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.5f)
                    .height(40.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(itemBgColor)
                    .clickable { onDone() },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = tr(R.string.s_wancheng),
                    color = textColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
    }
}

@Composable
private fun ToolIcon(tool: MenuTool, textColor: Color, size: Dp) {
    if (tool.textIcon != null) {
        Text(
            text = tool.textIcon,
            color = textColor.copy(alpha = 0.8f),
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            textAlign = TextAlign.Center,
        )
    } else if (tool.icon != null) {
        Icon(
            painter = tool.icon,
            contentDescription = tool.label,
            tint = textColor.copy(alpha = 0.8f),
            modifier = Modifier.size(size)
        )
    }
}

/** Gboard 式大工具卡：左侧图标 + 文字，圆角块。 */
@Composable
private fun ToolCard(
    tool: MenuTool,
    textColor: Color,
    itemBgColor: Color,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(itemBgColor)
            .clickable { onClick() }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ToolIcon(tool, textColor, 22.dp)
        Text(
            text = tool.label,
            color = textColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
