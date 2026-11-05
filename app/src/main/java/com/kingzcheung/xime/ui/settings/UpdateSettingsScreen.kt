package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
 * 更新设置：本页同时是「检测更新地址」与「下载来源」的设置界面。
 *
 * - 检测源：GitHub / Gitee 仓库（owner/repo），留空用内置默认
 * - 下载源：跟随检测源资产直链，或走 GitHub 加速代理（内置 3 个 + 自定义前缀）
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
            modifier = Modifier.fillMaxSize().padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ── 检测源 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("检测更新地址", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "从这里检查是否有新版本。GitHub 留空用内置默认：${AppUpdateChecker.DEFAULT_GITHUB_REPO}",
                        fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = githubRepo, onValueChange = { githubRepo = it },
                        label = { Text("GitHub 仓库（owner/repo）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(12.dp))
                    OutlinedTextField(
                        value = giteeRepo, onValueChange = { giteeRepo = it },
                        label = { Text("Gitee 仓库（owner/repo，可选）") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = {
                            SettingsPreferences.setUpdateGithubRepo(context, githubRepo)
                            SettingsPreferences.setUpdateGiteeRepo(context, giteeRepo)
                            Toast.makeText(context, "已保存检测地址", Toast.LENGTH_SHORT).show()
                        }) { Text("保存") }
                        TextButton(onClick = {
                            githubRepo = ""
                            giteeRepo = ""
                            SettingsPreferences.setUpdateGithubRepo(context, "")
                            SettingsPreferences.setUpdateGiteeRepo(context, "")
                        }) { Text("恢复默认") }
                    }
                }
            }

            // ── 下载源 ──
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("通过 GitHub 代理下载", fontWeight = FontWeight.Medium, fontSize = 15.sp)
                            Text(
                                "GitHub 直连慢时，APK 走代理前缀下载",
                                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(checked = useProxy, onCheckedChange = {
                            useProxy = it
                            SettingsPreferences.setUpdateUseProxy(context, it)
                        })
                    }
                    if (useProxy) {
                        Spacer(Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("代理源", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        PROXY_SOURCES.forEach { option ->
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RadioButton(
                                    selected = proxy == option,
                                    onClick = {
                                        proxy = option
                                        SettingsPreferences.setUpdateProxy(context, option)
                                    }
                                )
                                Text(option.removePrefix("https://").removeSuffix("/"), fontSize = 13.sp)
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customProxy, onValueChange = { customProxy = it },
                            label = { Text("自定义代理前缀（https://.../）") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            TextButton(
                                enabled = customProxy.isNotBlank(),
                                onClick = {
                                    proxy = customProxy.trim()
                                    SettingsPreferences.setUpdateProxy(context, customProxy.trim())
                                    Toast.makeText(context, "已应用自定义代理", Toast.LENGTH_SHORT).show()
                                }
                            ) { Text("使用自定义") }
                        }
                    }
                }
            }

            Text(
                "下载的 APK 保存在缓存目录，安装或取消后自动清理。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }
    }
}
