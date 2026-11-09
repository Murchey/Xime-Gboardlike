package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun StorageTaskPage(title: String, onBack: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, tr(R.string.s_fanhui)) }
        })
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(20.dp), content = content)
    }
}

@Composable
internal fun StorageLocationRow(state: StorageOverviewState, onClick: () -> Unit) {
    SettingsItem(icon = Icons.TwoTone.Cloud,
        title = if (state.config.isConfigured) state.config.protocol.name + " · " + state.config.bucketLabel else tr(R.string.storage_setup),
        subtitle = if (!state.config.isConfigured) tr(R.string.storage_not_setup)
            else if (state.config.privateAuth) tr(R.string.os_private) else tr(R.string.os_public),
        onClick = onClick, showArrow = true)
}

@Composable
internal fun storageSyncSummary(state: StorageOverviewState): String = when {
    !state.autoSync -> tr(R.string.storage_sync_off)
    !state.builtin -> tr(R.string.storage_plugin_active)
    !state.config.isConfigured -> tr(R.string.storage_config_needed)
    else -> tr(R.string.storage_sync_on)
}

@Composable
fun StorageOverviewContent(
    onBack: () -> Unit,
    onConfig: () -> Unit,
    onClipboardSync: () -> Unit,
    onRecords: () -> Unit,
    onLocal: () -> Unit,
    onPluginBackup: () -> Unit,
    model: ObjectStorageOverviewViewModel = viewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    var advanced by rememberSaveable { mutableStateOf(false) }
    StorageTaskPage(tr(R.string.s_tongbuyubeifen), onBack) {
        SettingsSection(tr(R.string.os_section)) {
            StorageLocationRow(state, onConfig)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingsItem(Icons.TwoTone.Sync, tr(R.string.storage_sync_title), storageSyncSummary(state), onClipboardSync, showArrow = true)
        }
        SettingsSection(tr(R.string.storage_backup_heading)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(tr(if (state.fullBackup) R.string.os_backup_full else R.string.os_backup_userdict))
                if (!state.config.isConfigured) Text(tr(R.string.storage_config_needed), style = MaterialTheme.typography.bodySmall)
                Button(onClick = { if (state.config.isConfigured) model.backupNow() else onConfig() }, enabled = !state.backingUp,
                    modifier = Modifier.fillMaxWidth()) {
                    if (state.backingUp) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(tr(when { state.backingUp -> R.string.storage_backup_running; !state.config.isConfigured -> R.string.storage_setup; else -> R.string.os_backup_now }))
                }
                state.backupMessage?.let { Text(it, color = if (state.backupError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
            SettingsItem(Icons.TwoTone.Restore, tr(R.string.os_backup_records), tr(R.string.storage_records_hint), onRecords, showArrow = true)
        }
        OutlinedButton(onClick = { advanced = !advanced }, modifier = Modifier.fillMaxWidth()) {
            Text(tr(R.string.storage_advanced))
            Spacer(Modifier.width(8.dp))
            Icon(if (advanced) Icons.TwoTone.ExpandLess else Icons.TwoTone.ExpandMore, contentDescription = null)
        }
        if (advanced) SettingsSection(tr(R.string.os_backup_scope)) {
            SettingsToggleItem(icon = Icons.TwoTone.Backup,
                title = tr(R.string.storage_full_option), subtitle = tr(R.string.storage_full_hint),
                checked = state.fullBackup, onCheckedChange = model::setFullBackup)
        }
        SettingsSection(tr(R.string.s_gengduo)) {
            SettingsItem(Icons.TwoTone.Folder, tr(R.string.storage_local), tr(R.string.storage_local_hint), onLocal, showArrow = true)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            SettingsItem(Icons.TwoTone.Cloud, tr(R.string.storage_plugin_backup), tr(R.string.storage_other_backup), onPluginBackup, showArrow = true)
        }
    }
}

@Composable
fun ClipboardSyncSummaryRow(onOpen: () -> Unit, onConfigure: () -> Unit, model: ObjectStorageOverviewViewModel = viewModel()) {
    val state by model.state.collectAsStateWithLifecycle()
    SettingsSection(tr(R.string.storage_sync_title)) {
        SettingsToggleItem(icon = Icons.TwoTone.Sync, title = tr(R.string.storage_sync_title),
            subtitle = storageSyncSummary(state), checked = state.autoSync,
            onCheckedChange = { enabled ->
                if (enabled && !state.config.isConfigured) onConfigure() else model.setAutoSync(enabled)
            }, onClick = onOpen, showArrow = true)
    }
}
