package com.kingzcheung.xime.ui.menubar

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlin.math.roundToInt

/** 放在剪贴板工具栏的垃圾桶左侧，由调用方控制面板显隐。 */
@Composable
fun RetentionQuickPanelButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onSurface,
) {
    IconButton(onClick = onClick, modifier = modifier.size(40.dp)) {
        Icon(
            imageVector = Icons.Default.Tune,
            contentDescription = tr(R.string.s_jiantiebanbaoliuguize),
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}

/**
 * 在剪贴板内容区域的 Box 顶层按需显示，占用现有 IME 窗口。
 * 关闭时应移出组合，下次打开会重新读取偏好。修改立即保存并复用现有历史清理逻辑。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RetentionQuickPanel(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.surface,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
) {
    val context = LocalContext.current
    val clipboardManager = remember(context) { ClipboardManager.getInstance(context) }
    val itemRange = SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE
    var maxItems by remember(context) {
        mutableFloatStateOf(SettingsPreferences.getClipboardMaxItems(context).toFloat())
    }
    var retentionMinutes by remember(context) {
        mutableIntStateOf(SettingsPreferences.getClipboardRetentionMinutes(context))
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = containerColor,
        contentColor = contentColor,
    ) {
        Column {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = tr(R.string.s_fanhui),
                    )
                }
                Text(
                    text = tr(R.string.s_jiantiebanbaoliuguize),
                    style = MaterialTheme.typography.titleMedium,
                )
            }
            HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = tr(R.string.s_zuiduobaoliutiaoshu),
                        style = MaterialTheme.typography.titleSmall,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = tr(R.string.s_tiaoshu, maxItems.roundToInt()),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                Slider(
                    value = maxItems,
                    onValueChange = { maxItems = it },
                    onValueChangeFinished = {
                        val count = maxItems.roundToInt().coerceIn(itemRange)
                        maxItems = count.toFloat()
                        SettingsPreferences.setClipboardMaxItems(context, count)
                        clipboardManager.enforceItemLimit()
                        clipboardManager.purgeExpired()
                    },
                    valueRange = itemRange.first.toFloat()..itemRange.last.toFloat(),
                    steps = (itemRange.last - itemRange.first) / 50 - 1,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    text = tr(R.string.s_baoliutiaoshu_tishi),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                )
                HorizontalDivider(color = contentColor.copy(alpha = 0.12f))
                Text(
                    text = tr(R.string.s_zidongqingli),
                    style = MaterialTheme.typography.titleSmall,
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    SettingsPreferences.CLIPBOARD_RETENTION_MINUTE_OPTIONS.forEach { minutes ->
                        FilterChip(
                            selected = retentionMinutes == minutes,
                            onClick = {
                                retentionMinutes = minutes
                                SettingsPreferences.setClipboardRetentionMinutes(context, minutes)
                                clipboardManager.purgeExpired()
                            },
                            label = { Text(retentionQuickLabel(minutes)) },
                        )
                    }
                }
                Text(
                    text = tr(R.string.s_zidongqingli_tishi),
                    style = MaterialTheme.typography.bodySmall,
                    color = contentColor.copy(alpha = 0.65f),
                )
            }
        }
    }
}

private fun retentionQuickLabel(minutes: Int): String = when {
    minutes <= 0 -> tr(R.string.s_buzidongqingli)
    minutes % 1440 == 0 -> tr(R.string.s_tian, minutes / 1440)
    else -> tr(R.string.s_xiaoshi, minutes / 60)
}
