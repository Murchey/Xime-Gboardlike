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
        onChange?.invoke(spec, spec.read(store))
    }

    val visible = specs.filter { it.visibility?.evaluate(localStore) != false }

    Column(modifier = modifier) {
        visible.forEachIndexed { index, spec ->
            if (index > 0) {
                HorizontalDivider(
                    modifier = Modifier.padding(start = 16.dp),
                    thickness = 0.5.dp,
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
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
                    onValueChange = { commit(spec, SettingValue.Number(it)) }
                )
                is SettingSpec.Choice -> ChoiceSettingRow(
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = tr(spec.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            spec.descriptionRes?.let {
                Text(
                    text = tr(it),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
    }
}

@Composable
private fun SliderSettingRow(
    spec: SettingSpec.Slider,
    value: Int,
    enabled: Boolean,
    onValueChange: (Int) -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = tr(spec.titleRes),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
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
                style = MaterialTheme.typography.bodySmall,
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
            steps = ((spec.max - spec.min) / spec.step - 1).coerceAtLeast(0)
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
            .padding(16.dp)
    ) {
        Text(
            text = tr(spec.titleRes),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium
        )
        spec.descriptionRes?.let {
            Text(
                text = tr(it),
                style = MaterialTheme.typography.bodySmall,
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
