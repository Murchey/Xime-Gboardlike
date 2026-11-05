package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.AppUpdateChecker
import com.kingzcheung.xime.settings.PROXY_SOURCES
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 更新设置：自定义 GitHub/Gitee 检测仓库、GitHub 加速代理。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun UpdateSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var githubRepo by remember { mutableStateOf(SettingsPreferences.getUpdateGithubRepo(context)) }
    var giteeRepo by remember { mutableStateOf(SettingsPreferences.getUpdateGiteeRepo(context)) }
    var useProxy by remember { mutableStateOf(SettingsPreferences.isUpdateUseProxy(context)) }
    var proxy by remember {
        val saved = SettingsPreferences.getUpdateProxy(context)
        mutableStateOf(if (saved.isNotBlank()) saved else PROXY_SOURCES.first())
    }
    var customProxy by remember { mutableStateOf("") }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("更新设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("检测仓库", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "留空则使用内置默认：${AppUpdateChecker.DEFAULT_GITHUB_REPO}",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubRepo,
                        onValueChange = { githubRepo = it },
                        label = { Text("GitHub 仓库 (owner/repo)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = giteeRepo,
                        onValueChange = { giteeRepo = it },
                        label = { Text("Gitee 仓库 (owner/repo，可选)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(onClick = {
                            SettingsPreferences.setUpdateGithubRepo(context, githubRepo)
                            SettingsPreferences.setUpdateGiteeRepo(context, giteeRepo)
                        }) { Text("保存") }
                    }
                }
            }

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用 GitHub 加速代理", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "GitHub 直连慢时，通过代理前缀下载 APK",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useProxy, onCheckedChange = {
                            useProxy = it
                            SettingsPreferences.setUpdateUseProxy(context, it)
                        })
                    }
                    if (useProxy) {
                        Spacer(Modifier.height(12.dp))
                        PBX_PROXY_LIST.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = proxy == option,
                                    onClick = {
                                        proxy = option
                                        SettingsPreferences.setUpdateProxy(context, option)
                                    }
                                )
                                Text(
                                    option.removePrefix("https://").removeSuffix("/"),
                                    fontSize = 13.sp
                                )
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customProxy,
                            onValueChange = { customProxy = it },
                            label = { Text("自定义代理前缀（https://.../）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                if (customProxy.isNotBlank()) {
                                    proxy = customProxy
                                    SettingsPreferences.setUpdateProxy(context, customProxy)
                                }
                            }) { Text("使用自定义") }
                        }
                    }
                }
            }

            Text(
                "下载的 APK 保存在缓存目录，安装或取消后自动清理。",
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}

private val PBX_PROXY_LIST = PROXY_SOURCES
