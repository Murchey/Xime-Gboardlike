package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.keyboard.isBuiltinToolbarButtonAvailable
import com.kingzcheung.xime.viewmodel.SchemaSwitchUiState
import com.kingzcheung.xime.viewmodel.currentIcon
import com.kingzcheung.xime.viewmodel.currentLabel

import androidx.compose.foundation.background
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

/** 工具条/工具面板统一数据项。 */
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
    val toolbarButtons: List<String> = emptyList(),
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
    val onUpdateToolbarButtons: (List<String>) -> Unit = {},
    val onToolbarButtonClick: (String) -> Unit = {},
)

/**
 * 单一工具栏状态机：普通态展示，编辑态排布，完成时一次性保存。
 * 方案开关和插件按钮都经过同一工具池，因此不会再出现多个入口各自维护顺序。
 */
@Composable
fun MenuBar(
    state: MenuBarState,
    callbacks: MenuBarCallbacks,
    modifier: Modifier = Modifier,
) {
    if (!state.isVisible) return
    val context = LocalContext.current
    val textColor = state.keyTextColor
    val cardColor = state.keyBgColor
    var editing by remember { mutableStateOf(false) }
    var draft by remember(state.toolbarButtons) { mutableStateOf(state.toolbarButtons) }

    val builtin = ToolbarButton.entries.filter {
        isBuiltinToolbarButtonAvailable(
            it,
            state.isSttEnabled,
            { com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(context) },
        )
    }.map { b ->
        MenuTool(b.id, b.label, rememberVectorPainter(b.icon)) {
            callbacks.onToolbarButtonClick(b.id)
        }
    }
    val switches = state.schemaSwitches.map { sw ->
        val key = sw.name.ifEmpty { sw.options.joinToString("+").ifEmpty { sw.states.joinToString("+") } }
        MenuTool("switch:$key", sw.currentLabel().ifEmpty { sw.currentIcon() }, textIcon = sw.currentIcon()) {
            callbacks.onToggleSchemaSwitch?.invoke(sw)
        }
    }
    val plugins = state.toolbarPluginButtons.map { p ->
        MenuTool(p.id, p.label, textIcon = p.label.take(1)) {
            callbacks.onToolbarButtonClick(p.id)
        }
    }
    val allTools = builtin + switches + plugins
    val byId = remember(allTools) { allTools.associateBy { it.id } }
    val activeIds = (if (editing) draft else state.toolbarButtons).filter { byId.containsKey(it) }
    val activeTools = activeIds.mapNotNull { byId[it] }
    val availableTools = allTools.filter { it.id !in activeIds }

    val darkLabel = when (state.darkMode) {
        0 -> tr(R.string.s_shensemoshi)
        1 -> tr(R.string.s_qiansemoshi)
        else -> tr(R.string.s_gensuixitong)
    }
    val systemTools = buildList {
        add(MenuTool("sys_clipboard", tr(R.string.s_jiantieban), action = callbacks.onClipboard))
        add(MenuTool("sys_quick", tr(R.string.s_kuaijiefasong), action = callbacks.onQuickSend))
        add(MenuTool("sys_schema", tr(R.string.s_shurufangan), action = callbacks.onSchemaList))
        add(MenuTool("sys_emoji", tr(R.string.s_biaoqing), action = callbacks.onEmoji))
        if (!state.isFloatingMode && state.canResizeKeyboard) {
            add(MenuTool("sys_resize", tr(R.string.s_jianpandiaojie), action = callbacks.onKeyboardResize))
        }
        add(MenuTool("sys_dark", darkLabel, action = callbacks.onToggleDarkMode))
        if (state.canFloatKeyboard) {
            add(MenuTool("sys_float", if (state.isFloatingMode) tr(R.string.s_tuichuxuanfu) else tr(R.string.s_xuanfumoshi), action = callbacks.onFloatingModeToggle ?: {}))
        }
        add(MenuTool("sys_deploy", tr(R.string.s_bushufangan), action = callbacks.onReloadConfig))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().background(state.backgroundColor)) {
        val side = if (maxWidth >= WIDE_CONTAINER_WIDTH) 50.dp else 16.dp
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = 8.dp)) {
            Row(
                Modifier.fillMaxWidth().height(44.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircleAction(
                    icon = Icons.Default.KeyboardArrowUp,
                    tint = textColor,
                    bg = cardColor,
                    label = tr(R.string.s_fanhui),
                ) { if (editing) { editing = false; draft = state.toolbarButtons } else callbacks.onDismiss() }
                if (editing) {
                    Text(tr(R.string.s_dingzhigongjulan), color = textColor, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                } else Spacer(Modifier.width(1.dp))
                CircleAction(
                    icon = Icons.TwoTone.Settings,
                    tint = textColor,
                    bg = cardColor,
                    label = tr(R.string.s_shezhi),
                    size = 20.dp,
                ) { callbacks.onSettings() }
            }

            if (editing) {
                ToolbarEditMode(
                    activeTools = activeTools,
                    availableTools = availableTools,
                    textColor = textColor,
                    cardColor = cardColor,
                    draft = draft,
                    onDraftChange = { draft = it },
                    onDone = {
                        callbacks.onUpdateToolbarButtons(draft)
                        editing = false
                    },
                )
            } else {
                ToolbarNormalMode(
                    activeTools = activeTools,
                    systemTools = systemTools,
                    textColor = textColor,
                    cardColor = cardColor,
                    onEdit = { draft = state.toolbarButtons; editing = true },
                )
            }
        }
    }
}

@Composable
private fun ToolbarNormalMode(
    activeTools: List<MenuTool>,
    systemTools: List<MenuTool>,
    textColor: Color,
    cardColor: Color,
    onEdit: () -> Unit,
) {
    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(48.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            activeTools.forEach { tool ->
                CircleTool(tool, textColor, cardColor)
            }
            Spacer(Modifier.weight(1f))
        }
        Column(
            Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            systemTools.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        ToolCard(tool, textColor, cardColor, Modifier.weight(1f).height(64.dp))
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.End) {
            CircleAction(
                icon = Icons.TwoTone.Edit,
                tint = textColor,
                bg = cardColor,
                label = tr(R.string.s_dingzhigongjulan),
                size = 26.dp,
                buttonSize = 44.dp,
                onClick = onEdit,
            )
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun ToolbarEditMode(
    activeTools: List<MenuTool>,
    availableTools: List<MenuTool>,
    textColor: Color,
    cardColor: Color,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit,
    onDone: () -> Unit,
) {
    val pool = activeTools + availableTools
    val positions = remember { mutableStateMapOf<Int, Offset>() }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val threshold = with(LocalDensity.current) { 44.dp.toPx() }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().height(72.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            draft.forEachIndexed { slot, id ->
                val tool = pool.firstOrNull { it.id == id } ?: return@forEachIndexed
                Box(
                    Modifier
                        .size(60.dp)
                        .onGloballyPositioned { c -> positions[slot] = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f) }
                        .graphicsLayer {
                            if (slot == dragSlot) {
                                translationX = dragOffset.x
                                translationY = dragOffset.y
                                scaleX = 1.08f
                                scaleY = 1.08f
                            }
                        }
                        .pointerInput(draft.size) {
                            detectDragGesturesAfterLongPress(
                                onDragStart = { dragSlot = slot; dragOffset = Offset.Zero },
                                onDrag = { change, amount ->
                                    change.consume()
                                    dragOffset += amount
                                    val center = positions[dragSlot]?.plus(dragOffset) ?: return@detectDragGesturesAfterLongPress
                                    if (dragOffset.y > 110.dp.toPx()) {
                                        onDraftChange(draft - id)
                                        dragSlot = -1
                                        dragOffset = Offset.Zero
                                        return@detectDragGesturesAfterLongPress
                                    }
                                    var target = -1
                                    var best = Float.MAX_VALUE
                                    positions.forEach { (i, p) ->
                                        if (i == dragSlot) return@forEach
                                        val d = (p - center).getDistance()
                                        if (d < best) { best = d; target = i }
                                    }
                                    if (target >= 0 && best < threshold) {
                                        val mutable = draft.toMutableList()
                                        val moved = mutable.removeAt(dragSlot)
                                        mutable.add(target, moved)
                                        onDraftChange(mutable)
                                        dragSlot = target
                                    }
                                },
                                onDragEnd = { dragSlot = -1; dragOffset = Offset.Zero },
                                onDragCancel = { dragSlot = -1; dragOffset = Offset.Zero },
                            )
                        }
                        .clip(RoundedCornerShape(12.dp))
                        .background(cardColor),
                    contentAlignment = Alignment.Center,
                ) {
                    ToolIcon(tool, textColor, 26.dp)
                    Box(
                        Modifier.align(Alignment.TopEnd).padding(3.dp).size(16.dp).clip(CircleShape)
                            .background(textColor.copy(alpha = .75f)).clickable { onDraftChange(draft - id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Default.Close, tr(R.string.s_shanchu), tint = cardColor, modifier = Modifier.size(10.dp))
                    }
                }
            }
        }
        Text(tr(R.string.s_changantuodongpaixu), color = textColor.copy(alpha = .55f), fontSize = 11.sp, modifier = Modifier.padding(vertical = 8.dp))
        Column(Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            availableTools.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    row.forEach { tool ->
                        ToolCard(tool, textColor, cardColor, Modifier.weight(1f).height(64.dp)) {
                            if (tool.id !in draft) onDraftChange(draft + tool.id)
                        }
                    }
                    if (row.size == 1) Spacer(Modifier.weight(1f))
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            Box(Modifier.fillMaxWidth(.5f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(cardColor).clickable { onDone() }, contentAlignment = Alignment.Center) {
                Text(tr(R.string.s_wancheng), color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
    }
}

@Composable
private fun CircleTool(tool: MenuTool, textColor: Color, cardColor: Color) {
    Box(Modifier.size(40.dp).clip(CircleShape).background(cardColor).clickable { tool.action() }, contentAlignment = Alignment.Center) {
        ToolIcon(tool, textColor, 22.dp)
    }
}

@Composable
private fun CircleAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color,
    bg: Color,
    label: String,
    size: Dp = 22.dp,
    buttonSize: Dp = 32.dp,
    onClick: () -> Unit,
) {
    Box(Modifier.size(buttonSize).clip(CircleShape).background(bg).clickable { onClick() }, contentAlignment = Alignment.Center) {
        Icon(icon, label, tint = tint, modifier = Modifier.size(size))
    }
}

@Composable
private fun ToolIcon(tool: MenuTool, textColor: Color, size: Dp) {
    if (tool.textIcon != null) {
        Text(tool.textIcon, color = textColor.copy(alpha = .82f), fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, textAlign = TextAlign.Center)
    } else if (tool.icon != null) {
        Icon(tool.icon, tool.label, tint = textColor.copy(alpha = .82f), modifier = Modifier.size(size))
    }
}

@Composable
private fun ToolCard(tool: MenuTool, textColor: Color, cardColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = tool.action) {
    Row(
        modifier = modifier.clip(RoundedCornerShape(16.dp)).background(cardColor).clickable { onClick() }.padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ToolIcon(tool, textColor, 22.dp)
        Text(tool.label, color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}
