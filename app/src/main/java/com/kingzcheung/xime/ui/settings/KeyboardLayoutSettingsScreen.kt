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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
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
import com.kingzcheung.xime.settings.KeyboardLayoutPreset
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.launch

/**
 * 键盘布局设置（二级页）：键数 + 字母排列。
 *
 * 14 / 17 / 18 键以 rime 方案承载：选中时按当前基础方案（全拼或任一内置双拼）
 * 派生对应的合并键方案并切换，切回 26 键恢复进入合并键布局前的方案。
 */
@Composable
fun KeyboardLayoutSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var layout by remember { mutableStateOf(SettingsPreferences.getKeyboardLayoutPreset(context)) }
    var order by remember { mutableStateOf(SettingsPreferences.getKeyboardLetterOrder(context)) }
    val scope = rememberCoroutineScope()

    /** 选布局/排列即生效：14/17/18 切到对应 rime 方案，26 键恢复进合并键前的方案。 */
    fun applyLayout(newLayout: String, newOrder: String) {
        layout = newLayout
        order = newOrder
        SettingsPreferences.setKeyboardLayoutPreset(context, newLayout)
        SettingsPreferences.setKeyboardLetterOrder(context, newOrder)
        val explicit = com.kingzcheung.xime.settings.KeyboardLayoutPreset.schemaIdFor(newLayout, newOrder)
        scope.launch(kotlinx.coroutines.Dispatchers.IO) {
            val current = SettingsPreferences.getCurrentSchema(context)
            if (explicit == null) {
                val saved = SettingsPreferences.getSchemaBeforeGrid(context)
                if (saved.isNotEmpty() && !com.kingzcheung.xime.settings.KeyboardLayoutPreset.isGridSchema(saved)) {
                    if (com.kingzcheung.xime.rime.RimeEngine.isInitialized()) {
                        com.kingzcheung.xime.rime.RimeEngine.getInstance().switchSchema(saved)
                    }
                    SettingsPreferences.setCurrentSchema(context, saved)
                }
            } else {
                // 以"当前基础方案"为准派生：全拼 → rime_ice_14jian，小鹤双拼 → double_pinyin_flypy_14jian
                val base = com.kingzcheung.xime.settings.GridSchemaGenerator.baseOf(current)
                    .takeIf { it in com.kingzcheung.xime.settings.GridSchemaGenerator.BASE_SCHEMA_IDS }
                    ?: "rime_ice"
                if (current.isNotEmpty() && !com.kingzcheung.xime.settings.KeyboardLayoutPreset.isGridSchema(current)) {
                    SettingsPreferences.setSchemaBeforeGrid(context, current)
                }
                val generated = com.kingzcheung.xime.settings.GridSchemaInstaller
                    .installAndEnable(context, base, newLayout, newOrder)
                val target = generated ?: explicit
                if (generated != null) {
                    // 新生成的方案要等部署编出 prism 才能切过去
                    com.kingzcheung.xime.rime.RimeConfigHelper.ensureDeployment(context)
                }
                if (com.kingzcheung.xime.rime.RimeEngine.isInitialized()) {
                    com.kingzcheung.xime.rime.RimeEngine.getInstance().switchSchema(target)
                }
                SettingsPreferences.setCurrentSchema(context, target)
            }
        }
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
                    Text(
                        text = tr(R.string.s_jianpanjianshu_fu),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                    LayoutChoiceRow(
                        title = tr(R.string.s_jianpan_26jian),
                        subtitle = tr(R.string.s_jianpan_26jian_fu),
                        selected = layout == KeyboardLayoutPreset.LAYOUT_26,
                        onClick = { applyLayout(KeyboardLayoutPreset.LAYOUT_26, order) }
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
                            onClick = { applyLayout(id, order) }
                        )
                    }
                    Text(
                        text = tr(R.string.s_jianpanbuju_weijieru),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 16.dp)
                    )
                })
            }
            item {
                SettingsGroup(title = tr(R.string.s_zimupaile), content = {
                    Text(
                        text = tr(R.string.s_zimupaile_fu),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp)
                    )
                    LayoutChoiceRow(
                        title = tr(R.string.s_paile_qwerty),
                        subtitle = tr(R.string.s_paile_qwerty_fu),
                        selected = order == KeyboardLayoutPreset.ORDER_QWERTY,
                        onClick = { applyLayout(layout, KeyboardLayoutPreset.ORDER_QWERTY) }
                    )
                    LayoutChoiceRow(
                        title = tr(R.string.s_paile_abcde),
                        subtitle = tr(R.string.s_paile_abcde_fu),
                        selected = order == KeyboardLayoutPreset.ORDER_ABCDE,
                        onClick = { applyLayout(layout, KeyboardLayoutPreset.ORDER_ABCDE) }
                    )
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