package com.kingzcheung.xime.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.objectstorage.StorageProtocol

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectStorageSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current.applicationContext
    val vm: ObjectStorageSettingsViewModel = viewModel(factory = viewModelFactory {
        initializer {
            val store = ObjectStorageConfigStore.of(context)
            ObjectStorageSettingsViewModel(store.load(), { store.save(it) }, {
                ObjectStorageClient(it).testConnection()
            })
        }
    })
    val config = vm.draft
    var showSecret by remember { mutableStateOf(false) }
    var confirmLeave by rememberSaveable { mutableStateOf(false) }
    var protocolExpanded by remember { mutableStateOf(false) }
    fun requestBack() {
        if (vm.saving) return
        if (vm.dirty) confirmLeave = true else onBack()
    }
    BackHandler { requestBack() }
    if (confirmLeave) {
        AlertDialog(
            onDismissRequest = { if (!vm.saving) confirmLeave = false },
            title = { Text(tr(R.string.config_leave_title)) },
            text = { Text(if (vm.messageIsError) vm.message ?: tr(R.string.config_unsaved_short)
                else tr(R.string.config_leave_body)) },
            confirmButton = {
                TextButton(enabled = !vm.busy, onClick = { vm.save { confirmLeave = false; onBack() } }) {
                    Text(tr(R.string.config_save_back))
                }
            },
            dismissButton = {
                Row {
                    TextButton(enabled = !vm.saving, onClick = {
                        vm.discard(); confirmLeave = false; onBack()
                    }) { Text(tr(R.string.config_discard)) }
                    TextButton(enabled = !vm.saving, onClick = { confirmLeave = false }) { Text(tr(R.string.config_continue_edit)) }
                }
            },
        )
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.os_configure)) },
                navigationIcon = {
                    IconButton(enabled = !vm.saving, onClick = { requestBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(R.string.s_fanhui))
                    }
                },
                actions = {
                    TextButton(enabled = vm.dirty && !vm.busy, onClick = { vm.save(onBack) }) {
                        Text(if (vm.saving) tr(R.string.config_saving) else tr(R.string.os_save))
                    }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).imePadding()
                .verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(tr(R.string.os_notice), style = MaterialTheme.typography.bodyMedium)
            Text(if (vm.dirty) tr(R.string.config_dirty) else tr(R.string.config_saved_status), style = MaterialTheme.typography.labelMedium)
            vm.message?.let {
                Text(it, color = if (vm.messageIsError) MaterialTheme.colorScheme.error
                    else MaterialTheme.colorScheme.primary)
            }
            SettingsSection(title = tr(R.string.os_protocol), content = {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Box {
                        OutlinedButton(enabled = !vm.busy, onClick = { protocolExpanded = true }) {
                            Text(when (config.protocol) {
                                StorageProtocol.COS -> tr(R.string.config_cos_selected)
                                StorageProtocol.OSS -> tr(R.string.config_oss_selected)
                                StorageProtocol.S3 -> tr(R.string.config_s3_selected)
                            })
                        }
                        DropdownMenu(expanded = protocolExpanded, onDismissRequest = { protocolExpanded = false }) {
                            StorageProtocol.entries.forEach { protocol ->
                                DropdownMenuItem(text = { Text(when (protocol) {
                                    StorageProtocol.COS -> tr(R.string.config_cos)
                                    StorageProtocol.OSS -> tr(R.string.config_oss)
                                    StorageProtocol.S3 -> tr(R.string.config_s3)
                                }) }, onClick = {
                                    vm.update(config.copy(protocol = protocol)); protocolExpanded = false
                                })
                            }
                        }
                    }
                    Text(when (config.protocol) {
                        StorageProtocol.COS -> tr(R.string.config_cos_hint)
                        StorageProtocol.OSS -> tr(R.string.config_oss_hint)
                        StorageProtocol.S3 -> tr(R.string.config_s3_hint)
                    }, style = MaterialTheme.typography.bodySmall)
                }
            })
            SettingsSection(title = tr(R.string.os_configure), content = {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = config.endpoint, onValueChange = { vm.update(config.copy(endpoint = it)) },
                        enabled = !vm.busy,
                        label = { Text(tr(if (config.protocol == StorageProtocol.S3) R.string.storage_s3_endpoint else R.string.storage_bucket_url)) },
                        supportingText = { Text(tr(if (config.protocol == StorageProtocol.S3) R.string.storage_s3_endpoint_hint else R.string.storage_bucket_url_hint)) },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    if (config.protocol == StorageProtocol.S3) {
                        OutlinedTextField(
                            value = config.bucket, onValueChange = { vm.update(config.copy(bucket = it)) },
                            enabled = !vm.busy, label = { Text(tr(R.string.os_bucket)) },
                            supportingText = { Text(tr(R.string.config_bucket_required_hint)) },
                            singleLine = true, modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    OutlinedTextField(
                        value = config.accessKeyId, onValueChange = { vm.update(config.copy(accessKeyId = it)) },
                        enabled = !vm.busy, label = { Text(if (config.protocol == StorageProtocol.COS)
                            "SecretId" else "AccessKey ID") },
                        supportingText = { Text(if (config.privateAuth) tr(R.string.config_private_hint) else tr(R.string.config_public_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.secretKey, onValueChange = { vm.update(config.copy(secretKey = it)) },
                        enabled = !vm.busy, label = { Text(tr(R.string.os_sk)) },
                        supportingText = { Text(tr(R.string.config_secret_hint)) },
                        singleLine = true, modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showSecret) VisualTransformation.None else PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        trailingIcon = { TextButton(onClick = { showSecret = !showSecret }) {
                            Text(if (showSecret) tr(R.string.config_hide) else tr(R.string.config_show))
                        } },
                    )
                    Column(Modifier.selectableGroup()) {
                        listOf(true, false).forEach { privateAuth ->
                            Row(
                                Modifier.fillMaxWidth().selectable(
                                    selected = config.privateAuth == privateAuth, enabled = !vm.busy,
                                    role = Role.RadioButton, onClick = { vm.update(config.copy(privateAuth = privateAuth)) },
                                ).padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                RadioButton(selected = config.privateAuth == privateAuth, onClick = null, enabled = !vm.busy)
                                Spacer(Modifier.width(8.dp))
                                Column {
                                    Text(tr(if (privateAuth) R.string.os_private else R.string.os_public))
                                    Text(tr(if (privateAuth) R.string.os_private_hint else R.string.os_public_hint),
                                        style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                    Text(tr(R.string.config_test_hint),
                        style = MaterialTheme.typography.bodySmall)
                    Button(enabled = !vm.busy, onClick = { vm.testConnection() }, modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.testing) tr(R.string.config_testing) else tr(R.string.config_test))
                    }
                }
            })
            Spacer(Modifier.height(16.dp))
        }
    }
}
