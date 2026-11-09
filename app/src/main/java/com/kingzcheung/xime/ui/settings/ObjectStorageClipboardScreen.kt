package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.R
import com.kingzcheung.xime.clipboard.sync.objstore.ObjectStorageSyncCoordinator
import com.kingzcheung.xime.clipboard.sync.objstore.SyncPhase
import com.kingzcheung.xime.i18n.tr

@Composable
fun ObjectStorageClipboardContent(
    onBack: () -> Unit, onConfig: () -> Unit, onPluginSync: () -> Unit,
    model: ObjectStorageOverviewViewModel = viewModel(),
) {
    val state by model.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val coordinator = remember { ObjectStorageSyncCoordinator.of(context) }
    val status by coordinator.status.collectAsStateWithLifecycle()
    var modeDialog by remember { mutableStateOf(false) }
    StorageTaskPage(tr(R.string.storage_sync_title), onBack) {
        SettingsSection(tr(R.string.storage_sync_title)) {
            SettingsToggleItem(Icons.TwoTone.Sync, tr(R.string.storage_auto_sync),
                if (state.config.isConfigured) tr(R.string.storage_auto_hint) else tr(R.string.storage_config_needed),
                checked = state.autoSync && state.builtin,
                onCheckedChange = { if (it && !state.config.isConfigured) onConfig() else model.setAutoSync(it) })
        }
        if (state.autoSync && !state.builtin) Text(tr(R.string.storage_plugin_active), color = MaterialTheme.colorScheme.onSurfaceVariant)
        SettingsSection(tr(R.string.storage_configuration)) { StorageLocationRow(state, onConfig) }
        SettingsSection(tr(R.string.os_sync_mode)) {
            SettingsItem(Icons.TwoTone.Tune, tr(if (state.pushOnly) R.string.storage_mode_push else R.string.storage_mode_merge),
                tr(if (state.pushOnly) R.string.storage_mode_push_hint else R.string.storage_mode_merge_hint),
                { modeDialog = true }, showArrow = true)
        }
        SettingsSection(tr(R.string.storage_current_status)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                val phaseText = when (status.phase) {
                    SyncPhase.Running -> tr(R.string.storage_running)
                    SyncPhase.Success -> tr(R.string.storage_complete)
                    SyncPhase.UpToDate -> tr(R.string.storage_latest)
                    SyncPhase.Error -> tr(R.string.storage_failed)
                    SyncPhase.Idle -> storageSyncSummary(state)
                }
                Text(phaseText, color = if (status.phase == SyncPhase.Error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface)
                status.message?.takeIf { status.phase == SyncPhase.Error }?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                val last = status.lastSuccessAt ?: state.lastSyncAt
                Text(if (last > 0) tr(R.string.os_last_sync, java.text.DateFormat.getDateTimeInstance(java.text.DateFormat.SHORT, java.text.DateFormat.SHORT).format(java.util.Date(last)))
                    else tr(R.string.storage_no_sync), style = MaterialTheme.typography.bodySmall)
                Button(onClick = coordinator::requestManual, enabled = state.config.isConfigured && status.phase != SyncPhase.Running,
                    modifier = Modifier.fillMaxWidth()) {
                    if (status.phase == SyncPhase.Running) { CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp); Spacer(Modifier.width(8.dp)) }
                    Text(tr(if (status.phase == SyncPhase.Running) R.string.storage_running else R.string.os_sync_now))
                }
                if (!state.config.isConfigured) TextButton(onClick = onConfig) { Text(tr(R.string.storage_setup)) }
                Text(tr(R.string.storage_manual_hint), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        SettingsSection(tr(R.string.storage_other_sync)) {
            SettingsItem(Icons.TwoTone.Cloud, tr(R.string.storage_plugin_sync), tr(R.string.os_engine_plugin), onPluginSync, showArrow = true)
        }
    }
    if (modeDialog) AlertDialog(onDismissRequest = { modeDialog = false }, title = { Text(tr(R.string.os_sync_mode)) },
        text = {
            Column {
                listOf(true, false).forEach { push ->
                    Row(Modifier.fillMaxWidth().clickable { model.setPushOnly(push); modeDialog = false }.padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(selected = state.pushOnly == push, onClick = null)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(tr(if (push) R.string.storage_mode_push else R.string.storage_mode_merge))
                            Text(tr(if (push) R.string.storage_mode_push_hint else R.string.storage_mode_merge_hint), style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }, confirmButton = { TextButton(onClick = { modeDialog = false }) { Text(tr(R.string.storage_cancel)) } })
}
