package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Cloud
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.objectstorage.ObjectStorageClient
import com.kingzcheung.xime.objectstorage.ObjectStorageConfig
import com.kingzcheung.xime.objectstorage.ObjectStorageConfigStore
import com.kingzcheung.xime.objectstorage.StorageProtocol
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 对象存储配置页（剪切板同步与数据备份共用一套配置）。
 *
 * 顶部常驻「仅 Android 版互通」说明卡；默认私有读写（推荐）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ObjectStorageSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { ObjectStorageConfigStore.of(context) }
    var config by remember { mutableStateOf(store.load()) }
    var showSecret by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    fun persist() { store.save(config) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.os_configure)) },
                navigationIcon = {
                    IconButton(onClick = { persist(); onBack() }) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(R.string.s_fanhui))
                    }
                },
                actions = {
                    TextButton(onClick = { persist(); onBack() }) { Text(tr(R.string.os_save)) }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // 常驻说明卡：仅 Android 版互通
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.Top,
                ) {
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

            SettingsSection(title = tr(R.string.os_protocol), content = {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    StorageProtocol.entries.forEach { p ->
                        FilterChip(
                            selected = config.protocol == p,
                            onClick = { config = config.copy(protocol = p); persist() },
                            label = { Text(p.name) },
                        )
                    }
                }
            })

            SettingsSection(title = tr(R.string.os_configure), content = {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = config.endpoint,
                        onValueChange = { config = config.copy(endpoint = it) },
                        label = { Text(tr(R.string.os_endpoint)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    )
                    OutlinedTextField(
                        value = config.bucket,
                        onValueChange = { config = config.copy(bucket = it) },
                        label = { Text(tr(R.string.os_bucket)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.accessKeyId,
                        onValueChange = { config = config.copy(accessKeyId = it) },
                        label = { Text(tr(R.string.os_ak)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = config.secretKey,
                        onValueChange = { config = config.copy(secretKey = it) },
                        label = { Text(tr(R.string.os_sk)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = if (showSecret) VisualTransformation.None
                        else PasswordVisualTransformation(),
                        trailingIcon = {
                            TextButton(onClick = { showSecret = !showSecret }) {
                                Text(if (showSecret) "隐藏" else "显示")
                            }
                        },
                    )

                    Text(
                        text = tr(R.string.os_private),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = config.privateAuth,
                            onClick = { config = config.copy(privateAuth = true); persist() },
                        )
                        Text(tr(R.string.os_private), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = tr(R.string.os_private_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp),
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        RadioButton(
                            selected = !config.privateAuth,
                            onClick = { config = config.copy(privateAuth = false); persist() },
                        )
                        Text(tr(R.string.os_public), style = MaterialTheme.typography.bodyMedium)
                    }
                    Text(
                        text = tr(R.string.os_public_hint),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 32.dp),
                    )

                    Button(
                        onClick = {
                            persist()
                            testing = true
                            testResult = null
                            scope.launch {
                                val result = withContext(Dispatchers.IO) {
                                    val c = store.load()
                                    if (!c.isConfigured) {
                                        ObjectStorageClient.Result.Failure("配置不完整")
                                    } else {
                                        ObjectStorageClient(c).probeRead(
                                            ObjectStorageClient.ROOT_PREFIX + "/index.json"
                                        )
                                    }
                                }
                                testing = false
                                testResult = when (result) {
                                    is ObjectStorageClient.Result.Success -> tr(R.string.os_test_ok)
                                    is ObjectStorageClient.Result.Failure -> tr(R.string.os_test_fail, result.message)
                                }
                            }
                        },
                        enabled = !testing,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.TwoTone.Cloud, contentDescription = null)
                        Spacer(Modifier.width(8.dp))
                        Text(if (testing) "…" else tr(R.string.os_test))
                    }
                    testResult?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodySmall,
                            color = if (it == tr(R.string.os_test_ok))
                                MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.error,
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
            })
        }
    }
}
