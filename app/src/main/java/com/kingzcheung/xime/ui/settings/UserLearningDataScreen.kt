package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.association.UserLearningData
import com.kingzcheung.xime.viewmodel.UserLearningDataViewModel

/**
 * 「学习数据」页：智能联想积累的用户私有 n-gram 数据（二元组 / 三元组）。
 *
 * 与「用户词库」页的分工：那是 librime 的 `*.userdb`（走引擎 JNI），
 * 这里是 `filesDir/user_ngram_cache.json`（纯本地 JSON，不依赖引擎进程）。
 * 两页的搜索 / 多选 / 删除交互保持一致，用户不必学两套。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UserLearningDataContent(onBack: () -> Unit) {
    val viewModel: UserLearningDataViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    val selectionMode = uiState.isSelectionMode
    val selectedCount = uiState.selectedKeys.size
    val allFilteredSelected = uiState.filteredEntries.isNotEmpty() && uiState.filteredEntries.all {
        it.key in uiState.selectedKeys
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        if (selectionMode) tr(R.string.s_yixuan_xiang, selectedCount)
                        else tr(R.string.s_xuexishuju)
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = { if (selectionMode) viewModel.exitSelectionMode() else onBack() }
                    ) {
                        Icon(
                            if (selectionMode) Icons.Default.Close
                            else Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = if (selectionMode) tr(R.string.s_tuichuduoxuan)
                            else tr(R.string.s_fanhui)
                        )
                    }
                },
                actions = {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.padding(end = 16.dp).size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else if (selectionMode) {
                        IconButton(onClick = viewModel::toggleSelectAll) {
                            Icon(
                                Icons.Default.DoneAll,
                                contentDescription = if (allFilteredSelected) tr(R.string.s_quxiaoquanxuan)
                                else tr(R.string.s_quanxuan)
                            )
                        }
                        IconButton(
                            onClick = { viewModel.requestDeleteSelected() },
                            enabled = selectedCount > 0
                        ) {
                            Icon(
                                Icons.Default.DeleteSweep,
                                contentDescription = tr(R.string.s_shanchuxuanzhong),
                                tint = if (selectedCount > 0) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { innerPadding ->
        Column(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
            RimeSearchField(
                query = uiState.searchQuery,
                onQueryChange = viewModel::setSearchQuery,
                modifier = Modifier.padding(horizontal = 16.dp),
                placeholder = tr(R.string.s_sousuoxuexishuju)
            )
            Spacer(modifier = Modifier.height(8.dp))

            uiState.message?.let { message ->
                LearningMessageBanner(
                    message = message,
                    isError = uiState.messageIsError,
                    onDismiss = viewModel::dismissMessage
                )
                Spacer(modifier = Modifier.height(8.dp))
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (uiState.searchQuery.isEmpty()) {
                        tr(R.string.s_gong_tiao, uiState.entries.size)
                    } else {
                        tr(R.string.s_pipei_tiao_2, uiState.filteredEntries.size, uiState.entries.size)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f).padding(vertical = 4.dp)
                )
                if (!selectionMode && uiState.filteredEntries.isNotEmpty()) {
                    TextButton(
                        onClick = viewModel::enterSelectionMode,
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text(tr(R.string.s_duoxuan), style = MaterialTheme.typography.labelMedium)
                    }
                }
            }

            when {
                uiState.isLoading -> {
                    Centered { CircularProgressIndicator() }
                }
                uiState.entries.isEmpty() -> {
                    Centered {
                        UsageHint(
                            title = tr(R.string.s_zanwuxuexishuju),
                            message = tr(R.string.s_yongzhegefanganjixushuru_citia_3a000)
                        )
                    }
                }
                uiState.filteredEntries.isEmpty() -> {
                    Centered {
                        Text(
                            tr(R.string.s_weizhaodaopipeitiaomu),
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 6.dp, bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(items = uiState.filteredEntries, key = { it.key }) { entry ->
                            LearningEntryRow(
                                entry = entry,
                                selectionMode = selectionMode,
                                selected = entry.key in uiState.selectedKeys,
                                onToggle = { viewModel.toggleSelection(entry) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (uiState.pendingDeleteCount > 0) {
        AlertDialog(
            onDismissRequest = viewModel::cancelDeleteSelected,
            title = { Text(tr(R.string.s_shanchuxuanzhong)) },
            text = {
                Text(tr(R.string.s_jiangshanchu_xuanzhong_xuexishuju, uiState.pendingDeleteCount))
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDeleteSelected) {
                    Text(tr(R.string.s_shanchu), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelDeleteSelected) { Text(tr(R.string.s_quxiao)) }
            }
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { content() }
}

/** 一条 n-gram 记录：主文本 + 词元数/出现次数，与词库行保持同样的视觉密度。 */
@Composable
private fun LearningEntryRow(
    entry: UserLearningData.Entry,
    selectionMode: Boolean,
    selected: Boolean,
    onToggle: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (selectionMode) Modifier.clickable(onClick = onToggle) else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = if (selectionMode && selected) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (selectionMode) {
                Checkbox(
                    checked = selected,
                    onCheckedChange = { onToggle() },
                    modifier = Modifier.padding(end = 4.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(entry.text, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(2.dp))
                Text(
                    tr(R.string.s_ngram_miaoshu, entry.order, entry.count),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
private fun LearningMessageBanner(message: String, isError: Boolean, onDismiss: () -> Unit) {
    val container = if (isError) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.secondaryContainer
    val onContainer = if (isError) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onSecondaryContainer
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp),
        color = container
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                message,
                style = MaterialTheme.typography.bodySmall,
                color = onContainer,
                modifier = Modifier.weight(1f).padding(vertical = 12.dp)
            )
            IconButton(onClick = onDismiss) {
                Icon(
                    Icons.Default.Close, contentDescription = tr(R.string.s_guanbitishi),
                    modifier = Modifier.size(18.dp), tint = onContainer
                )
            }
        }
    }
}
