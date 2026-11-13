package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/**
 * 设置分组：分组标题 + 紧贴其下的设置行，直接平铺在页面底色上（GKD 风格）。
 *
 * 不用卡片包裹、不画分隔线：分组靠"主色标题 + 上下间距"区分。
 */
@Composable
fun SettingsGroup(
    title: String? = null,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        if (title != null) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(SettingsGroupHeaderPadding),
            )
        }
        Column(modifier = Modifier.fillMaxWidth(), content = { content() })
    }
}

/** 需要"内容块"时用的卡片（规则/预览这类），颜色与圆角沿用 M3 的 surfaceContainer + shapes.medium。 */
@Composable
fun SettingsCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = settingsCardContainerColor(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) { content() }
    }
}

/** 卡片底色：M3 标准 Card 容器色（surfaceContainer），GKD 同样用它。 */
@Composable
internal fun settingsCardContainerColor(): androidx.compose.ui.graphics.Color =
    MaterialTheme.colorScheme.surfaceContainer
