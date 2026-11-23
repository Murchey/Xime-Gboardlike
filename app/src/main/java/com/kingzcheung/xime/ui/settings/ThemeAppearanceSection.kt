package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.FrostedGlassPreferences
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardTopStyle
import com.kingzcheung.xime.ui.theme.LiquidGlassStrength
import com.kingzcheung.xime.ui.theme.liquidGlassSample

/**
 * 高级设置抽屉：外观/材质相关设置。默认折叠，避免主题页一开门堆满预览卡片。
 */
@Composable
fun ThemeAdvancedAppearance() {
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = tr(R.string.s_waiguan_gaojishezhi),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = if (expanded) "▴" else "▾",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (expanded) ThemeAppearanceSection()
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeAppearanceSection() {
    val context = LocalContext.current
    var glassOn by remember { mutableStateOf(SettingsPreferences.isGlassEffectEnabled(context)) }
    val glassDark = MaterialTheme.colorScheme.surface.luminance() < .5f
    var backgroundOpacity by remember(glassDark) {
        mutableFloatStateOf(FrostedGlassPreferences.read(context, glassDark).backgroundOpacity)
    }
    var liquidOn by remember { mutableStateOf(SettingsPreferences.isLiquidGlassEnabled(context)) }
    var liquidStrength by remember { mutableStateOf(SettingsPreferences.getLiquidGlassStrength(context)) }
    var topStyle by remember { mutableStateOf(SettingsPreferences.getKeyboardTopStyle(context)) }
    var roundedBottom by remember { mutableStateOf(SettingsPreferences.roundedKeyboardBottom(context)) }
    val keyColor = MaterialTheme.colorScheme.surfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSection(title = tr(R.string.s_bolixiaoguo)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = tr(R.string.s_bolixiaoguo),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = tr(R.string.s_bolixiaoguo_fu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = glassOn,
                        onCheckedChange = {
                            glassOn = it
                            SettingsPreferences.setGlassEffectEnabled(context, it)
                        },
                    )
                }
                if (glassOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "背景不透明度 ${(backgroundOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = backgroundOpacity,
                        onValueChange = { v ->
                            backgroundOpacity = v
                            FrostedGlassPreferences.writeOpacities(context, glassDark, backgroundOpacity = v)
                        },
                        valueRange = 0f..1f,
                    )
                }

                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(
                            text = tr(R.string.s_yetaiboli),
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = tr(R.string.s_yetaiboli_fu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = liquidOn,
                        onCheckedChange = { on ->
                            liquidOn = on
                            SettingsPreferences.setLiquidGlassEnabled(context, on)
                            if (on) glassOn = true
                        },
                    )
                }
                if (liquidOn) {
                    Spacer(Modifier.height(10.dp))
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        LiquidGlassStrength.entries.forEach { strength ->
                            LiquidStrengthPreview(
                                strength = strength,
                                selected = liquidStrength == strength,
                                keyColor = keyColor,
                                accent = accent,
                                onClick = {
                                    liquidStrength = strength
                                    SettingsPreferences.setLiquidGlassStrength(context, strength)
                                },
                            )
                        }
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = tr(R.string.s_jianpandingbu_yangshi)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = tr(R.string.s_jianpandingbu_yangshi_fu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KeyboardTopStyle.entries.forEach { style ->
                        TopStylePreview(
                            style = style,
                            selected = topStyle == style,
                            keyColor = keyColor,
                            accent = accent,
                            onClick = {
                                topStyle = style
                                SettingsPreferences.setKeyboardTopStyle(context, style)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = tr(R.string.s_jianpandibu_yangshi)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = tr(R.string.s_jianpandibu_yangshi_fu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    BottomStylePreview(
                        rounded = true,
                        selected = roundedBottom,
                        keyColor = keyColor,
                        accent = accent,
                        onClick = {
                            roundedBottom = true
                            SettingsPreferences.setRoundedKeyboardBottom(context, true)
                        },
                    )
                    BottomStylePreview(
                        rounded = false,
                        selected = !roundedBottom,
                        keyColor = keyColor,
                        accent = accent,
                        onClick = {
                            roundedBottom = false
                            SettingsPreferences.setRoundedKeyboardBottom(context, false)
                        },
                    )
                }
            }
        }
    }
}

/** 液态玻璃强度示例。 */
@Composable
private fun LiquidStrengthPreview(
    strength: LiquidGlassStrength,
    selected: Boolean,
    keyColor: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val dark = keyColor.luminance() < 0.5f
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(keyColor.copy(alpha = 0.62f))
                .liquidGlassSample(10.dp, dark = dark, strength = strength)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(10.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 15.sp)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = strength.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 键盘顶部样式预览。 */
@Composable
private fun TopStylePreview(
    style: KeyboardTopStyle,
    selected: Boolean,
    keyColor: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 40.dp)
                .clip(style.clipShape(24.dp))
                .background(keyColor)
                .then(
                    if (style == KeyboardTopStyle.SOFT) {
                        Modifier.drawWithContent {
                            drawContent()
                            val h = 24.dp.toPx()
                            drawRect(
                                brush = Brush.verticalGradient(
                                    listOf(keyColor, keyColor.copy(alpha = 0f)),
                                    0f,
                                    h,
                                ),
                                topLeft = Offset.Zero,
                                size = androidx.compose.ui.geometry.Size(size.width, h),
                            )
                        }
                    } else {
                        Modifier
                    }
                )
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .10f),
                    shape = style.clipShape(24.dp),
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = style.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** 键盘底部样式预览。 */
@Composable
private fun BottomStylePreview(
    rounded: Boolean,
    selected: Boolean,
    keyColor: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val radius = if (rounded) 24.dp else 0.dp
    val shape = RoundedCornerShape(bottomStart = radius, bottomEnd = radius)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 40.dp)
                .clip(shape)
                .background(keyColor)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.onSurface.copy(alpha = .10f),
                    shape = shape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (rounded) "底部圆角" else "底部直角",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
