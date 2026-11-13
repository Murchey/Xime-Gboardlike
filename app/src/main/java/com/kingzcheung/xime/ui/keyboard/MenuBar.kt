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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
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

/** 顶部工具栏里「输入方案」槽位的虚拟 id（它不是普通工具栏按钮，单独记位置）。 */
private const val SCHEMA_SLOT_ID = "sys_schema_slot"

/** 把方案槽位插进工具栏按钮序列（slot < 0 表示放末尾）。 */
internal fun withSchemaSlot(draft: List<String>, slot: Int): List<String> {
    val index = if (slot < 0) draft.size else slot.coerceIn(0, draft.size)
    return draft.toMutableList().apply { add(index, SCHEMA_SLOT_ID) }
}

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
    // 有卡片悬停在顶部栏目上方：栏目描边高亮，提示松手即入栏
    var dropActive by remember { mutableStateOf(false) }
    // 顶部各槽位中心（由 ToolbarStrip 实测回传）：外部卡片据此算插入位置
    var slotCenters by remember { mutableStateOf<Map<Int, Offset>>(emptyMap()) }
    // 外部卡片拖进来时的落点（-1 = 不在栏内）
    var externalDropIndex by remember { mutableStateOf(-1) }
    // 拖动中的磁贴：工具 + 手指位置 + 是否大卡片形态
    var dragTool by remember { mutableStateOf<MenuTool?>(null) }
    var dragCenter by remember { mutableStateOf(Offset.Zero) }
    var dragBig by remember { mutableStateOf(true) }
    var overlayOrigin by remember { mutableStateOf(Offset.Zero) }

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

    // 「输入方案」作为可拖动槽位参与排序，位置持久化在 menu_schema_slot
    var schemaSlot by remember { mutableIntStateOf(SettingsPreferences.getMenuSchemaSlot(context)) }
    val schemaTool = MenuTool(
        SCHEMA_SLOT_ID,
        tr(R.string.s_shurufangan),
        rememberVectorPainter(ToolbarButton.SCHEMA.icon),
    ) { callbacks.onSchemaList() }
    val stripTools = remember(allTools, schemaTool) { allTools + schemaTool }
    val slots = remember(draft, schemaSlot) { withSchemaSlot(draft, schemaSlot) }

    // 统一提交：同时写回工具栏按钮与方案按钮位置，保证拖完立刻生效
    fun commitSlots(newSlots: List<String>) {
        val nextDraft = newSlots.filter { it != SCHEMA_SLOT_ID }
        draft = nextDraft
        val nextSlot = newSlots.indexOf(SCHEMA_SLOT_ID).let { if (it < 0) nextDraft.size else it }
        schemaSlot = nextSlot
        SettingsPreferences.setMenuSchemaSlot(context, nextSlot)
    }

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

    val onDragVisual: (MenuTool?, Offset, Boolean) -> Unit = { tool, center, big ->
        dragTool = tool
        dragCenter = center
        dragBig = big
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxWidth()
            .background(state.backgroundColor)
            .onGloballyPositioned { overlayOrigin = it.boundsInRoot().topLeft },
    ) {
        val side = if (maxWidth >= WIDE_CONTAINER_WIDTH) 50.dp else 16.dp
        Column(Modifier.fillMaxSize().padding(horizontal = side, vertical = 8.dp)) {
            // ===== 顶部工具栏（普通态与编辑态同一条，Gboard 式直接在栏上编辑）=====
            ToolbarStrip(
                editing = editing,
                slots = slots,
                allTools = stripTools,
                onSlotsChange = { commitSlots(it) },
                textColor = textColor,
                cardColor = cardColor,
                onDismiss = { callbacks.onDismiss() },
                onFinishEdit = {
                    callbacks.onUpdateToolbarButtons(draft)
                    editing = false
                },
                onSchemaList = { callbacks.onSchemaList() },
                onStripBounds = { toolbarStripBounds = it },
                dropHighlight = dropActive,
                externalDropIndex = if (editing) externalDropIndex else -1,
                onSlotCenters = { slotCenters = it },
                onDragVisual = onDragVisual,
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
                    slotCenters = slotCenters,
                    onHoverStrip = { dropActive = it },
                    onDropIndex = { externalDropIndex = it },
                    onDragVisual = onDragVisual,
                    onDone = {
                        callbacks.onUpdateToolbarButtons(draft)
                        editing = false
                        externalDropIndex = -1
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

        // 拖动磁贴叠加层：栏内画小图标、栏外画大卡片，跨区自动切换形态
        dragTool?.let { tool ->
            DragTileOverlay(
                tool = tool,
                center = dragCenter,
                origin = overlayOrigin,
                big = dragBig,
                textColor = textColor,
                cardColor = cardColor,
            )
        }
    }
}

/**
 * 顶部工具栏条：左侧返回/收起，中间是一排「磁贴槽位」。
 *
 * 方案按钮不再固定在右端，而是作为其中一个槽位参与排序（位置记在 menu_schema_slot）。
 * 编辑态：
 * - 长按任一槽位抬起，横向经过其他槽位即交换，图标一直跟着手指；
 * - 往下拖出栏外，图标就地变成大卡片（松手即移出工具栏回到下方功能池）；
 * - 外部卡片拖进来时按落点插入，栏内用虚线圈占位预览。
 */
@Composable
private fun ToolbarStrip(
    editing: Boolean,
    slots: List<String>,
    allTools: List<MenuTool>,
    onSlotsChange: (List<String>) -> Unit,
    textColor: Color,
    cardColor: Color,
    onDismiss: () -> Unit,
    onFinishEdit: () -> Unit,
    onSchemaList: () -> Unit,
    onStripBounds: (Rect) -> Unit = {},
    dropHighlight: Boolean = false,
    externalDropIndex: Int = -1,
    onSlotCenters: (Map<Int, Offset>) -> Unit = {},
    onDragVisual: (MenuTool?, Offset, Boolean) -> Unit = { _, _, _ -> },
) {
    val positions = remember { mutableStateMapOf<Int, Offset>() }
    var dragSlot by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var outOfStrip by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val threshold = with(density) { 40.dp.toPx() }
    val outThreshold = with(density) { 64.dp.toPx() }
    val currentSlots by rememberUpdatedState(slots)
    val haptics = LocalHapticFeedback.current

    // 槽位中心同步给外部（下方卡片据此算插入位置）
    LaunchedEffect(slots.size, positions.size) { onSlotCenters(positions.toMap()) }

    Row(
        Modifier.fillMaxWidth().height(52.dp)
            .then(
                if (dropHighlight) {
                    Modifier.border(1.5.dp, textColor.copy(alpha = .55f), RoundedCornerShape(26.dp))
                } else Modifier
            )
            .onGloballyPositioned { c -> onStripBounds(c.boundsInRoot()) },
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
        Row(
            Modifier.weight(1f).padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            slots.forEachIndexed { slot, id ->
                // 外部拖入的落点占位：虚线圈提示"松手会插在这里"
                if (slot == externalDropIndex) DropGhost(textColor)
                val tool = allTools.firstOrNull { it.id == id } ?: return@forEachIndexed
                if (editing) {
                    Box(
                        Modifier
                            .padding(horizontal = 2.dp)
                            .size(40.dp)
                            .onGloballyPositioned { c ->
                                positions[slot] = c.positionInRoot() +
                                    Offset(c.size.width / 2f, c.size.height / 2f)
                            }
                            .graphicsLayer {
                                // 拖动中的槽位本体隐藏：视觉由根层叠加的"磁贴"承担
                                alpha = if (slot == dragSlot) 0f else 1f
                            }
                            .clip(CircleShape)
                            .background(cardColor)
                            .clickable {
                                if (id == SCHEMA_SLOT_ID) onSchemaList() else onSlotsChange(slots - id)
                            }
                            // 拖动手势放最内层，避免按下事件被 clickable 抢走
                            .pointerInput(id, slots) {
                                detectDragGesturesAfterLongPress(
                                    onDragStart = {
                                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                        dragSlot = slot
                                        dragOffset = Offset.Zero
                                        outOfStrip = false
                                        positions[slot]?.let { onDragVisual(tool, it, false) }
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        dragOffset += amount
                                        val from = dragSlot
                                        val base = positions[from] ?: return@detectDragGesturesAfterLongPress
                                        val dragged = base + dragOffset
                                        // 拖出栏外：就地变回大卡片，松手即移出工具栏
                                        val nowOut = dragOffset.y > outThreshold
                                        if (nowOut != outOfStrip) outOfStrip = nowOut
                                        onDragVisual(tool, dragged, nowOut)
                                        if (nowOut) return@detectDragGesturesAfterLongPress
                                        var target = -1
                                        var best = Float.MAX_VALUE
                                        positions.forEach { (i, p) ->
                                            if (i == from) return@forEach
                                            val d = (p - dragged).getDistance()
                                            if (d < best) { best = d; target = i }
                                        }
                                        if (target >= 0 && best < threshold &&
                                            from in currentSlots.indices && target in currentSlots.indices
                                        ) {
                                            val toCenter = positions[target]
                                            val mutable = currentSlots.toMutableList()
                                            val moved = mutable.removeAt(from)
                                            mutable.add(target, moved)
                                            onSlotsChange(mutable)
                                            // 扣掉新旧槽位中心差，图标留在手指下，才能一路拖过去
                                            if (toCenter != null) {
                                                dragOffset = adjustDragOffsetAfterSwap(dragOffset, base, toCenter)
                                            }
                                            dragSlot = target
                                        }
                                    },
                                    onDragEnd = {
                                        val dragged = currentSlots.getOrNull(dragSlot)
                                        // 方案按钮是系统动作，拖出去也不移除，只回弹
                                        if (outOfStrip && dragged != null && dragged != SCHEMA_SLOT_ID) {
                                            onSlotsChange(currentSlots.filter { it != dragged })
                                        }
                                        dragSlot = -1
                                        dragOffset = Offset.Zero
                                        outOfStrip = false
                                        onDragVisual(null, Offset.Zero, false)
                                    },
                                    onDragCancel = {
                                        dragSlot = -1
                                        dragOffset = Offset.Zero
                                        outOfStrip = false
                                        onDragVisual(null, Offset.Zero, false)
                                    },
                                )
                            },
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
            if (externalDropIndex == slots.size) DropGhost(textColor)
        }
    }
}

/** 外部卡片拖入时的落点占位虚线圈。 */
@Composable
private fun DropGhost(textColor: Color) {
    Box(
        Modifier.padding(horizontal = 2.dp).size(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(34.dp)
                .clip(CircleShape)
                .border(1.5.dp, textColor.copy(alpha = .45f), CircleShape)
        )
    }
}

/** 编辑态主体：Gboard 提示文案 + 候选功能 grid 两列多行（点按加入工具栏，拖到上方即插入该位置）。 */
@Composable
private fun ToolbarEditBody(
    candidateTools: List<MenuTool>,
    toolbarableIds: Set<String>,
    textColor: Color,
    cardColor: Color,
    draft: List<String>,
    onDraftChange: (List<String>) -> Unit,
    toolbarStripBounds: Rect,
    slotCenters: Map<Int, Offset>,
    onHoverStrip: (Boolean) -> Unit = {},
    onDropIndex: (Int) -> Unit = {},
    onDragVisual: (MenuTool?, Offset, Boolean) -> Unit = { _, _, _ -> },
    onDone: () -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
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
                                slotCenters = slotCenters,
                                modifier = Modifier.weight(1f).height(64.dp),
                                onTap = {
                                    if (tool.id in toolbarableIds && tool.id !in draft) {
                                        onDraftChange(addToToolbarFifo(draft, tool.id))
                                    }
                                },
                                onDragIntoToolbar = { index ->
                                    if (tool.id in toolbarableIds && tool.id !in draft) {
                                        onDraftChange(
                                            draft.toMutableList().apply {
                                                add(index.coerceIn(0, size), tool.id)
                                            }.let { list ->
                                                // 顶部最多 TOOLBAR_MAX_ITEMS 个，超出挤掉最早的
                                                while (list.size > TOOLBAR_MAX_ITEMS) list.removeAt(0)
                                                list.toList()
                                            }
                                        )
                                    }
                                },
                                onDragMove = { center, isDragging ->
                                    if (!isDragging) {
                                        onDragVisual(null, Offset.Zero, false)
                                        onDropIndex(-1)
                                    } else {
                                        // 悬停在顶部栏目上方时，卡片变形成小图标并算插入位置
                                        val over = toolbarStripBounds != Rect.Zero &&
                                            center.y <= toolbarStripBounds.bottom &&
                                            center.x >= toolbarStripBounds.left &&
                                            center.x <= toolbarStripBounds.right
                                        onDragVisual(tool, center, !over)
                                        onDropIndex(if (over) dropIndexFor(center.x, slotCenters) else -1)
                                    }
                                },
                                onHoverStrip = onHoverStrip,
                            )
                        }
                        if (rowTools.size == 1) Spacer(Modifier.weight(1f))
                    }
                }
            }
            Row(Modifier.fillMaxWidth().padding(top = 6.dp), horizontalArrangement = Arrangement.Center) {
                Box(
                    Modifier
                        .fillMaxWidth(.5f)
                        .height(40.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(cardColor)
                        .clickable { onDone() },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(tr(R.string.s_wancheng), color = textColor, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(6.dp))
        }
    }
}

/**
 * 由落点横坐标算插入下标：落在某槽位中心左侧就插到它前面。
 *
 * 槽位中心来自顶部横排的实测位置，因此拖到两个图标之间会插在中间，
 * 而不是像 FIFO 那样一律追加到末尾。
 */
internal fun dropIndexFor(x: Float, centers: Map<Int, Offset>): Int {
    if (centers.isEmpty()) return 0
    for ((index, center) in centers.toList().sortedBy { it.first }) {
        if (x < center.x) return index
    }
    return centers.keys.max() + 1
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
 * 可拖动工具卡：点按加入栏尾；长按拖动跟手。
 *
 * 拖到顶部栏目上方时卡片会就地「变小图标」，落点决定插入位置，
 * 松手才真正插入；拖回下方则恢复成大卡片。
 */
@Composable
private fun DraggableToolCard(
    tool: MenuTool,
    textColor: Color,
    cardColor: Color,
    toolbarStripBounds: Rect,
    slotCenters: Map<Int, Offset>,
    modifier: Modifier = Modifier,
    onTap: () -> Unit,
    onDragIntoToolbar: (Int) -> Unit,
    onDragMove: (center: Offset, isDragging: Boolean) -> Unit = { _, _ -> },
    onHoverStrip: (Boolean) -> Unit = {},
) {
    var dragOffset by remember { mutableStateOf(Offset.Zero) }
    var dragging by remember { mutableStateOf(false) }
    var cardRootCenter by remember { mutableStateOf(Offset.Zero) }
    var added by remember { mutableStateOf(false) }
    // 是否正悬停在顶部栏目上方：拖动中只做高亮预览，松手才真正入栏（磁贴手感）
    var overStrip by remember { mutableStateOf(false) }
    var dropIndex by remember { mutableStateOf(-1) }

    Box(
        modifier = modifier
            .onGloballyPositioned { c ->
                val b = c.boundsInRoot()
                cardRootCenter = Offset((b.left + b.right) / 2f, (b.top + b.bottom) / 2f)
            }
            .graphicsLayer {
                translationX = dragOffset.x
                translationY = dragOffset.y
                // 拖到上方栏目后本体隐藏（改由叠加层画小图标），避免"大卡片悬在栏上"
                val lift = if (dragging) 1.08f else 1f
                scaleX = lift
                scaleY = lift
                rotationZ = if (dragging && !overStrip) (dragOffset.x / 40f).coerceIn(-8f, 8f) else 0f
                alpha = if (dragging && overStrip) 0f else if (dragging) 0.97f else 1f
                shadowElevation = if (dragging && !overStrip) 18f else 0f
                shape = RoundedCornerShape(24.dp)
                clip = dragging && !overStrip
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
                        val over = toolbarStripBounds != Rect.Zero &&
                            center.y <= toolbarStripBounds.bottom &&
                            center.x >= toolbarStripBounds.left &&
                            center.x <= toolbarStripBounds.right
                        if (over != overStrip) {
                            overStrip = over
                            onHoverStrip(over)
                        }
                        dropIndex = if (over) dropIndexFor(center.x, slotCenters) else -1
                    },
                    onDragEnd = {
                        // 松手时才吸附：悬停在栏目上方就插到当前落点
                        if (!added && overStrip) {
                            added = true
                            onDragIntoToolbar(dropIndex.coerceAtLeast(0))
                        }
                        overStrip = false
                        dropIndex = -1
                        onHoverStrip(false)
                        dragging = false
                        dragOffset = Offset.Zero
                        onDragMove(Offset.Zero, false)
                    },
                    onDragCancel = {
                        overStrip = false
                        dropIndex = -1
                        onHoverStrip(false)
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

/** 拖动中的磁贴叠加层：栏内是小图标，栏外是大卡片，跨区时形态自动切换。 */
@Composable
private fun DragTileOverlay(
    tool: MenuTool,
    center: Offset,
    origin: Offset,
    big: Boolean,
    textColor: Color,
    cardColor: Color,
) {
    var size by remember { mutableStateOf(androidx.compose.ui.unit.IntSize.Zero) }
    val width = if (big) 220.dp else 44.dp
    val height = if (big) 64.dp else 44.dp
    Box(
        Modifier
            .onSizeChanged { size = it }
            .offset {
                androidx.compose.ui.unit.IntOffset(
                    (center.x - origin.x - size.width / 2).toInt(),
                    (center.y - origin.y - size.height / 2).toInt(),
                )
            }
            .graphicsLayer {
                shadowElevation = if (big) 24f else 12f
                scaleX = if (big) 1f else 1.12f
                scaleY = if (big) 1f else 1.12f
                shape = if (big) RoundedCornerShape(24.dp) else CircleShape
                clip = true
            },
        contentAlignment = Alignment.Center,
    ) {
        if (big) {
            ToolCard(tool, textColor, cardColor, Modifier.width(width).height(height))
        } else {
            Box(
                Modifier.size(44.dp).clip(CircleShape).background(cardColor),
                contentAlignment = Alignment.Center,
            ) {
                ToolIcon(tool, textColor, 22.dp)
            }
        }
    }
}


/**
 * 交换两个槽位后修正拖动偏移，保证被拖图标仍停在手指下。
 *
 * 视觉位置 = 槽位中心 + 偏移。交换后槽位中心从 [fromCenter] 变成 [toCenter]，
 * 若不补偿，图标会瞬间跳走，后续移动检测不到邻近槽位——
 * 表现就是长按后「一次只能往前/往后挪一格」。
 */
internal fun adjustDragOffsetAfterSwap(
    dragOffset: Offset,
    fromCenter: Offset,
    toCenter: Offset,
): Offset = dragOffset - (toCenter - fromCenter)
