package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.FontImporter
import com.kingzcheung.xime.settings.FontPreferences
import com.kingzcheung.xime.settings.FontSlot
import com.kingzcheung.xime.settings.KeysConfigHelper
import com.kingzcheung.xime.ui.keyboard.AppFonts

/**
 * 字体设置：导入一次，全局生效。
 *
 * 一次导入即同时写入五个槽位（按键主字 / 字根 / 候选词 / 注释 / 界面），
 * 因此键盘按键、候选栏、设置页等所有文字都换成同一款字体；文件按 SHA 去重，
 * 只占一份空间。清除时五个槽位一起清掉，回退系统默认。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var currentPath by remember { mutableStateOf(currentGlobalFont(context)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val old = currentPath
            val imported = FontImporter.import(context, uri)
            // 一次导入 → 五个槽位全部指向同一个文件
            FontSlot.entries.forEach { FontPreferences.set(context, it, imported.relativePath) }
            if (old.isNotBlank() && old != imported.relativePath) FontImporter.delete(context, old)
            imported.relativePath
        }.onSuccess { path ->
            message = null
            currentPath = path
            KeysConfigHelper.reloadFonts(context)
            version++
        }.onFailure { error ->
            message = error.message ?: "导入失败"
        }
    }

    fun clearFont() {
        val old = currentPath
        FontPreferences.clearAll(context)
        if (old.isNotBlank()) FontImporter.delete(context, old)
        currentPath = ""
        KeysConfigHelper.reloadFonts(context)
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = if (currentPath.isBlank()) tr(R.string.s_xitongmoren)
                                else FontImporter.displayName(currentPath),
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr(R.string.s_ziti_yingyongfanwei),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (currentPath.isNotBlank()) {
                            TextButton(onClick = { clearFont() }) {
                                Text(tr(R.string.s_qingchu))
                            }
                        }
                        TextButton(onClick = {
                            picker.launch(
                                arrayOf(
                                    "font/ttf", "font/otf", "font/collection",
                                    "application/octet-stream", "*/*"
                                )
                            )
                        }) {
                            Text(if (currentPath.isBlank()) tr(R.string.s_daoru) else tr(R.string.s_genguan))
                        }
                    }
                }
            }

            item { FontPreviewCard(version = version) }

            item {
                message?.let { text ->
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                OutlinedButton(
                    onClick = {
                        FontPreferences.clearAll(context)
                        currentPath = ""
                        message = null
                        KeysConfigHelper.reloadFonts(context)
                        version++
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(tr(R.string.s_huifumorenziti))
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }
}

/** 五个槽位当前是否指向同一款字体；不一致时以按键主字为准（历史多槽位配置的兼容）。 */
private fun currentGlobalFont(context: android.content.Context): String =
    FontPreferences.get(context, FontSlot.KEY)

@Composable
private fun FontPreviewCard(version: Int) {
    val keyFamily: FontFamily = remember(version) { AppFonts.keyFontFamily }
    val candidateFamily: FontFamily = remember(version) { AppFonts.candidateFontFamily }

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
                text = "曦码输入法 你好世界",
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
