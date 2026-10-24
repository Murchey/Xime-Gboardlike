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
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.Switch
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
 * 字体设置：导入本地 ttf/otf/ttc，分别替换按键主字、字根、候选词、注释与整个界面。
 *
 * 导入的字体复制到 `files/rime/fonts/`，选择结果存 SharedPreferences（不写 xime.yaml），
 * 每次变更调用 [KeysConfigHelper.reloadFonts] 立即生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FontSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var version by remember { mutableIntStateOf(0) }
    var message by remember { mutableStateOf<String?>(null) }
    var pendingSlot by remember { mutableStateOf<FontSlot?>(null) }
    var uiEnabled by remember { mutableStateOf(FontPreferences.isUiFontEnabled(context)) }
    // 各槽位当前字体：从 prefs 读进状态，改字体后必须刷新（否则整行不会重组）
    var slotValues by remember { mutableStateOf(readSlots(context)) }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        val slot = pendingSlot
        pendingSlot = null
        if (uri == null || slot == null) return@rememberLauncherForActivityResult
        runCatching {
            val old = FontPreferences.get(context, slot)
            val imported = FontImporter.import(context, uri)
            FontPreferences.set(context, slot, imported.relativePath)
            val stillUsed = FontSlot.entries.any { FontPreferences.get(context, it) == old }
            if (old.isNotBlank() && old != imported.relativePath && !stillUsed) {
                FontImporter.delete(context, old)
            }
        }.onSuccess {
            message = null
            KeysConfigHelper.reloadFonts(context)
            slotValues = readSlots(context)
            version++
        }.onFailure { error ->
            message = error.message ?: "导入失败"
        }
    }

    fun launchPick(slot: FontSlot) {
        pendingSlot = slot
        picker.launch(arrayOf("font/ttf", "font/otf", "font/collection", "application/octet-stream", "*/*"))
    }

    fun clearSlot(slot: FontSlot) {
        val old = FontPreferences.get(context, slot)
        FontPreferences.clear(context, slot)
        // 多个槽位可能共用同一个字体文件，只有没人再用时才删
        val stillUsed = FontSlot.entries.any { FontPreferences.get(context, it) == old }
        if (old.isNotBlank() && !stillUsed) FontImporter.delete(context, old)
        KeysConfigHelper.reloadFonts(context)
        slotValues = readSlots(context)
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
                SettingsSection(title = tr(R.string.s_jianpanziti)) {
                    FontSlotRow(
                        title = tr(R.string.s_anjianzhuzi),
                        value = slotValues[FontSlot.KEY] ?: "",
                        onPick = { launchPick(FontSlot.KEY) },
                        onClear = { clearSlot(FontSlot.KEY) }
                    )
                    FontSlotRow(
                        title = tr(R.string.s_zigenziti),
                        value = slotValues[FontSlot.KEY_LABEL] ?: "",
                        onPick = { launchPick(FontSlot.KEY_LABEL) },
                        onClear = { clearSlot(FontSlot.KEY_LABEL) }
                    )
                    FontSlotRow(
                        title = tr(R.string.s_houxuanziti),
                        value = slotValues[FontSlot.CANDIDATE] ?: "",
                        onPick = { launchPick(FontSlot.CANDIDATE) },
                        onClear = { clearSlot(FontSlot.CANDIDATE) }
                    )
                    FontSlotRow(
                        title = tr(R.string.s_zhushiziti),
                        value = slotValues[FontSlot.COMMENT] ?: "",
                        onPick = { launchPick(FontSlot.COMMENT) },
                        onClear = { clearSlot(FontSlot.COMMENT) }
                    )
                }
            }

            item {
                SettingsSection(title = tr(R.string.s_jiemianziti)) {
                    FontSlotRow(
                        title = tr(R.string.s_jiemianziti),
                        value = slotValues[FontSlot.UI] ?: "",
                        onPick = { launchPick(FontSlot.UI) },
                        onClear = { clearSlot(FontSlot.UI) }
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(R.string.s_jiemianziti_kaiguan),
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = tr(R.string.s_jiemianziti_kaiguan_shuoming),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = uiEnabled,
                            onCheckedChange = { checked ->
                                uiEnabled = checked
                                FontPreferences.setUiFontEnabled(context, checked)
                                KeysConfigHelper.reloadFonts(context)
                                version++
                            }
                        )
                    }
                }
            }

            item {
                FontPreviewCard(version = version)
            }

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
                        uiEnabled = true
                        message = null
                        KeysConfigHelper.reloadFonts(context)
                        slotValues = readSlots(context)
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

@Composable
private fun FontSlotRow(
    title: String,
    value: String,
    onPick: () -> Unit,
    onClear: () -> Unit
) {
    val name = if (value.isBlank()) tr(R.string.s_xitongmoren) else FontImporter.displayName(value)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 16.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = name,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (value.isNotBlank()) {
            TextButton(onClick = onClear) {
                Text(tr(R.string.s_qingchu))
            }
        }
        TextButton(onClick = onPick) {
            Text(tr(R.string.s_daoru))
        }
    }
}

@Composable
private fun FontPreviewCard(version: Int) {
    // AppFonts 是单例，字体变更后靠 version 触发重读
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

/** 读取全部槽位的当前字体（相对 files/rime 的路径，空串表示未设置）。 */
private fun readSlots(context: android.content.Context): Map<FontSlot, String> =
    FontSlot.entries.associateWith { FontPreferences.get(context, it) }
