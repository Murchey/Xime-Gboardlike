package com.kingzcheung.xime.ui.emoji

import com.kingzcheung.xime.R
import com.kingzcheung.xime.data.EmojiData
import com.kingzcheung.xime.data.EmojiSearch
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.ui.keyboard.EmojiCategoryTab
import com.kingzcheung.xime.ui.keyboard.PluginEmojiButton
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as rowItems
import androidx.compose.foundation.lazy.itemsIndexed as rowItemsIndexed
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** 搜索态面板高度（dp）：收成键盘上方一条，留搜索栏 + 两行结果。 */
internal const val EMOJI_PANEL_SEARCH_HEIGHT_DP = 180

/** 浏览态面板占屏高的比例：首次打开半屏展开，键盘仍在下方可用。 */
internal const val EMOJI_PANEL_BROWSE_HEIGHT_RATIO = 0.38f

/** 浏览态面板高度下限（dp），小屏横屏时别压没。 */
internal const val EMOJI_PANEL_MIN_HEIGHT_DP = 200

/** 宿主输入框至少要留在屏幕上的高度（dp），面板不得把它挤到屏幕外。 */
internal const val EMOJI_HOST_INPUT_RESERVE_DP = 120

/**
 * 计算浏览态面板高度。
 *
 * 屏高扣掉键盘、再扣掉留给宿主输入框的一条，剩下的才是面板能占的空间；
 * 期望值超过这个预算就按预算走，避免"面板半屏很好看但输入框被顶出屏幕"。
 * 预算低于 [minHeightDp] 时仍取 minHeightDp——面板太矮没法用，宁可让输入框贴边。
 */
internal fun emojiPanelBrowseHeightDp(
    desiredDp: Int,
    screenHeightDp: Int,
    keyboardHeightDp: Int,
    minHeightDp: Int,
): Int {
    val budget = screenHeightDp - keyboardHeightDp - EMOJI_HOST_INPUT_RESERVE_DP
    return desiredDp.coerceAtMost(budget.coerceAtLeast(minHeightDp))
}

/**
 * 键盘上方的表情面板（与快捷发送表单同构，不是独立窗口）。
 *
 * 不做独立 Activity 的原因：独立窗口会接管输入连接，系统不再给宿主应用做
 * ADJUST_RESIZE，宿主输入框必被面板盖住。面板放进输入法窗口后，窗口高度增加
 * 即上报更大的 IME inset，宿主输入框自然被顶到面板上沿之上。
 *
 * 搜索框不是 EditText：输入法窗口里的 EditText 会抢走输入连接、把宿主输入框
 * 挤到不可见。查询词由键盘按键注入 [EmojiSearchInput]，键盘保持可见可点。
 */
@Composable
fun EmojiPanelArea(
    backgroundColor: Color,
    textColor: Color,
    accentColor: Color,
    cardColor: Color,
    /** 面板高度（dp）：与容器撑高同源，浏览态整块、搜索态一条。 */
    browseHeightDp: Int,
    onSelect: (String) -> Unit,
    onImageSelect: (String) -> Unit,
    onDelete: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val categories by ExtensionManager.emojiCategoriesFlow.collectAsStateWithLifecycle()
    val builtins = remember(categories) {
        categories.filterNot { it.isPlugin }.ifEmpty { EmojiData.categories }
    }
    var dictionary by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    LaunchedEffect(Unit) {
        dictionary = withContext(Dispatchers.IO) { EmojiSearch.loadDictionary(context) }
    }
    var recent by remember {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_EMOJIS))
    }
    val pluginGroups = remember(categories) {
        categories.filter { it.isPlugin }.groupBy { it.pluginId ?: it.name }
    }
    val pluginNames = remember(categories) {
        ExtensionManager.getAllInstalledPlugins().associate { it.id to it.name }
    }
    var selectedPlugin by remember { mutableStateOf<String?>(null) }
    val activePlugin = selectedPlugin?.takeIf { it in pluginGroups }
    val pageCategories = if (activePlugin == null) builtins else pluginGroups[activePlugin].orEmpty()
    val query = EmojiSearchInput.query
    val searching = EmojiSearchInput.searchMode
    var results by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(query, builtins, dictionary) {
        results = if (query.isBlank()) emptyList()
        else withContext(Dispatchers.Default) { EmojiSearch.search(query, builtins, dictionary) }
    }
    var selectedBookmark by remember(activePlugin, pageCategories) { mutableIntStateOf(0) }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val sectionStarts = remember(activePlugin, pageCategories, recent) {
        EmojiPickerIndex.sectionStarts(
            categoryItemCounts = pageCategories.map { category ->
                if (activePlugin == null) category.emojis.size else category.emojiItems.orEmpty().size
            },
            recentCount = if (activePlugin == null) recent.size else null,
            emptyCategoryPlaceholder = activePlugin != null,
        )
    }
    LaunchedEffect(activePlugin, searching) { gridState.scrollToItem(0) }
    LaunchedEffect(gridState, searching, sectionStarts) {
        if (!searching) {
            snapshotFlow { gridState.firstVisibleItemIndex }.collectLatest { first ->
                selectedBookmark = EmojiPickerIndex.selectedSection(sectionStarts, first)
            }
        }
    }
    fun selectEmoji(emoji: String) {
        recent = RecentUsageStore.record(context, RecentUsageStore.KEY_RECENT_EMOJIS, emoji)
        onSelect(emoji)
    }

    val searchMode = EmojiSearchInput.searchMode

    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(browseHeightDp.dp)
            .background(cardColor),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 8.dp, top = 4.dp, bottom = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(
                // 返回先退出搜索态回到半屏浏览，已在浏览态才关面板
                onClick = { if (searching) EmojiSearchInput.exitSearch() else onClose() },
                modifier = Modifier.size(36.dp),
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr(R.string.s_fanhui),
                    tint = textColor,
                    modifier = Modifier.size(20.dp),
                )
            }
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(32.dp)
                    .background(textColor.copy(alpha = 0.08f), RoundedCornerShape(16.dp))
                    .clickable { EmojiSearchInput.enterSearch() }
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Default.Search,
                    contentDescription = null,
                    // 搜索态图标高对比，提示当前按键进的是搜索框
                    tint = textColor.copy(alpha = if (searching) 0.9f else 0.45f),
                    modifier = Modifier.size(15.dp),
                )
                Text(
                    text = if (searching) query else tr(R.string.s_sousuo),
                    color = if (searching) textColor else textColor.copy(alpha = 0.45f),
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 6.dp)
                        .semantics { contentDescription = tr(R.string.s_sousuo) },
                )
                if (query.isNotEmpty()) {
                    Box(
                        Modifier.size(22.dp).clickable { EmojiSearchInput.query = "" },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = null,
                            tint = textColor.copy(alpha = 0.5f),
                            modifier = Modifier.size(14.dp),
                        )
                    }
                }
            }
        }
        if (!searching) {
            LazyRow(
                modifier = Modifier.fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (activePlugin == null) item {
                    EmojiCategoryTab(
                        icon = "🕘",
                        pluginIcon = null,
                        isSelected = selectedBookmark == 0,
                        onClick = {
                            selectedBookmark = 0
                            scope.launch { gridState.scrollToItem(sectionStarts.getOrElse(0) { 0 }) }
                        },
                        backgroundColor = Color.Transparent,
                        textColor = textColor,
                        selectedBackgroundColor = accentColor.copy(alpha = 0.3f),
                        modifier = Modifier.size(30.dp),
                    )
                }
                rowItemsIndexed(pageCategories) { index, category ->
                    val target = index + if (activePlugin == null) 1 else 0
                    Box(Modifier.semantics { contentDescription = category.name }) {
                        EmojiCategoryTab(
                            icon = category.icon,
                            pluginIcon = category.pluginIcon,
                            isSelected = selectedBookmark == target,
                            onClick = {
                                selectedBookmark = target
                                scope.launch { gridState.scrollToItem(sectionStarts.getOrElse(target) { 0 }) }
                            },
                            backgroundColor = Color.Transparent,
                            textColor = textColor,
                            selectedBackgroundColor = accentColor.copy(alpha = 0.3f),
                            modifier = Modifier.size(30.dp),
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = textColor.copy(alpha = 0.12f))
        LazyVerticalGrid(
            columns = GridCells.Fixed(8),
            state = gridState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
        ) {
            if (searching) {
                // 刚进搜索态还没输入时提示可打字，不要显示成"无结果"
                if (query.isBlank()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerEmpty(tr(R.string.s_shuruguanjiancisousuo))
                    }
                } else if (results.isEmpty()) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerEmpty(tr(R.string.s_meiyou_sousuo_jieguo))
                    }
                }
                items(results, key = { "s:" + it }) { emoji ->
                    PickerEmoji(emoji, textColor) { selectEmoji(emoji) }
                }
            } else if (activePlugin == null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    PickerHeading(tr(R.string.s_zuijinshiyong), textColor)
                }
                if (recent.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                    PickerEmpty(tr(R.string.s_zanwuzuijinshiyong))
                }
                items(recent, key = { "r:" + it }) { emoji ->
                    PickerEmoji(emoji, textColor) { selectEmoji(emoji) }
                }
                pageCategories.forEachIndexed { index, category ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerHeading(category.name, textColor)
                    }
                    items(category.emojis, key = { "b:$index:$it" }) { emoji ->
                        PickerEmoji(emoji, textColor) { selectEmoji(emoji) }
                    }
                }
            } else {
                pageCategories.forEach { category ->
                    val matches = category.emojiItems.orEmpty()
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerHeading(category.name, textColor)
                    }
                    if (matches.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                        PickerEmpty(tr(R.string.s_zanwuzuijinshiyong))
                    }
                    items(
                        items = matches,
                        key = { "p:" + category.pluginId + ":" + category.name + ":" + it.id },
                    ) { item ->
                        PluginEmojiButton(
                            emojiItem = item,
                            onClick = {
                                val image = item.imageUrl
                                if (image != null) onImageSelect(image)
                                else selectEmoji(item.insertText ?: item.text)
                            },
                            defaultHeightDp = category.layoutItemHeightDp.coerceIn(40, 120),
                            backgroundColor = Color.Transparent,
                            textColor = textColor,
                            modifier = Modifier.fillMaxWidth().padding(2.dp),
                        )
                    }
                }
            }
        }
        // 搜索态收成一条：分类栏与底部操作栏都不显示，高度只够搜索栏
        if (!searching) {
        HorizontalDivider(color = textColor.copy(alpha = 0.12f))
        Row(
            modifier = Modifier.fillMaxWidth().height(38.dp).padding(horizontal = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = activePlugin == null,
                onClick = { selectedPlugin = null; EmojiSearchInput.query = "" },
                label = { Text("Emoji") },
            )
            LazyRow(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                rowItems(pluginGroups.keys.toList()) { id ->
                    FilterChip(
                        selected = activePlugin == id,
                        onClick = { selectedPlugin = id; EmojiSearchInput.query = "" },
                        label = {
                            Text(pluginNames[id] ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        },
                    )
                }
            }
            IconButton(onClick = onDelete, modifier = Modifier.size(40.dp)) {
                Text("⌫", color = textColor, fontSize = 18.sp)
            }
        }
        }
    }
}

@Composable
private fun PickerHeading(title: String, textColor: Color) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = textColor.copy(alpha = 0.65f),
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

@Composable
private fun PickerEmpty(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 10.dp),
    )
}

@Composable
private fun PickerEmoji(emoji: String, textColor: Color, onClick: () -> Unit) {
    Box(
        Modifier
            .fillMaxWidth()
            .heightIn(min = 40.dp)
            .clickable(onClick = onClick)
            .semantics { contentDescription = emoji },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = 24.sp, color = textColor, maxLines = 1) }
}
