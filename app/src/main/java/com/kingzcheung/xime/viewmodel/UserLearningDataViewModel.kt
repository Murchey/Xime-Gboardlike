package com.kingzcheung.xime.viewmodel

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.association.UserLearningData
import com.kingzcheung.xime.association.UserLearningStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class UserLearningDataUiState(
    val entries: List<UserLearningData.Entry> = emptyList(),
    val filteredEntries: List<UserLearningData.Entry> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = true,
    val isDeleting: Boolean = false,
    val isSelectionMode: Boolean = false,
    val selectedKeys: Set<String> = emptySet(),
    /** 大于 0 表示确认弹窗已打开（值是待删除条数）。 */
    val pendingDeleteCount: Int = 0,
    val message: String? = null,
    val messageIsError: Boolean = false
)

/**
 * 「学习数据」页：智能联想积累的私有 n-gram 数据的浏览、搜索、多选与删除。
 *
 * 数据源在 [UserLearningStore]：引擎已初始化时走内存缓存并落盘，否则直接改 JSON 文件，
 * 因此本页不要求先加载联想模型。
 */
class UserLearningDataViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(UserLearningDataUiState())
    val uiState: StateFlow<UserLearningDataUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        viewModelScope.launch {
            val entries = UserLearningStore.load(context)
            _uiState.update { state ->
                val aliveKeys = entries.map { it.key }.toSet()
                state.copy(
                    entries = entries,
                    filteredEntries = UserLearningData.filter(entries, state.searchQuery),
                    isLoading = false,
                    // 选中的记录可能已经不在了（例如别处清空），顺手剔除
                    selectedKeys = state.selectedKeys intersect aliveKeys,
                    isSelectionMode = state.isSelectionMode && entries.isNotEmpty()
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.update {
            it.copy(searchQuery = query, filteredEntries = UserLearningData.filter(it.entries, query))
        }
    }

    // ── 多选 ──

    fun enterSelectionMode() {
        if (_uiState.value.entries.isEmpty()) return
        _uiState.update { it.copy(isSelectionMode = true, selectedKeys = emptySet()) }
    }

    fun exitSelectionMode() {
        _uiState.update { it.copy(isSelectionMode = false, selectedKeys = emptySet()) }
    }

    fun toggleSelection(entry: UserLearningData.Entry) {
        val state = _uiState.value
        if (!state.isSelectionMode) return
        val next = if (entry.key in state.selectedKeys) state.selectedKeys - entry.key
        else state.selectedKeys + entry.key
        _uiState.update { it.copy(selectedKeys = next) }
    }

    /** 全选 / 取消全选当前搜索结果（只作用于过滤后的列表）。 */
    fun toggleSelectAll() {
        val state = _uiState.value
        if (!state.isSelectionMode) return
        val allKeys = state.filteredEntries.map { it.key }.toSet()
        val next = if (allKeys.isNotEmpty() && state.selectedKeys.containsAll(allKeys)) {
            state.selectedKeys - allKeys
        } else {
            state.selectedKeys + allKeys
        }
        _uiState.update { it.copy(selectedKeys = next) }
    }

    // ── 删除 ──

    fun requestDeleteSelected() {
        val count = _uiState.value.selectedKeys.size
        if (count == 0) return
        _uiState.update { it.copy(pendingDeleteCount = count) }
    }

    fun cancelDeleteSelected() {
        _uiState.update { it.copy(pendingDeleteCount = 0) }
    }

    fun confirmDeleteSelected() {
        val keys = _uiState.value.selectedKeys
        if (keys.isEmpty()) {
            _uiState.update { it.copy(pendingDeleteCount = 0) }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isDeleting = true, pendingDeleteCount = 0, message = null) }
            val result = UserLearningStore.remove(context, keys)
            val entries = withContext(Dispatchers.IO) { UserLearningStore.load(context) }
            _uiState.update { state ->
                state.copy(
                    entries = entries,
                    filteredEntries = UserLearningData.filter(entries, state.searchQuery),
                    isDeleting = false,
                    isSelectionMode = false,
                    selectedKeys = emptySet(),
                    message = result.fold(
                        onSuccess = { tr(R.string.s_yishanchu_tiaoxuexishuju, it) },
                        onFailure = { e ->
                            tr(R.string.s_shanchushibai_2, e.message ?: tr(R.string.s_weizhicuowu))
                        }
                    ),
                    messageIsError = result.isFailure
                )
            }
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(message = null) }
    }
}
