package com.kingzcheung.xime.ui.settings

import android.graphics.Typeface
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.settings.FontImporter
import com.kingzcheung.xime.settings.FontPreferences
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.AppFonts
import java.io.File

/**
 * 字体设置：在「系统默认 / 内置字体 / 已导入字体」之间选择，一次选择全局生效。
 *
 * 五个字体槽位（按键主字 / 字根 / 候选词 / 注释 / 界面）始终指向同一款字体；
 * 导入的字体按 SHA-256 去重，文件保存在应用私有目录。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf(FontPreferences.currentGlobalSelection(context)) }
    var pendingDelete by remember { mutableStateOf<String?>(null) }
    val importedFonts = remember(version) { FontImporter.list(context) }

    fun applySelection(value: String) {
        FontPreferences.setAll(context, value)
        currentPath = value
        message = null
        KeysConfigHelper.reloadFonts(context)
        version++
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            FontImporter.import(context, uri).relativePath
        }.onSuccess { path ->
            applySelection(path)
        }.onFailure { error ->
            message = error.message ?: "导入失败"
        }
    }

    fun deleteFont(path: String) {
        if (currentPath == path) {
            FontPreferences.setAll(context, AppFonts.BUILTIN_SENTINEL)
            currentPath = AppFonts.BUILTIN_SENTINEL
            KeysConfigHelper.reloadFonts(context)
        }
        FontImporter.delete(context, path)
        pendingDelete = null
        message = null
        version++
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_zitiguanli)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr(R.string.s_fanhui)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = tr(R.string.s_zitishuoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                SettingsSection(title = tr(R.string.s_quanjuziti)) {
                    FontOptionRow(
                        title = tr(R.string.s_xitongmorenziti),
                        subtitle = tr(R.string.s_xitongmorenziti_fu),
                        selected = currentPath == AppFonts.SYSTEM_SENTINEL,
                        onClick = { applySelection(AppFonts.SYSTEM_SENTINEL) },
                        fontFamily = FontFamily.Default,
                    )
                    FontDivider()
                    FontOptionRow(
                        title = tr(R.string.s_neizhiziti),
                        subtitle = tr(R.string.s_neizhiziti_fu),
                        selected = currentPath.isBlank() || currentPath == AppFonts.BUILTIN_SENTINEL,
                        onClick = { applySelection(AppFonts.BUILTIN_SENTINEL) },
                        fontFamily = remember {
                            FontFamily(Font(AppFonts.BUILTIN_FONT_ASSET, context.assets))
                        },
                    )
                    importedFonts.forEach { font ->
                        FontDivider()
                        FontOptionRow(
                            title = FontImporter.displayName(font.relativePath),
                            subtitle = tr(R.string.s_yidaoruziti_fu),
                            selected = currentPath == font.relativePath,
                            onClick = { applySelection(font.relativePath) },
                            fontFamily = remember(font.relativePath) {
                                runCatching {
                                    FontFamily(
                                        Typeface.createFromFile(
                                            File(context.filesDir, "rime/${font.relativePath}")
                                        )
                                    )
                                }.getOrNull()
                            },
                            trailing = {
                                IconButton(onClick = { pendingDelete = font.relativePath }) {
                                    Icon(
                                        imageVector = Icons.TwoTone.DeleteOutline,
                                        contentDescription = tr(R.string.s_shanchu)
                                    )
                                }
                            },
                        )
                    }
                    FontDivider()
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                picker.launch(
                                    arrayOf(
                                        "font/ttf", "font/otf", "font/collection",
                                        "application/octet-stream", "*/*"
                                    )
                                )
                            }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.Add,
                            contentDescription = null,
                            modifier = Modifier.size(22.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.size(12.dp))
                        Text(
                            text = tr(R.string.s_daoruziti),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            item { FontPreviewCard(version = version, currentPath = currentPath) }

            message?.let { text ->
                item {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    pendingDelete?.let { path ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr(R.string.s_shanchuziti)) },
            text = { Text(tr(R.string.s_shanchuziti_queren, FontImporter.displayName(path))) },
            confirmButton = {
                TextButton(onClick = { deleteFont(path) }) {
                    Text(tr(R.string.s_shanchu), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr(R.string.s_quxiao)) }
            }
        )
    }
}

@Composable
private fun FontDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(start = 16.dp),
        thickness = 0.5.dp,
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
    )
}

@Composable
private fun FontOptionRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
    fontFamily: FontFamily?,
    trailing: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(modifier = Modifier.size(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontFamily = fontFamily,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun FontPreviewCard(version: Int, currentPath: String) {
    val context = LocalContext.current
    val keyFamily: FontFamily = remember(version, currentPath) {
        when (currentPath) {
            AppFonts.SYSTEM_SENTINEL -> FontFamily.Default
            "", AppFonts.BUILTIN_SENTINEL -> runCatching {
                FontFamily(Font(AppFonts.BUILTIN_FONT_ASSET, context.assets))
            }.getOrDefault(FontFamily.Default)
            else -> runCatching {
                FontFamily(Typeface.createFromFile(File(context.filesDir, "rime/$currentPath")))
            }.getOrDefault(FontFamily.Default)
        }
    }
    val candidateFamily: FontFamily = keyFamily

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr(R.string.s_zitiyulan),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "abcdefg ABCDEFG 12345",
                fontFamily = keyFamily,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "XimeGboard 输入法 你好世界",
                fontFamily = candidateFamily,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = tr(R.string.s_zitiyulan_jiemian),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
