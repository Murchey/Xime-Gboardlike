package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * 设置行的统一底座：24dp 中性图标 + 标题/摘要 + 行尾插槽。
 *
 * 与旧 SettingsItem 的差别：图标不再套 40dp 蓝色块，行高按内容保证 ≥56dp，
 * 行尾用 Slot 组合 Switch / 箭头 / 角标。
 */
@Composable
fun SettingsRow(
    title: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    role: Role? = null,
    trailing: (@Composable RowScope.() -> Unit)? = null,
) {
    val vertical = SettingsDimens.RowVerticalTwoLine
    Row(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (onClick != null) Modifier.clickable(enabled = enabled, role = role, onClick = onClick)
                else Modifier
            )
            .padding(horizontal = SettingsDimens.RowHorizontal, vertical = vertical),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (icon != null) {
            // 图标用 Material 主色（浅色蓝、深色亮蓝），与 Android 系统设置一致
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (enabled) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.primary.copy(alpha = 0.38f),
                modifier = Modifier.size(SettingsDimens.IconSize),
            )
            Spacer(Modifier.width(SettingsDimens.IconGap))
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = if (enabled) MaterialTheme.colorScheme.onSurface
                else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f),
            )
            if (summary != null) {
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (enabled) MaterialTheme.colorScheme.onSurfaceVariant
                    else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.38f),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        if (trailing != null) {
            Spacer(Modifier.width(SettingsDimens.TrailingGap))
            trailing()
        }
    }
}

/** 可点击入口行：行尾带箭头。 */
@Composable
fun SettingsNavRow(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    badge: (@Composable () -> Unit)? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        trailing = {
            if (badge != null) {
                badge()
                Spacer(Modifier.width(8.dp))
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
    )
}

/** 开关行：整行可点即可切换。 */
@Composable
fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = onClick ?: { onCheckedChange(!checked) },
        role = Role.Switch,
        trailing = {
            Switch(checked = checked, onCheckedChange = onCheckedChange, enabled = enabled)
        },
    )
}

/** 单选行：行尾 RadioButton，左侧可带图标。 */
@Composable
fun SettingsRadioRow(
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    summary: String? = null,
    enabled: Boolean = true,
) {
    SettingsRow(
        title = title,
        modifier = modifier,
        icon = icon,
        summary = summary,
        enabled = enabled,
        onClick = onClick,
        role = Role.RadioButton,
        trailing = {
            RadioButton(selected = selected, onClick = null, enabled = enabled)
        },
    )
}

/** 行尾角标（如"2 项可更新"），高亮胶囊。 */
@Composable
fun SettingsBadge(text: String, error: Boolean = true) {
    val container = if (error) MaterialTheme.colorScheme.errorContainer
    else MaterialTheme.colorScheme.primaryContainer
    val content = if (error) MaterialTheme.colorScheme.onErrorContainer
    else MaterialTheme.colorScheme.onPrimaryContainer
    Box(
        modifier = Modifier
            .clip(RoundedCornerShapeCompat.Capsule)
            .background(container)
            .padding(horizontal = 8.dp, vertical = 3.dp),
    ) {
        Text(text = text, style = MaterialTheme.typography.labelSmall, color = content)
    }
}

private object RoundedCornerShapeCompat {
    val Capsule = androidx.compose.foundation.shape.RoundedCornerShape(50)
}
