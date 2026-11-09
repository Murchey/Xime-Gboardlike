package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

import android.text.format.DateUtils
import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager.BackupEntry
import java.time.Instant
import java.time.OffsetDateTime

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectStorageBackupRecordsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val factory = remember(context.applicationContext) { ObjectStorageBackupRecordsViewModel.factory(context) }
    val model: ObjectStorageBackupRecordsViewModel = viewModel(factory = factory)
    val state by model.state.collectAsState()
    var confirmation by remember { mutableStateOf<Pair<BackupEntry, Boolean>?>(null) }
    val busy = state.loading || state.busyKey != null
    LaunchedEffect(model) { model.refresh() }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text(tr(R.string.backup_ui_1)) },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.backup_ui_2)) } },
            actions = {
                IconButton(onClick = model::refresh, enabled = !busy) { Icon(Icons.Default.Refresh, tr(R.string.backup_ui_3)) }
            },
        )
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            if (state.busyKey != null) LinearProgressIndicator(Modifier.fillMaxWidth())
            state.operationError?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error) }
            state.message?.let { Text(it, Modifier.padding(16.dp), color = MaterialTheme.colorScheme.primary) }
            when {
                state.loading && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
                state.loadError != null -> Column(Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(state.loadError!!, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = model::refresh, enabled = !busy) { Text(tr(R.string.backup_ui_4)) }
                }
                state.loaded && state.entries.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(tr(R.string.backup_ui_5))
                }
                else -> {
                    if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        items(state.entries, key = { it.key }) { entry ->
                            OutlinedCard(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)) {
                                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(if (entry.full) tr(R.string.backup_ui_6) else tr(R.string.backup_ui_7), style = MaterialTheme.typography.titleMedium)
                                    Text(entry.displayName, style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        backupRecordDate(entry.lastModified) + " · " + Formatter.formatFileSize(context, entry.size),
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                                        TextButton(onClick = { confirmation = entry to true }, enabled = !busy) { Text(tr(R.string.backup_ui_8)) }
                                        TextButton(onClick = { confirmation = entry to false }, enabled = !busy) {
                                            Text(tr(R.string.backup_ui_9), color = MaterialTheme.colorScheme.error)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    confirmation?.let { (entry, restore) ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(if (restore) tr(R.string.backup_ui_10) else tr(R.string.backup_ui_11)) },
            text = {
                Text(entry.displayName + "\n\n" + if (!restore) tr(R.string.backup_ui_12) else if (entry.full)
                    tr(R.string.backup_ui_13) else tr(R.string.backup_ui_14))
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    if (restore) model.restore(entry) else model.delete(entry)
                }, enabled = !busy) { Text(if (restore) tr(R.string.backup_ui_8) else tr(R.string.backup_ui_9)) }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(tr(R.string.backup_ui_15)) } },
        )
    }
}

@Composable
private fun backupRecordDate(raw: String): String {
    val context = LocalContext.current
    val timestamp = remember(raw) {
        runCatching { Instant.parse(raw).toEpochMilli() }
            .recoverCatching { OffsetDateTime.parse(raw).toInstant().toEpochMilli() }.getOrNull()
    }
    return timestamp?.let {
        DateUtils.formatDateTime(context, it, DateUtils.FORMAT_SHOW_DATE or DateUtils.FORMAT_SHOW_TIME or DateUtils.FORMAT_SHOW_YEAR)
    } ?: raw.ifBlank { tr(R.string.backup_ui_16) }
}