package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.content.ContentValues
import android.content.Intent
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.IosShare
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import java.util.Locale

private const val TAG = "GlideTraceData"

/**
 * 滑动轨迹数据页（开发者选项 → 滑动轨迹数据）：
 * 采集开关、条数与体积、导出 jsonl（分享 / 保存到 Downloads）、清空。
 *
 * 导出内容是解码器的离线评测输入：每条记录含轨迹点 (x,y,t)、键位几何快照、
 * 本次手势的编码前缀、几何解码候选，以及用户确认后的 expected 文本。
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

    fun refresh() {
        count = GlideTraceRecorder.recordCount()
        sizeBytes = GlideTraceRecorder.sizeBytes()
    }

    LaunchedEffect(Unit) {
        GlideTraceRecorder.init(context)
        refresh()
    }

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
                .padding(horizontal = 16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(12.dp),
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
                text = tr(R.string.s_yicaiji_tiao, count) + " · " + formatSize(sizeBytes),
                fontSize = 13.sp,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(4.dp))
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
            ) {
                Icon(Icons.Filled.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(tr(R.string.s_qingkongyangben))
            }
        }
    }
}

private fun formatSize(bytes: Long): String = when {
    bytes >= 1024 * 1024 -> String.format(Locale.US, "%.1f MB", bytes / 1024.0 / 1024.0)
    bytes >= 1024 -> String.format(Locale.US, "%.1f KB", bytes / 1024.0)
    else -> "$bytes B"
}

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
