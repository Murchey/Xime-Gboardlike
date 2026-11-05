package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.AppUpdateDownloader
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.DownloadProgress
import com.kingzcheung.xime.settings.PROXY_SOURCES
import com.kingzcheung.xime.settings.UpdateInfo
import kotlinx.coroutines.launch

/** 更新下载对话框：显示版本信息、代理选择、下载进度 */
@Composable
fun UpdateDownloadDialog(
    updateInfo: UpdateInfo,
    onDismiss: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var useProxy by remember { mutableStateOf(SettingsPreferences.isUpdateUseProxy(context)) }
    var selectedProxy by remember {
        val saved = SettingsPreferences.getUpdateProxy(context)
        mutableStateOf(if (saved.isNotBlank()) saved else PROXY_SOURCES.first())
    }
    var downloading by remember { mutableStateOf(false) }
    var progress by remember { mutableStateOf(0f) }
    var completed by remember { mutableStateOf(false) }
    val fileName = "XimeGboard-v${updateInfo.versionName}.apk"

    AlertDialog(
        onDismissRequest = { if (!downloading) onDismiss() },
        title = { Text("发现新版本 v${updateInfo.versionName}") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                Text("来源: ${updateInfo.source}", fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.height(8.dp))
                if (updateInfo.releaseNotes.isNotBlank()) {
                    Text("更新说明", fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text(updateInfo.releaseNotes, fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, lineHeight = 18.sp)
                    Spacer(Modifier.height(12.dp))
                }
                // 代理选择
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = useProxy, onCheckedChange = { useProxy = it })
                    Text("使用 GitHub 加速代理", fontSize = 14.sp)
                }
                if (useProxy) {
                    PROXY_SOURCES.forEach { proxy ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = selectedProxy == proxy, onClick = { selectedProxy = proxy })
                            Text(proxy.removePrefix("https://").removeSuffix("/"), fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
                if (downloading) {
                    Spacer(Modifier.height(12.dp))
                    LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth())
                    Text("${(progress * 100).toInt()}%", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton = {
            if (completed) {
                TextButton(onClick = {
                    val ok = AppUpdateDownloader.install(context, fileName)
                    if (ok) {
                        onDismiss()
                    } else {
                        // 未授权「安装未知应用」或拉起失败：已引导到系统授权页，保留对话框
                        Toast.makeText(
                            context,
                            "请先允许本应用安装未知应用，授权后返回再点安装",
                            Toast.LENGTH_LONG
                        ).show()
                    }
                }) {
                    Text("安装")
                }
            } else if (!downloading) {
                TextButton(onClick = {
                    downloading = true
                    scope.launch {
                        AppUpdateDownloader.download(
                            context, updateInfo.apkUrl, fileName,
                            useProxy = useProxy, proxyPrefix = if (useProxy) selectedProxy else null
                        ).collect { p ->
                            when (p) {
                                is DownloadProgress.Progress -> progress = p.fraction
                                is DownloadProgress.Completed -> { completed = true; downloading = false }
                                is DownloadProgress.Failed -> downloading = false
                            }
                        }
                    }
                }) {
                    Icon(Icons.Default.CloudDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("下载")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = { AppUpdateDownloader.cleanCachedApks(context); onDismiss() }) {
                Text(if (downloading) "取消" else "关闭")
            }
        }
    )
}
