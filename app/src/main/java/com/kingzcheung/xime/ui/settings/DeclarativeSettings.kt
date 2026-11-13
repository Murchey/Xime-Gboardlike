package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.FilterChip
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.settings.declarative.ChoiceOption
import com.kingzcheung.xime.settings.declarative.SettingSpec
import com.kingzcheung.xime.settings.declarative.SettingValue
import com.kingzcheung.xime.settings.declarative.SettingsStore
import com.kingzcheung.xime.settings.declarative.read
import com.kingzcheung.xime.settings.declarative.readRaw
import com.kingzcheung.xime.settings.declarative.write
import kotlin.math.roundToInt

/**
 * 声明式设置渲染层：按 [SettingSpec] 列表自动生成开关 / 滑杆 / 单选行。
 *
 * - 可见性依赖（[SettingSpec.visibility]）在本地快照上求值，切换开关后依赖项即时增删；
 * - 写入同时落到 [store]（持久化）与快照（重组），并回调 [onChange] 供页面做副作用。
 */
@Composable
fun DeclarativeSettingItems(
    specs: List<SettingSpec>,
    store: SettingsStore,
    modifier: Modifier = Modifier,
    onChange: ((SettingSpec, SettingValue) -> Unit)? = null,
    /** 滑杆拖动结束回调（用于需要"松手才生效"的副作用，如重建 Schema）。 */
    onChangeFinished: ((SettingSpec, SettingValue) -> Unit)? = null,
    /**
     * 联动写入：提交某项时顺带写入其他键（如"打开滑动输入"同时打开"神经解码"）。
     * 返回值会一并写进存储与本地快照，被联动的那一行界面立即刷新。
     */
    linkedWrites: ((SettingSpec, SettingValue) -> List<Pair<String, SettingValue>>)? = null,
) {
    val snapshot = remember(specs) {
        mutableStateMapOf<String, SettingValue>().apply {
            specs.forEach { put(it.key, it.readRaw(store)) }
        }
    }
    val localStore = remember(snapshot) { SnapshotSettingsStore(snapshot) }

    val commit: (SettingSpec, SettingValue) -> Unit = { spec, value ->
        spec.write(store, value)
        snapshot[spec.key] = spec.readRaw(store)
        linkedWrites?.invoke(spec, spec.read(store))?.forEach { (key, linked) ->
            when (linked) {
                is SettingValue.Flag -> store.putFlag(key, linked.value)
                is SettingValue.Number -> store.putNumber(key, linked.value)
                is SettingValue.Text -> store.putText(key, linked.value)
            }
            snapshot[key] = linked
        }
        onChange?.invoke(spec, spec.read(store))
    }

    val visible = specs.filter { it.visibility?.evaluate(localStore) != false }

    Column(modifier = modifier) {
        visible.forEachIndexed { index, spec ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = SettingsDimens.RowHorizontal),
                    thickness = SettingsDimens.DividerThickness,
                    color = MaterialTheme.colorScheme.outlineVariant
                )
            }
            val enabled = spec.enabledWhen?.evaluate(localStore) != false
            when (spec) {
                is SettingSpec.Switch -> SwitchSettingRow(
                    spec = spec,
                    // 禁用且声明了 offWhenDisabled 时显示为关（联动项"不生效"）
                    checked = if (!enabled && spec.offWhenDisabled) false
                    else (spec.read(localStore) as SettingValue.Flag).value,
                    enabled = enabled,
                    onCheckedChange = { commit(spec, SettingValue.Flag(it)) }
                )
                is SettingSpec.Slider -> SliderSettingRow(
                    spec = spec,
                    value = localStore.number(spec.key, spec.coerce(spec.default)),
                    enabled = enabled,
                    onValueChange = { commit(spec, SettingValue.Number(it)) },
                    onValueChangeFinished = {
                        onChangeFinished?.invoke(
                            spec,
                            SettingValue.Number(localStore.number(spec.key, spec.coerce(spec.default)))
                        )
                    }
                )
                is SettingSpec.Choice -> ChoiceSettingRow(
                    spec = spec,
                    selected = localStore.text(spec.key, spec.default),
                    enabled = enabled,
                    onSelect = { commit(spec, SettingValue.Text(it)) }
                )
                is SettingSpec.Menu -> MenuSettingRow(
                    spec = spec,
                    selected = localStore.text(spec.key, spec.default),
                    enabled = enabled,
                    onSelect = { commit(spec, SettingValue.Text(it)) }
                )
            }
        }
    }
}

@Composable
private fun SwitchSettingRow(
    spec: SettingSpec.Switch,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    SettingsSwitchRow(
        title = tr(spec.titleRes),
        summary = spec.descriptionRes?.let { tr(it) },
        checked = checked,
        onCheckedChange = onCheckedChange,
        enabled = enabled,
    )
}

@Composable
private fun SliderSettingRow(
    spec: SettingSpec.Slider,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
    onValueChangeFinished: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDimens.RowHorizontal, vertical = SettingsDimens.RowVerticalTwoLine)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr(spec.titleRes),
                style = MaterialTheme.typography.bodyLarge
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = sliderValueLabel(spec, value),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        spec.descriptionRes?.let {
            Text(
                text = tr(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Slider(
            value = value.toFloat(),
            enabled = enabled,
            onValueChange = { raw ->
                val stepped = ((raw / spec.step).roundToInt() * spec.step).coerceIn(spec.min, spec.max)
                onValueChange(stepped)
            },
            valueRange = spec.min.toFloat()..spec.max.toFloat(),
            steps = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0),
            onValueChangeFinished = onValueChangeFinished
        )
    }
}

@Composable
private fun ChoiceSettingRow(
    spec: SettingSpec.Choice,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDimens.RowHorizontal, vertical = SettingsDimens.RowVerticalTwoLine)
    ) {
        Text(
            text = tr(spec.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        spec.descriptionRes?.let {
            Text(
                text = tr(it),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            spec.options.forEach { option: ChoiceOption ->
                FilterChip(
                    selected = selected == option.value,
                    enabled = enabled,
                    onClick = { onSelect(option.value) },
                    label = { Text(tr(option.labelRes)) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                )
            }
        }
    }
}

@Composable
private fun sliderValueLabel(spec: SettingSpec.Slider, value: Int): String = when {
    value == 0 && spec.zeroLabelRes != null -> tr(spec.zeroLabelRes)
    spec.suffix != null -> "$value${spec.suffix}"
    else -> value.toString()
}

/** 渲染层本地快照：让可见性依赖在本次组合内即时生效，不额外触发磁盘读。 */
private class SnapshotSettingsStore(
    private val values: SnapshotStateMap<String, SettingValue>
) : SettingsStore {
    override fun flag(key: String, default: Boolean): Boolean =
        (values[key] as? SettingValue.Flag)?.value ?: default

    override fun number(key: String, default: Int): Int =
        (values[key] as? SettingValue.Number)?.value ?: default

    override fun text(key: String, default: String): String =
        (values[key] as? SettingValue.Text)?.value ?: default

    override fun putFlag(key: String, value: Boolean) {
        values[key] = SettingValue.Flag(value)
    }

    override fun putNumber(key: String, value: Int) {
        values[key] = SettingValue.Number(value)
    }

    override fun putText(key: String, value: String) {
        values[key] = SettingValue.Text(value)
    }
}

/**
 * 悬浮菜单行：整行可点，点击后在行的锚点处弹出悬浮 panel 选择。
 *
 * 相比平铺 chips，选项多时列表更清爽；选中项左侧带勾。
 */
@Composable
private fun MenuSettingRow(
    spec: SettingSpec.Menu,
    selected: String,
    enabled: Boolean,
    onSelect: (String) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val current = spec.options.firstOrNull { it.value == selected } ?: spec.options.first()
    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = true }
                .padding(horizontal = SettingsDimens.RowHorizontal, vertical = SettingsDimens.RowVerticalTwoLine),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(spec.titleRes),
                    style = MaterialTheme.typography.bodyLarge
                )
                spec.descriptionRes?.let {
                    Text(
                        text = tr(it),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Text(
                text = tr(current.labelRes),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.width(4.dp))
            Icon(
                imageVector = Icons.Filled.KeyboardArrowDown,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            spec.options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(tr(option.labelRes)) },
                    leadingIcon = {
                        if (option.value == selected) {
                            Icon(
                                imageVector = Icons.Filled.Check,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    },
                    onClick = {
                        onSelect(option.value)
                        expanded = false
                    },
                )
            }
        }
    }
}
