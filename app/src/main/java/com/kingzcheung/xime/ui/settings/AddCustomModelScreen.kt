package com.kingzcheung.xime.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.model.ModelStorage
import com.kingzcheung.xime.settings.CustomPredictionModel
import com.kingzcheung.xime.settings.CustomPredictionModelStore
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 添加自定义预测模型：用户填名称与标签、选本地 zip/单文件，导入到专属目录后
 * 立即出现在「智能联想 → 选择模型」列表里。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddCustomModelContent(onBack: () -> Unit, onAdded: () -> Unit = {}) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var tag by remember { mutableStateOf("自定义") }
    var description by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingUri by remember { mutableStateOf<Uri?>(null) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) pendingUri = uri
    }

    fun doImport(uri: Uri) {
        if (name.isBlank()) { message = "请先填写模型名称"; return }
        busy = true
        scope.launch {
            val id = CustomPredictionModelStore.newId()
            val dir = ModelStorage.getModelDir(context, id)
            val result = withContext(Dispatchers.IO) {
                runCatching {
                    dir.mkdirs()
                    val fileName = context.contentResolver
                        .query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
                        ?.use { if (it.moveToFirst()) it.getString(0) else null }
                        ?.lowercase() ?: ""
                    context.contentResolver.openInputStream(uri)?.use { input ->
                        if (fileName.endsWith(".zip")) {
                            java.util.zip.ZipInputStream(input.buffered()).use { zip ->
                                var e = zip.nextEntry
                                while (e != null) {
                                    if (!e.isDirectory) {
                                        val n = e.name.replace('\\', '/').substringAfterLast('/')
                                        if (n.isNotBlank() && n != "." && n != "..") {
                                            java.io.FileOutputStream(java.io.File(dir, n)).use { zip.copyTo(it) }
                                        }
                                    }
                                    zip.closeEntry(); e = zip.nextEntry
                                }
                            }
                        } else {
                            val n = if (fileName.isNotBlank()) fileName.substringAfterLast('/') else "model_int8_dynamic.onnx"
                            java.io.FileOutputStream(java.io.File(dir, n)).use { input.copyTo(it) }
                        }
                    } ?: error("无法打开所选文件")
                }
            }
            busy = false
            if (result.isSuccess) {
                val model = CustomPredictionModel(
                    id = id, name = name.trim(), tag = tag.trim().ifBlank { "自定义" },
                    description = description.trim(),
                )
                CustomPredictionModelStore.upsert(context, model)
                // 默认选中刚添加的模型，用户回到联想页即可直接使用
                SettingsPreferences.setPredictionSelectedModel(context, id)
                message = "已添加「${model.name}」，可在智能联想中选择使用"
                onAdded()
            } else {
                dir.deleteRecursively()
                message = "导入失败：${result.exceptionOrNull()?.message}"
            }
        }
    }

    if (pendingUri != null) {
        AlertDialog(
            onDismissRequest = { pendingUri = null },
            title = { Text("导入模型文件") },
            text = { Text("将导入到自定义模型「${name.ifBlank { "未命名" }}」，确定继续？") },
            confirmButton = {
                TextButton(onClick = { pendingUri?.let { doImport(it) }; pendingUri = null }) { Text("导入") }
            },
            dismissButton = { TextButton(onClick = { pendingUri = null }) { Text("取消") } }
        )
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text("添加自定义模型") },
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
            Text(
                "把你的预测模型（vocab.json + model_int8_dynamic.onnx，或包含它们的 zip）添加为自定义模型。添加后可在「智能联想 → 选择模型」中选用。",
                fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            OutlinedTextField(
                value = name, onValueChange = { name = it },
                label = { Text("模型名称") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = tag, onValueChange = { tag = it },
                label = { Text("标签（如：自训练 / 医学 / 古籍）") }, singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = description, onValueChange = { description = it },
                label = { Text("说明（可选）") },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = { picker.launch(arrayOf("*/*")) },
                enabled = !busy,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(if (busy) "导入中…" else "选择模型文件并添加")
            }
            message?.let {
                Text(it, fontSize = 13.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
            }
            Text(
                "提示：导入是复制到你自己的模型目录，不影响已有模型；删除请在「本地模型」页操作。",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
