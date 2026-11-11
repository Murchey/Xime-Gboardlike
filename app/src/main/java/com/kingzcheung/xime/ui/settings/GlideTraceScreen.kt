package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import com.kingzcheung.xime.glide.GlideTraceRecorder
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.util.FileLogger
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private const val TAG = "GlideTraceData"

/**
 * 滑动轨迹数据页（开发者选项 → 滑动轨迹数据）：
 * 采集开关、最近轨迹列表、逐条标注"我实际想打出的内容"、导出 jsonl、清空。
 *
 * 标注放在这里而不是键盘里：输入法无法给自己嵌套输入，中文/汉字只能
 * 在普通 Activity 的输入框里打。键盘上的「滑动标记」按钮只负责一键开关采集。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GlideTraceScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }
    var enabled by remember { mutableStateOf(SettingsPreferences.isGlideTraceEnabled(context)) }
    var count by remember { mutableStateOf(0) }
    var sizeBytes by remember { mutableLongStateOf(0L) }
    var busy by remember { mutableStateOf(false) }
    var entries by remember { mutableStateOf<List<GlideTraceRecorder.TraceEntry>>(emptyList()) }
    var labeling by remember { mutableStateOf<GlideTraceRecorder.TraceEntry?>(null) }
    var labelText by remember { mutableStateOf("") }

    fun refresh() {
        count = GlideTraceRecorder.recordCount()
        sizeBytes = GlideTraceRecorder.sizeBytes()
        entries = GlideTraceRecorder.recentEntries()
    }

    LaunchedEffect(Unit) {
        GlideTraceRecorder.init(context)
        refresh()
    }

    val unlabeled = entries.count { it.expected.isEmpty() }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_huadongguijishuju)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = tr(R.string.s_fanhui))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            SettingsItem(
                icon = Icons.Default.SaveAlt,
                title = tr(R.string.s_guijikaiguan),
                onClick = {},
                trailing = {
                    Switch(
                        checked = enabled,
                        onCheckedChange = { value ->
                            enabled = value
                            SettingsPreferences.setGlideTraceEnabled(context, value)
                            GlideTraceRecorder.init(context)
                            GlideTraceRecorder.enabled = value
                            refresh()
                        },
                    )
                },
            )
            Text(
                text = tr(R.string.s_huadongguijishuju_fu),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = tr(R.string.s_yicaiji_tiao, count) + " · " + formatSize(sizeBytes) +
                    if (unlabeled > 0) " · " + tr(R.string.s_daibiaojishu, unlabeled) else "",
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Text(
                text = tr(R.string.s_biaojishishuoming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            if (entries.isEmpty()) {
                Text(
                    text = tr(R.string.s_zanwuguidingshuru),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                )
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    items(entries, key = { it.id }) { entry ->
                        TraceRowCard(entry) {
                            labeling = entry
                            labelText = entry.expected
                        }
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                shareExport(context, snackbarHostState)
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = count > 0 && !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.IosShare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_fenxiang_json))
                }
                OutlinedButton(
                    onClick = {
                        busy = true
                        scope.launch {
                            try {
                                saveExport(context, snackbarHostState)
                                refresh()
                            } finally {
                                busy = false
                            }
                        }
                    },
                    enabled = count > 0 && !busy,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(Icons.Filled.SaveAlt, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_baocundaoxiazai))
                }
            }
            OutlinedButton(
                onClick = {
                    GlideTraceRecorder.clearSamples()
                    refresh()
                },
                enabled = count > 0,
                modifier = Modifier.padding(bottom = 12.dp),
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(R.string.s_qingkongyangben))
            }
        }
    }

    labeling?.let { target ->
        AlertDialog(
            onDismissRequest = { labeling = null },
            title = { Text(tr(R.string.s_biaozhuqiwangwenben)) },
            text = {
                Column {
                    Text(
                        text = tr(R.string.s_jiechubianma_, target.code.ifEmpty { tr(R.string.s_weijiechubianma) }),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = labelText,
                        onValueChange = { labelText = it },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(tr(R.string.s_woxiangdade)) },
                        singleLine = false,
                        maxLines = 3,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (labelText.isNotBlank()) {
                            GlideTraceRecorder.label(target.id, labelText.trim(), "mark")
                        }
                        labeling = null
                        refresh()
                    },
                ) { Text(tr(R.string.s_baocun)) }
            },
            dismissButton = {
                TextButton(onClick = { labeling = null }) { Text(tr(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun TraceRowCard(entry: GlideTraceRecorder.TraceEntry, onClick: () -> Unit) {
    val labeled = entry.expected.isNotEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .clickable { onClick() }
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatTime(entry.timeMs) + "  " +
                    entry.code.ifEmpty { tr(R.string.s_weijiechubianma) },
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (labeled) tr(R.string.s_yibiaoj_, entry.expected) else tr(R.string.s_weibiaoj_dianji),
                fontSize = 12.sp,
                color = if (labeled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Text(
            text = tr(R.string.s_biaozhu),
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

private fun formatTime(ms: Long): String =
    SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(ms))

private suspend fun shareExport(
    context: android.content.Context,
    snackbar: SnackbarHostState,
) {
    val file = GlideTraceRecorder.exportCopy()
    if (file == null) {
        snackbar.showSnackbar(tr(R.string.s_zanwucaijiyangben))
        return
    }
    try {
        val uri = FileProvider.getUriForFile(context, context.packageName + ".fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_SUBJECT, tr(R.string.s_huadongguijishuju))
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            clipData = android.content.ClipData.newRawUri(null, uri)
        }
        context.startActivity(Intent.createChooser(intent, tr(R.string.s_fenxiangcaijishuju)))
    } catch (e: Exception) {
        Log.e(TAG, "shareExport failed", e)
        FileLogger.e(TAG, "shareExport failed", e)
        snackbar.showSnackbar(tr(R.string.s_daochushibai_2, e.message))
    }
}

private suspend fun saveExport(
    context: android.content.Context,
    snackbar: SnackbarHostState,
) {
    val file = GlideTraceRecorder.exportCopy()
    if (file == null) {
        snackbar.showSnackbar(tr(R.string.s_zanwucaijiyangben))
        return
    }
    try {
        val path = withContext(Dispatchers.IO) { saveToDownloads(context, file) }
        snackbar.showSnackbar(tr(R.string.s_yibaocundao, path))
    } catch (e: Exception) {
        Log.e(TAG, "saveExport failed", e)
        FileLogger.e(TAG, "saveExport failed", e)
        snackbar.showSnackbar(tr(R.string.s_baocunshibai_2, e.message))
    }
}

/** 与手写采集同口径：Q+ 走 MediaStore.Downloads，更早版本直接写公共目录。 */
private fun saveToDownloads(context: android.content.Context, file: File): String {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            // DISPLAY_NAME 已带 .jsonl：用 octet-stream，MediaStore 才不会追加 .json 变成
            // xime_glide_xxx.jsonl.json（离线脚本按 .jsonl 匹配）
            put(MediaStore.Downloads.DISPLAY_NAME, file.name)
            put(MediaStore.Downloads.MIME_TYPE, "application/octet-stream")
            put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        }
        val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
            ?: throw IllegalStateException("MediaStore insert failed")
        context.contentResolver.openOutputStream(uri)?.use { output ->
            file.inputStream().use { it.copyTo(output) }
        } ?: throw IllegalStateException("openOutputStream failed")
        "Download/" + file.name
    } else {
        @Suppress("DEPRECATION")
        val dest = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            file.name,
        )
        file.copyTo(dest, overwrite = true)
        "Download/" + file.name
    }
}
