package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.settings.GridSchemaGenerator
import com.kingzcheung.xime.settings.KeyboardLayoutApplier
import com.kingzcheung.xime.settings.KeyboardLayoutPreset
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * 键盘布局设置（二级页）：键数 + 字母排列 + 基础方案，改完点「部署」才生效。
 *
 * 14/17/18 键按所选基础方案（全拼/双拼）派生合并键方案；部署完成后**替换当前方案**，
 * 上一套方案的编译产物会被删掉（同一时间只有一个方案在真正使用）。
 */
@Composable
fun KeyboardLayoutSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    // 异体键盘只从内置基础方案派生：导入方案（哪怕正在使用）不作为基础方案初值
    val savedBase = SettingsPreferences.getKeyboardLayoutBaseSchema(context)
        .takeIf { it in GridSchemaGenerator.BASE_SCHEMA_IDS }
        ?: SettingsPreferences.getCurrentSchema(context)
            .takeIf { it in GridSchemaGenerator.BASE_SCHEMA_IDS }
        ?: "rime_ice"

    var layout by remember { mutableStateOf(SettingsPreferences.getKeyboardLayoutPreset(context)) }
    var order by remember { mutableStateOf(SettingsPreferences.getKeyboardLetterOrder(context)) }
    var base by remember { mutableStateOf(savedBase) }
    var deploying by remember { mutableStateOf(false) }
    var resultText by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    // 基础方案：只列磁盘上真有文件的（全拼 + 内置双拼，用户导入的第三方双拼也在内）
    val bases = remember {
        val rimeDir = SchemaManager.getRimeDir(context)
        GridSchemaGenerator.BASE_SCHEMA_IDS
            .filter { File(rimeDir, it + ".schema.yaml").isFile }
            .map { it to (SchemaManager.getSchemaDisplayName(context, it) ?: it) }
    }
    val baseName = bases.firstOrNull { it.first == base }?.second ?: base

    fun select(newLayout: String, newOrder: String, newBase: String) {
        layout = newLayout
        order = newOrder
        base = newBase
        resultText = null
        SettingsPreferences.setKeyboardLayoutPreset(context, newLayout)
        SettingsPreferences.setKeyboardLetterOrder(context, newOrder)
        SettingsPreferences.setKeyboardLayoutBaseSchema(context, newBase)
    }

    SettingsPageScaffold(
        title = tr(R.string.s_jianpanbujushezhi),
        onBack = onBack,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.GroupGap)
        ) {
            item {
                SettingsGroup(title = tr(R.string.s_jianpanjianshu), content = {
                    LayoutChoiceRow(
                        title = tr(R.string.s_jianpan_26jian),
                        subtitle = tr(R.string.s_jianpan_26jian_fu),
                        selected = layout == KeyboardLayoutPreset.LAYOUT_26,
                        onClick = { select(KeyboardLayoutPreset.LAYOUT_26, order, base) }
                    )
                    listOf(
                        KeyboardLayoutPreset.LAYOUT_14 to (R.string.s_jianpan_14jian to R.string.s_jianpan_14jian_fu),
                        KeyboardLayoutPreset.LAYOUT_17 to (R.string.s_jianpan_17jian to R.string.s_jianpan_17jian_fu),
                        KeyboardLayoutPreset.LAYOUT_18 to (R.string.s_jianpan_18jian to R.string.s_jianpan_18jian_fu),
                    ).forEach { (id, texts) ->
                        LayoutChoiceRow(
                            title = tr(texts.first),
                            subtitle = tr(texts.second),
                            selected = layout == id,
                            onClick = { select(id, order, base) }
                        )
                    }
                })
            }
            item {
                SettingsGroup(title = tr(R.string.s_zimupaile), content = {
                    LayoutChoiceRow(
                        title = tr(R.string.s_paile_qwerty),
                        subtitle = tr(R.string.s_paile_qwerty_fu),
                        selected = order == KeyboardLayoutPreset.ORDER_QWERTY,
                        onClick = { select(layout, KeyboardLayoutPreset.ORDER_QWERTY, base) }
                    )
                    LayoutChoiceRow(
                        title = tr(R.string.s_paile_abcde),
                        subtitle = tr(R.string.s_paile_abcde_fu),
                        selected = order == KeyboardLayoutPreset.ORDER_ABCDE,
                        onClick = { select(layout, KeyboardLayoutPreset.ORDER_ABCDE, base) }
                    )
                })
            }
            item {
                SettingsGroup(title = tr(R.string.s_jichufangan), content = {
                    Text(
                        text = if (layout == KeyboardLayoutPreset.LAYOUT_26) {
                            tr(R.string.s_jichufangan_26_fu)
                        } else {
                            tr(R.string.s_jichufangan_fu)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)
                    )
                    bases.forEach { (id, name) ->
                        LayoutChoiceRow(
                            title = name,
                            subtitle = id,
                            selected = base == id,
                            onClick = { select(layout, order, id) }
                        )
                    }
                })
            }
            item {
                SettingsGroup(content = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !deploying,
                            onClick = {
                                scope.launch {
                                    deploying = true
                                    resultText = null
                                    val target = withContext(Dispatchers.IO) {
                                        KeyboardLayoutApplier.apply(context, layout, order, base)
                                    }
                                    deploying = false
                                    resultText = if (target == null) {
                                        tr(R.string.s_bushushibai)
                                    } else {
                                        tr(R.string.s_bushuwancheng) + "：" +
                                            (SchemaManager.getSchemaDisplayName(context, target) ?: target)
                                    }
                                }
                            }
                        ) {
                            if (deploying) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(18.dp),
                                    strokeWidth = 2.dp,
                                    color = MaterialTheme.colorScheme.onPrimary,
                                )
                                Text(
                                    text = "  " + tr(R.string.s_bushuzhong),
                                    style = MaterialTheme.typography.labelLarge,
                                )
                            } else {
                                Text(tr(R.string.s_bushufangan))
                            }
                        }
                        val msg = resultText
                        if (msg != null) {
                            Text(
                                text = msg,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        } else {
                            Text(
                                text = tr(R.string.s_jichufangan_tishi, layout, baseName),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                })
            }
        }
    }
}

@Composable
private fun LayoutChoiceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
                color = if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (selected) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
            )
        }
    }
}
