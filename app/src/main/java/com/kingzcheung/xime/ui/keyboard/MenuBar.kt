package com.kingzcheung.xime.ui.keyboard

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.keyboard.ToolbarButton
import com.kingzcheung.xime.keyboard.ToolbarButtonItem
import com.kingzcheung.xime.keyboard.isBuiltinToolbarButtonAvailable
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.viewmodel.SchemaSwitchUiState
import com.kingzcheung.xime.viewmodel.currentIcon
import com.kingzcheung.xime.viewmodel.currentLabel

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.twotone.Edit
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
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

/** 工具栏顶部最多容纳的功能数（Gboard 式：顶部分散放 5 个）。 */
private const val TOOLBAR_MAX_ITEMS = 5

/**
 * 向工具栏加入一项（Gboard FIFO）：已满 5 个时挤掉最早的一个，保持顶部数量恒定。
 */
private fun addToToolbarFifo(draft: List<String>, id: String): List<String> {
    val list = draft.filter { it != id }.toMutableList()
    list.add(id)
    while (list.size > TOOLBAR_MAX_ITEMS) {
        list.removeAt(0)
    }
    return list
}

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
 * 编辑态保留顶部真实工具栏（Gboard 式）：直接在工具栏上长按拖动排序，
 * 点按移除；下方为可添加工具池，点按卡片即入栏。
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
    // 顶部工具栏条的屏幕区域：编辑态卡片拖入该区域即加入工具栏（Gboard 拖动到顶部栏目）
    var toolbarStripBounds by remember { mutableStateOf(Rect.Zero) }

    val builtin = ToolbarButton.entries.filter {
        isBuiltinToolbarButtonAvailable(
            it,
            state.isSttEnabled,
            { com.kingzcheung.xime.handwriting.HandwritingEngine.hasModel(context) },
            isDebugLogEnabled = SettingsPreferences.isVerboseLoggingEnabled(context),
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
    // 可拖入工具栏的真实按钮 id（内置 + 方案开关 + 插件），系统专属动作不可入栏
    val toolbarableIds = remember(allTools) { allTools.map { it.id }.toSet() }

    val darkLabel = when (state.darkMode) {
        0 -> tr(R.string.s_shensemoshi)
        1 -> tr(R.string.s_qiansemoshi)
        else -> tr(R.string.s_gensuixitong)
    }
    // 候选栏目功能列表：编辑/普通态一致，含方案设置（简/繁等开关）。
    // 基于 availableTools（未入栏）：拖上工具栏少一个、拖下多一个（动态池）。
    // 再附加系统专属动作（调节/深色/部署，不可入栏，常驻）。
    val candidateTools = availableTools + buildList {
        if (!state.isFloatingMode && state.canResizeKeyboard) {
            add(MenuTool("sys_resize", tr(R.string.s_jianpandiaojie), rememberVectorPainter(ToolbarButton.EDIT.icon), action = callbacks.onKeyboardResize))
        }
        add(MenuTool("sys_dark", darkLabel, rememberVectorPainter(ToolbarButton.SYMBOL.icon), action = callbacks.onToggleDarkMode))
        add(MenuTool("sys_deploy", tr(R.string.s_bushufangan), rememberVectorPainter(ToolbarButton.SCHEMA.icon), action = callbacks.onReloadConfig))
    }

    BoxWithConstraints(modifier = modifier.fillMaxWidth().background(state.backgroundColor)) {
        val side = if (maxWidth >= WIDE_CONTAINER_WIDTH) 50.dp else 16.dp
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = 8.dp)) {
            // ===== 顶部工具栏（普通态与编辑态同一条，Gboard 式直接在栏上编辑）=====
            ToolbarStrip(
                editing = editing,
                activeTools = activeTools,
                allTools = allTools,
                draft = draft,
                onDraftChange = { draft = it },
                textColor = textColor,
                cardColor = cardColor,
                onDismiss = { callbacks.onDismiss() },
                onFinishEdit = {
                    callbacks.onUpdateToolbarButtons(draft)
                    editing = false
                },
                onSchemaList = { callbacks.onSchemaList() },
                onStripBounds = { toolbarStripBounds = it },
            )

            if (editing) {
                ToolbarEditBody(
                    candidateTools = candidateTools,
                    toolbarableIds = toolbarableIds,
                    textColor = textColor,
                    cardColor = cardColor,
                    draft = draft,
                    onDraftChange = { draft = it },
                    toolbarStripBounds = toolbarStripBounds,
                    onDone = {
                        callbacks.onUpdateToolbarButtons(draft)
                        editing = false
                    },
                )
            } else {
                ToolbarNormalBody(
                    candidateTools = candidateTools,
                    textColor = textColor,
                    cardColor = cardColor,
                    onEdit = { draft = state.toolbarButtons; editing = true },
                )
            }
        }
    }
}

/**
 * 顶部工具栏条：左侧返回/收起，中间已启用工具（编辑态可长按拖动排序、点按移除），
 * 右侧方案切换。编辑态保留同一工具栏本体，与 Gboard 的定制交互一致。
 */
@Composable
private fun ToolbarStrip(
    editing: Boolean,
    activeTools: List<MenuTool>,
    allTools: List<MenuTool>,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit,
    textColor: Color,
    cardColor: Color,
    onDismiss: () -> Unit,
    onFinishEdit: () -> Unit,
    onSchemaList: () -> Unit,
    onStripBounds: (Rect) -> Unit = {},
) {
    val positions = remember { mutableStateMapOf<Int, Offset>() }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    val threshold = with(LocalDensity.current) { 40.dp.toPx() }
    val removeDropPx = with(LocalDensity.current) { 90.dp.toPx() }
    // 手势进行中保持读取最新 draft，避免闭包捕获旧列表导致越界/闪退
    val currentDraft by rememberUpdatedState(draft)

    Row(
        Modifier.fillMaxWidth().height(52.dp).onGloballyPositioned { c ->
            onStripBounds(c.boundsInRoot())
        },
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CircleAction(
            icon = if (editing) Icons.AutoMirrored.Filled.ArrowBack else Icons.Default.Close,
            tint = textColor,
            bg = cardColor,
            label = if (editing) tr(R.string.s_wancheng) else tr(R.string.s_quxiao),
            size = 24.dp,
        ) {
            if (editing) onFinishEdit() else onDismiss()
        }
        // 顶部工具按钮平均间隔排列（Gboard 式），不滚动，固定按钮均匀分布
        Row(
            Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val ids = if (editing) draft else activeTools.map { it.id }
            ids.forEachIndexed { slot, id ->
                val tool = allTools.firstOrNull { it.id == id } ?: return@forEachIndexed
                if (editing) {
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(40.dp)
                            .onGloballyPositioned { c -> positions[slot] = c.positionInRoot() + Offset(c.size.width / 2f, c.size.height / 2f) }
                            .graphicsLayer {
                                if (slot == dragSlot) {
                                    translationX = dragOffset.x
                                    translationY = dragOffset.y
                                    scaleX = 1.15f
                                    scaleY = 1.15f
                                    val alpha = if (dragOffset.y > removeDropPx * 0.6f) 0.4f else 1f
                                    this.alpha = alpha
                                }
                            }
                            .pointerInput(tool.id) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = { dragSlot = slot; dragOffset = Offset.Zero },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val center = positions[dragSlot]?.plus(dragOffset) ?: return@detectDragGesturesAfterLongPress
                                        // 下拉出栏一定距离即移除（越界保护：草稿在手势中可能已变化）
                                        if (dragOffset.y > removeDropPx) {
                                            onDraftChange(currentDraft.filter { it != id })
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
                                        if (target >= 0 && best < threshold &&
                                            dragSlot in currentDraft.indices && target in currentDraft.indices
                                        ) {
                                            val mutable = currentDraft.toMutableList()
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
                            .clip(CircleShape)
                            .background(cardColor)
                            .clickable { onDraftChange(draft - id) },
                        contentAlignment = Alignment.Center,
                    ) {
                        ToolIcon(tool, textColor, 22.dp)
                    }
                } else {
                    Box(Modifier.padding(horizontal = 2.dp)) {
                        CircleTool(tool, textColor, cardColor)
                    }
                }
            }
        }
        CircleAction(
            icon = Icons.TwoTone.KeyboardAlt,
            tint = textColor,
            bg = cardColor,
            label = tr(R.string.s_shurufangan),
            size = 22.dp,
        ) { onSchemaList() }
    }
}

/** 编辑态主体：Gboard 提示文案 + 候选功能 grid 两列多行（点按或拖动到顶部工具栏入栏）。 */
@Composable
private fun ToolbarEditBody(
    candidateTools: List<MenuTool>,
    toolbarableIds: Set<String>,
    textColor: Color,
    cardColor: Color,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit,
    toolbarStripBounds: Rect,
    onDone: () -> Unit,
) {
    // 拖动卡片叠加层状态：拖动时卡片跨越布局（不被滚动区裁剪）悬浮到顶部工具栏
    var dragTool by remember { mutableStateOf<MenuTool?>(null) }
    var dragCenter by remember { mutableStateOf(Offset.Zero) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

    Box(Modifier.fillMaxSize().onGloballyPositioned { c ->
        val b = c.boundsInRoot()
        overlayOrigin = Offset(b.left, b.top)
    }) {
        Column(Modifier.fillMaxSize()) {
            Text(
                tr(R.string.s_tuodonggexiangyitianjiahuochongxinpaiuxu),
                color = textColor.copy(alpha = .55f),
                fontSize = 12.sp,
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                textAlign = TextAlign.Center,
                maxLines = 1,
            )
            // 编辑态：候选栏目 grid 两列多行（纵向滚动），卡片可像纸牌一样拖动摆放。
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                candidateTools.chunked(2).forEach { rowTools ->
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rowTools.forEach { tool ->
                            DraggableToolCard(
                                tool = tool,
                                textColor = textColor,
                                cardColor = cardColor,
                                toolbarStripBounds = toolbarStripBounds,
                                modifier = Modifier.weight(1f).height(64.dp),
                                onTap = {
                                    if (tool.id in toolbarableIds && tool.id !in draft) {
                                        onDraftChange(addToToolbarFifo(draft, tool.id))
                                    }
                                },
                                onDragIntoToolbar = {
                                    if (tool.id in toolbarableIds && tool.id !in draft) {
                                        onDraftChange(addToToolbarFifo(draft, tool.id))
                                    }
                                },
                                onDragMove = { center, isDragging ->
                                    dragCenter = center
                                    dragTool = if (isDragging) tool else null
                                },
                            )
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
            Box(
                Modifier.fillMaxWidth(.5f).height(40.dp).clip(RoundedCornerShape(20.dp)).background(cardColor).clickable { onDone() },
                contentAlignment = Alignment.Center,
            ) {
                Text(tr(R.string.s_wancheng), color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
        }

        // 跨越布局的拖动卡片叠加层：卡片悬浮跟随手指，可越过滚动区放到顶部工具栏
        dragTool?.let { tool ->
            var cardSize by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
            Box(
                Modifier
                    .onSizeChanged { cardSize = it }
                    .offset {
                        androidx.compose.ui.unit.IntOffset(
                            (dragCenter.x - overlayOrigin.x - cardSize.width / 2).toInt(),
                            (dragCenter.y - overlayOrigin.y - cardSize.height / 2).toInt(),
                        )
                    }
                    .graphicsLayer {
                        shadowElevation = 24f
                        shape = RoundedCornerShape(24.dp)
                        clip = true
                    },
            ) {
                ToolCard(tool, textColor, cardColor, Modifier.width(220.dp).height(64.dp))
            }
        }
    }
}

/**
 * 普通态主体：两页排版（左右滑动翻页），每页 2 列 × 3 行，按钮大小统一一致 + 右下角铅笔进入定制。
 * 展示与编辑态一致的功能列表（含方案设置）。编辑态则为两列多行 grid（见 ToolbarEditBody）。
 */
@Composable
private fun ToolbarNormalBody(
    candidateTools: List<MenuTool>,
    textColor: Color,
    cardColor: Color,
    onEdit: () -> Unit,
) {
    // 每页 2 列 × 3 行 = 6 个；HorizontalPager 横向翻页（Gboard 式翻页效果）
    val pageSize = 6
    val pages = candidateTools.chunked(pageSize)
    val pagerState = rememberPagerState(pageCount = { pages.size.coerceAtLeast(1) })
    Column(Modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            pageSpacing = 0.dp,
        ) { pageIndex ->
            val pageTools = pages.getOrNull(pageIndex) ?: emptyList()
            Column(
                Modifier.fillMaxHeight().padding(horizontal = 4.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                pageTools.chunked(2).forEach { rowTools ->
                    // 每行等分高度（weight）→ 三行按钮大小平均，避免最后一行被裁小
                    Row(
                        Modifier.fillMaxWidth().weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        rowTools.forEach { tool ->
                            ToolCard(tool, textColor, cardColor, Modifier.weight(1f).fillMaxHeight())
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
        }
        // 底部：页码圆点居中，编辑入口为右下角描边胶囊（Gboard 工具栏样式）
        Box(Modifier.fillMaxWidth().padding(top = 6.dp)) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                repeat(pages.size.coerceAtLeast(1)) { index ->
                    val active = index == pagerState.currentPage
                    Box(
                        modifier = Modifier
                            .size(if (active) 8.dp else 6.dp)
                            .clip(CircleShape)
                            .background(textColor.copy(alpha = if (active) 0.9f else 0.3f))
                    )
                }
            }
            val editShape = RoundedCornerShape(50)
            Row(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .clip(editShape)
                    .border(1.dp, textColor.copy(alpha = 0.5f), editShape)
                    .clickable { onEdit() }
                    .padding(horizontal = 22.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.TwoTone.Edit,
                    contentDescription = tr(R.string.s_dingzhigongjulan),
                    tint = textColor,
                    modifier = Modifier.size(22.dp),
                )
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

/**
 * 工具图标统一渲染：文字图标与矢量图标统一居中在同一 size 方框内（视觉等大、对齐一致），
 * 修复不同按钮图标大小不一、错位问题。
 */
@Composable
private fun ToolIcon(tool: MenuTool, textColor: Color, size: Dp) {
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (tool.textIcon != null) {
            // 文字图标字号随图标框尺寸缩放，与矢量图标视觉等大、居中对齐
            Text(
                tool.textIcon,
                color = textColor.copy(alpha = .82f),
                fontSize = (size.value * 0.82f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                textAlign = TextAlign.Center,
            )
        } else if (tool.icon != null) {
            Icon(tool.icon, tool.label, tint = textColor.copy(alpha = .82f), modifier = Modifier.fillMaxSize())
        }
    }
}

@Composable
private fun ToolCard(tool: MenuTool, textColor: Color, cardColor: Color, modifier: Modifier = Modifier, onClick: () -> Unit = tool.action) {
    Row(
        modifier = modifier
            .clip(RoundedCornerShape(34.dp))
            .background(cardColor.copy(alpha = 0.88f))
            .clickable { onClick() }
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        ToolIcon(tool, textColor, 30.dp)
        Text(tool.label, color = textColor, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
        Icon(Icons.Default.ChevronRight, tr(R.string.s_xiayibu), tint = textColor.copy(alpha = .72f), modifier = Modifier.size(24.dp))
    }
}

/**
 * 可拖动工具卡：点按入栏；长按拖动跟随手指，拖入顶部工具栏区域即加入工具栏（Gboard 拖动到顶部栏目）。
 * 拖动时卡片轻微放大、阴影浮现，松手或拖出即复位。
 */
@Composable
private fun DraggableToolCard(
    tool: MenuTool,
    textColor: Color,
    cardColor: Color,
    toolbarStripBounds: Rect,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDragIntoToolbar: () -> Unit,
    onDragMove: (center: Offset, isDragging: Boolean) -> Unit = { _, _ -> },
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var cardRootCenter by remember { mutableStateOf(Offset.Zero) }
    var added by remember { mutableStateOf(false) }

    Box(
        modifier = modifier
            .onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                cardRootCenter = Offset((b.left + b.right) / 2f, (b.top + b.bottom) / 2f)
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                // 纸牌式拖动：抬起放大 + 阴影浮现 + 轻微倾斜（像拿一张牌）
                val lift = if (dragging) 1.08f else 1f
                scaleX = lift
                scaleY = lift
                // 按横向位移轻微倾斜，模拟手持纸牌的自然角度
                rotationZ = if (dragging) (dragOffset.x / 40f).coerceIn(-8f, 8f) else 0f
                alpha = if (dragging) 0.97f else 1f
                // 抬起时投影，营造纸牌悬浮在页面上的立体感
                shadowElevation = if (dragging) 18f else 0f
                shape = RoundedCornerShape(24.dp)
                clip = dragging
            }
            .pointerInput(tool.id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = {
                        dragging = true
                        added = false
                        dragOffset = Offset.Zero
                        onDragMove(cardRootCenter, true)
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragOffset += amount
                        val center = cardRootCenter + dragOffset
                        onDragMove(center, true)
                        // 拖入顶部工具栏区域 → 入栏（Gboard：拖到顶部栏目）
                        if (!added &&
                            toolbarStripBounds != Rect.Zero &&
                            center.y <= toolbarStripBounds.bottom &&
                            center.x >= toolbarStripBounds.left &&
                            center.x <= toolbarStripBounds.right
                        ) {
                            added = true
                            onDragIntoToolbar()
                        }
                    },
                    onDragEnd = {
                        dragging = false
                        dragOffset = Offset.Zero
                        onDragMove(Offset.Zero, false)
                    },
                    onDragCancel = {
                        dragging = false
                        dragOffset = Offset.Zero
                        onDragMove(Offset.Zero, false)
                    },
                )
            }
    ) {
        ToolCard(tool, textColor, cardColor, Modifier.fillMaxSize(), onClick = onTap)
    }
}
