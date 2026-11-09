package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager.BackupEntry
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ObjectStorageBackupRecordsState(
    val entries: List<BackupEntry> = emptyList(),
    val loading: Boolean = false,
    val loaded: Boolean = false,
    val loadError: String? = null,
    val busyKey: String? = null,
    val message: String? = null,
    val operationError: String? = null,
)

internal interface ObjectStorageBackupRecordsRepository {
    suspend fun list(): Result<List<BackupEntry>>
    suspend fun restore(entry: BackupEntry): ObjectStorageBackupManager.Outcome
    suspend fun delete(entry: BackupEntry): Result<Unit>
}

class ObjectStorageBackupRecordsViewModel internal constructor(
    private val repository: ObjectStorageBackupRecordsRepository,
) : ViewModel() {
    private val mutableState = MutableStateFlow(ObjectStorageBackupRecordsState())
    val state = mutableState.asStateFlow()

    fun refresh() {
        if (mutableState.value.loading || mutableState.value.busyKey != null) return
        mutableState.value = mutableState.value.copy(loading = true, loadError = null)
        viewModelScope.launch { loadRecords() }
    }

    private suspend fun loadRecords() {
        try {
            val entries = repository.list().getOrThrow()
            mutableState.value = mutableState.value.copy(entries = entries, loaded = true, loadError = null)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(loadError = e.message ?: tr(R.string.backup_ui_17))
        } finally {
            mutableState.value = mutableState.value.copy(loading = false)
        }
    }

    fun restore(entry: BackupEntry) = operate(entry, restore = true)
    fun delete(entry: BackupEntry) = operate(entry, restore = false)

    private fun operate(entry: BackupEntry, restore: Boolean) {
        if (mutableState.value.busyKey != null || mutableState.value.loading) return
        mutableState.value = mutableState.value.copy(busyKey = entry.key, message = null, operationError = null)
        viewModelScope.launch {
            try {
                val message = if (restore) {
                    when (val result = repository.restore(entry)) {
                        is ObjectStorageBackupManager.Outcome.Success -> result.message
                        is ObjectStorageBackupManager.Outcome.Failure -> throw IllegalStateException(result.message)
                    }
                } else {
                    repository.delete(entry).getOrThrow()
                    tr(R.string.backup_ui_18)
                }
                mutableState.value = mutableState.value.copy(message = message, loading = true)
                loadRecords()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                mutableState.value = mutableState.value.copy(operationError = e.message ?: tr(R.string.backup_ui_19))
            } finally {
                mutableState.value = mutableState.value.copy(busyKey = null, loading = false)
            }
        }
    }

    companion object {
        fun factory(context: Context): ViewModelProvider.Factory {
            val app = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(ObjectStorageBackupRecordsViewModel::class.java))
                    return ObjectStorageBackupRecordsViewModel(object : ObjectStorageBackupRecordsRepository {
                        override suspend fun list() = ObjectStorageBackupManager.listBackupsResult(app)
                        override suspend fun restore(entry: BackupEntry) = ObjectStorageBackupManager.restore(app, entry)
                        override suspend fun delete(entry: BackupEntry) = ObjectStorageBackupManager.deleteBackupResult(app, entry)
                    }) as T
                }
            }
        }
    }
}