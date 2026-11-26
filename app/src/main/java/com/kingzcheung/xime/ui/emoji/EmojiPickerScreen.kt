package com.kingzcheung.xime.ui.emoji

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.TextUnit
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.data.EmojiSearch
import com.kingzcheung.xime.data.EmojiData
import com.kingzcheung.xime.data.RecentUsageStore
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.ui.keyboard.EmojiCategoryTab
import com.kingzcheung.xime.ui.keyboard.PluginEmojiButton

/** Activity 内的表情浏览页；文字与图片上屏交给宿主处理。 */
@Composable
fun EmojiPickerScreen(
    onSelect: (String) -> Unit,
    onImageSelect: (String) -> Unit,
    onBack: () -> Unit,
    onDelete: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    var searchFocused by remember { mutableStateOf(false) }
    val colors = MaterialTheme.colorScheme
    val categories by ExtensionManager.emojiCategoriesFlow.collectAsStateWithLifecycle()
    val builtins = remember(categories) { categories.filterNot { it.isPlugin }.ifEmpty { EmojiData.categories } }
    var emojiDictionary by remember { mutableStateOf<Map<String, Set<String>>>(emptyMap()) }
    var searchResults by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(builtins) {
        emojiDictionary = withContext(Dispatchers.IO) { EmojiSearch.loadDictionary(context) }
    }
    val pluginGroups = remember(categories) {
        categories.filter { it.isPlugin }.groupBy { it.pluginId ?: it.name }
    }
    val pluginNames = remember(categories) {
        ExtensionManager.getAllInstalledPlugins().associate { it.id to it.name }
    }
    var selectedPlugin by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var recent by remember(context) {
        mutableStateOf(RecentUsageStore.get(context, RecentUsageStore.KEY_RECENT_EMOJIS))
    }
    val activePlugin = selectedPlugin?.takeIf { it in pluginGroups }
    val pageCategories = if (activePlugin == null) builtins else pluginGroups[activePlugin].orEmpty()
    var selectedBookmark by remember(activePlugin, pageCategories) { mutableIntStateOf(0) }
    val categoryStartIndexes = remember(activePlugin, pageCategories, recent) {
        EmojiPickerIndex.sectionStarts(
            categoryItemCounts = pageCategories.map { category ->
                if (activePlugin == null) category.emojis.size else category.emojiItems.orEmpty().size
            },
            recentCount = if (activePlugin == null) recent.size else null,
            emptyCategoryPlaceholder = activePlugin != null,
        )
    }
    val searching = query.isNotBlank()
    LaunchedEffect(query, builtins, emojiDictionary) {
        searchResults = if (query.isBlank()) emptyList() else {
            withContext(Dispatchers.Default) { EmojiSearch.search(query, builtins, emojiDictionary) }
        }
    }
    val gridState = rememberLazyGridState()
    val scope = rememberCoroutineScope()
    val emojiFontSize = 28.sp
    fun selectBookmark(index: Int) {
        query = ""
        selectedBookmark = index
        focusManager.clearFocus()
        scope.launch { gridState.scrollToItem(categoryStartIndexes.getOrElse(index) { 0 }) }
    }
    val selectText: (String) -> Unit = { value ->
        recent = RecentUsageStore.record(context, RecentUsageStore.KEY_RECENT_EMOJIS, value)
        onSelect(value)
    }

    val returnFromSearch = {
        if (searchFocused || query.isNotBlank()) {
            query = ""
            focusManager.clearFocus()
            keyboardController?.hide()
            Unit
        } else {
            onBack()
        }
    }
    BackHandler(onBack = returnFromSearch)
    LaunchedEffect(activePlugin, query) { gridState.scrollToItem(0) }
    LaunchedEffect(gridState, activePlugin, searching, categoryStartIndexes) {
        if (!searching) {
            snapshotFlow { gridState.firstVisibleItemIndex }.collectLatest { first ->
                val section = EmojiPickerIndex.selectedSection(categoryStartIndexes, first)
                selectedBookmark = section
            }
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = colors.surface) {
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val compact = maxHeight < 420.dp
            Column(Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(
                    Modifier.fillMaxWidth().widthIn(max = 840.dp).padding(end = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = returnFromSearch) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        modifier = Modifier.weight(1f).padding(vertical = if (compact) 0.dp else 8.dp)
                            .onFocusChanged { searchFocused = it.isFocused },
                        placeholder = { Text("搜索表情", maxLines = 1) },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) {
                                Icon(Icons.Default.Close, contentDescription = "清空搜索")
                            }
                        },
                        singleLine = true,
                        shape = RoundedCornerShape(28.dp),
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search,
                            platformImeOptions = androidx.compose.ui.text.input.PlatformImeOptions(
                                privateImeOptions = "xime.emoji.search",
                            ),
                        ),
                        keyboardActions = KeyboardActions(onSearch = { focusManager.clearFocus() }),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = colors.surfaceContainerHigh,
                            unfocusedContainerColor = colors.surfaceContainerHigh,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                    )
                }
                if (!compact && !searching) LazyRow(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (activePlugin == null) item {
                        PickerCategory("🕘", "最近使用", selectedBookmark == 0) { selectBookmark(0) }
                    }
                    rowItemsIndexed(pageCategories) { categoryIndex, category ->
                        Box(Modifier.semantics { contentDescription = category.name }) {
                            EmojiCategoryTab(
                                icon = category.icon,
                                pluginIcon = category.pluginIcon,
                                isSelected = !searching && selectedBookmark == categoryIndex + if (activePlugin == null) 1 else 0,
                                onClick = { selectBookmark(categoryIndex + if (activePlugin == null) 1 else 0) },
                                backgroundColor = colors.surface,
                                textColor = colors.onSurface,
                                selectedBackgroundColor = colors.primaryContainer,
                                modifier = Modifier.padding(vertical = 8.dp).size(40.dp),
                            )
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                LazyVerticalGrid(
                    columns = GridCells.Fixed(8),
                    state = gridState,
                    modifier = Modifier.weight(1f).widthIn(max = 840.dp).fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = if (compact) 2.dp else 8.dp),
                ) {
                    if (searching && activePlugin == null) {
                        if (!compact) item(span = { GridItemSpan(maxLineSpan) }) {
                            PickerHeading("搜索结果 · " + searchResults.size)
                        }
                        if (searchResults.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            PickerEmpty("没有找到表情，试试「开心」「爱」或 smile")
                        }
                        items(searchResults, key = { "search:" + it }) { emoji ->
                            PickerEmoji(emoji, emojiFontSize) { selectText(emoji) }
                        }
                    } else if (activePlugin == null) {
                        item(span = { GridItemSpan(maxLineSpan) }) { PickerHeading("最近使用") }
                        if (recent.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                            PickerEmpty("点按表情后会显示在这里")
                        }
                        items(recent, key = { "recent:" + it }) { emoji ->
                            PickerEmoji(emoji, emojiFontSize) { selectText(emoji) }
                        }
                        pageCategories.forEachIndexed { index, category ->
                            item(span = { GridItemSpan(maxLineSpan) }) { PickerHeading(category.name) }
                            items(category.emojis, key = { "builtin:" + index + ":" + it }) { emoji ->
                                PickerEmoji(emoji, emojiFontSize) { selectText(emoji) }
                            }
                        }
                    } else {
                        pageCategories.forEach { category ->
                            val matches = category.emojiItems.orEmpty().filter {
                                !searching || it.text.contains(query.trim(), ignoreCase = true) ||
                                    it.insertText?.contains(query.trim(), ignoreCase = true) == true
                            }
                            item(span = { GridItemSpan(maxLineSpan) }) { PickerHeading(category.name) }
                            if (matches.isEmpty()) item(span = { GridItemSpan(maxLineSpan) }) {
                                PickerEmpty(if (searching) "没有找到表情" else "此分类暂无表情")
                            }
                            items(
                                items = matches,
                                key = { "plugin:" + category.pluginId + ":" + category.name + ":" + it.id },
                            ) { item ->
                                PluginEmojiButton(
                                    emojiItem = item,
                                    onClick = {
                                        val image = item.imageUrl
                                        if (image != null) onImageSelect(image)
                                        else selectText(item.insertText ?: item.text)
                                    },
                                    defaultHeightDp = category.layoutItemHeightDp.coerceIn(40, 120),
                                    backgroundColor = colors.surface,
                                    textColor = colors.onSurface,
                                    modifier = Modifier.fillMaxWidth().padding(2.dp),
                                )
                            }
                        }
                    }
                }
                HorizontalDivider(color = colors.outlineVariant)
                if (!compact) Row(
                    modifier = Modifier.fillMaxWidth().widthIn(max = 840.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回键盘")
                    }
                    LazyRow(
                        Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(horizontal = 4.dp),
                    ) {
                        item {
                            FilterChip(
                                selected = activePlugin == null,
                                onClick = { selectedPlugin = null; query = "" },
                                label = { Text("Emoji") },
                            )
                        }
                        rowItems(pluginGroups.keys.toList()) { id ->
                            FilterChip(
                                selected = activePlugin == id,
                                onClick = { selectedPlugin = id; query = "" },
                                label = {
                                    Text(pluginNames[id] ?: id, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                },
                            )
                        }
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "退格")
                    }
                }
            }
        }
    }
}

@Composable
private fun PickerHeading(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun PickerEmpty(message: String) {
    Text(
        message,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 8.dp, vertical = 12.dp),
    )
}

@Composable
private fun PickerEmoji(emoji: String, fontSize: TextUnit, onClick: () -> Unit) {
    Box(
        Modifier.fillMaxWidth().heightIn(min = 44.dp).clickable(onClick = onClick)
            .semantics { contentDescription = emoji },
        contentAlignment = Alignment.Center,
    ) { Text(emoji, fontSize = fontSize, maxLines = 1) }
}

@Composable
private fun PickerCategory(icon: String, label: String, selected: Boolean, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        modifier = Modifier.padding(vertical = 4.dp).size(44.dp)
            .semantics { contentDescription = label },
        shape = CircleShape,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
    ) { Box(contentAlignment = Alignment.Center) { Text(icon, fontSize = 22.sp) } }
}
