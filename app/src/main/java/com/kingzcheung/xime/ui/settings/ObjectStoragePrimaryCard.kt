package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.clipboard.sync.objstore.ClipboardObjectStoreSync
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.objectstorage.ObjectStorageBackupManager
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay

/**
 * 对象存储主卡（【同步与备份】页与【剪切板】页共用，置顶展示）。
 *
 * 用户要求：优先展示本卡，上游 WebDAV 收进二级；顶部常驻 Android-only 说明。
 */
@Composable
fun ObjectStoragePrimaryCard(
    onNavigateToConfig: () -> Unit,
    onNavigateToLegacy: () -> Unit,
    /** 是否显示备份相关操作（剪切板页只显示同步，不显示备份）。 */
    showBackup: Boolean = true,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val config = remember { ObjectStorageConfigStore.of(context).load() }
    var message by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        // 常驻说明卡
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            ),
            modifier = Modifier.fillMaxWidth(),
        ) {
            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                Icon(
                    Icons.TwoTone.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = tr(R.string.os_notice),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }

        // 配置摘要
        SettingsSection(title = tr(R.string.os_section), content = {
            SettingsItem(
                icon = Icons.TwoTone.Cloud,
                title = if (config.isConfigured)
                    tr(R.string.os_configured, "${config.protocol.name} · ${config.bucketLabel}")
                else tr(R.string.os_not_configured),
                subtitle = if (config.privateAuth) tr(R.string.os_private) else tr(R.string.os_public),
                onClick = onNavigateToConfig,
                showArrow = true,
            )
        })

        // 剪切板同步
        SettingsSection(title = tr(R.string.os_clipboard_sync), content = {
            Column(
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val engine = if (ClipboardObjectStoreSync.isBuiltinEnabled(context))
                    tr(R.string.os_engine_builtin) else tr(R.string.os_engine_plugin)
                Text(
                    text = tr(R.string.os_section) + " · " + engine,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = ClipboardObjectStoreSync.isBuiltinEnabled(context),
                        onClick = {
                            SettingsPreferences.getPrefsPublic(context).edit()
                                .putString(ClipboardObjectStoreSync.PREF_ENGINE,
                                    ClipboardObjectStoreSync.ENGINE_BUILTIN).apply()
                            // 触发服务层重挂（重启 IME 服务由设置页统一负责）
                            message = tr(R.string.os_engine_builtin)
                        },
                        label = { Text(tr(R.string.os_engine_builtin)) },
                    )
                    FilterChip(
                        selected = !ClipboardObjectStoreSync.isBuiltinEnabled(context),
                        onClick = {
                            SettingsPreferences.getPrefsPublic(context).edit()
                                .putString(ClipboardObjectStoreSync.PREF_ENGINE,
                                    ClipboardObjectStoreSync.ENGINE_PLUGIN).apply()
                            message = tr(R.string.os_engine_plugin)
                        },
                        label = { Text(tr(R.string.os_engine_plugin)) },
                    )
                }

                val mode = ClipboardObjectStoreSync.mode(context)
                Text(tr(R.string.os_sync_mode), style = MaterialTheme.typography.titleSmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = mode == ClipboardObjectStoreSync.MODE_PUSH,
                        onClick = {
                            SettingsPreferences.getPrefsPublic(context).edit()
                                .putString(ClipboardObjectStoreSync.PREF_MODE,
                                    ClipboardObjectStoreSync.MODE_PUSH).apply()
                        },
                        label = { Text(tr(R.string.os_mode_push)) },
                    )
                    FilterChip(
                        selected = mode != ClipboardObjectStoreSync.MODE_PUSH,
                        onClick = {
                            SettingsPreferences.getPrefsPublic(context).edit()
                                .putString(ClipboardObjectStoreSync.PREF_MODE,
                                    ClipboardObjectStoreSync.MODE_BIDIRECTIONAL).apply()
                        },
                        label = { Text(tr(R.string.os_mode_bidi)) },
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    // 直接构造一次引擎跑 syncNow（不依赖 IME 服务生命周期）
                                    val mgr = com.kingzcheung.xime.clipboard.ClipboardManager.getInstance(context)
                                    val e = ClipboardObjectStoreSync(
                                        context = context,
                                        clipboardManager = mgr,
                                        dao = com.kingzcheung.xime.clipboard.db.ClipboardDatabase
                                            .getInstance(context).clipboardDao(),
                                    )
                                    e.start()
                                    val out = e.sync(throttle = false)
                                    e.release()
                                    out
                                }
                                busy = false
                                message = when (r) {
                                    is ClipboardObjectStoreSync.SyncOutcome.Success ->
                                        tr(R.string.os_msg_ok, "上传 ${r.uploaded} · 下载 ${r.downloaded} · 删除 ${r.deleted}")
                                    is ClipboardObjectStoreSync.SyncOutcome.Failure -> tr(R.string.os_fail, r.message)
                                    else -> tr(R.string.os_never_sync)
                                }
                            }
                        },
                        enabled = !busy && config.isConfigured,
                    ) {
                        Icon(Icons.TwoTone.Sync, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (busy) "…" else tr(R.string.os_sync_now))
                    }
                }

                val lastAt = SettingsPreferences.getPrefsPublic(context)
                    .getLong(ClipboardObjectStoreSync.PREF_LAST_UPDATED, 0L)
                Text(
                    text = if (lastAt > 0) tr(
                        R.string.os_last_sync,
                        java.text.SimpleDateFormat("MM-dd HH:mm", java.util.Locale.getDefault())
                            .format(java.util.Date(lastAt))
                    ) else tr(R.string.os_never_sync),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                message?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                }
            }
        })

        // 数据备份（仅同步与备份页显示）
        if (showBackup) {
            SettingsSection(title = tr(R.string.os_backup_now), content = {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    val full = ObjectStorageBackupManager.isFullSchemasEnabled(context)
                    Text(tr(R.string.os_backup_scope), style = MaterialTheme.typography.titleSmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = !full,
                            onClick = { ObjectStorageBackupManager.setFullSchemasEnabled(context, false) },
                            label = { Text(tr(R.string.os_backup_userdict)) },
                        )
                        FilterChip(
                            selected = full,
                            onClick = { ObjectStorageBackupManager.setFullSchemasEnabled(context, true) },
                            label = { Text(tr(R.string.os_backup_full)) },
                        )
                    }
                    if (full) {
                        Text(
                            text = tr(R.string.os_backup_full_hint),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Button(
                        onClick = {
                            busy = true
                            scope.launch {
                                val r = withContext(Dispatchers.IO) {
                                    ObjectStorageBackupManager.backupNow(context)
                                }
                                busy = false
                                message = when (r) {
                                    is ObjectStorageBackupManager.Outcome.Success -> tr(R.string.os_msg_ok, r.message)
                                    is ObjectStorageBackupManager.Outcome.Failure -> tr(R.string.os_fail, r.message)
                                }
                            }
                        },
                        enabled = !busy && config.isConfigured,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.TwoTone.Backup, contentDescription = null)
                        Spacer(Modifier.width(6.dp))
                        Text(if (busy) "…" else tr(R.string.os_backup_now))
                    }
                }
            })
        }

        // 上游 WebDAV 等收进二级
        OutlinedButton(
            onClick = onNavigateToLegacy,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(tr(R.string.os_legacy))
        }
        Text(
            text = tr(R.string.os_legacy_hint),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
