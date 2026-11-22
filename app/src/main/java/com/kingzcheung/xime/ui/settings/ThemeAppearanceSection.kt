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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import com.kingzcheung.xime.ui.theme.KeyShape
import com.kingzcheung.xime.ui.theme.KeyboardTopStyle
import com.kingzcheung.xime.ui.theme.LiquidGlassStrength
import com.kingzcheung.xime.ui.theme.LocalMaterialPalette
import com.kingzcheung.xime.ui.theme.MaterialLevel
import com.kingzcheung.xime.ui.theme.MaterialPalette
import com.kingzcheung.xime.ui.theme.VisualStyle
import com.kingzcheung.xime.ui.theme.liquidGlassSample
import com.kingzcheung.xime.ui.theme.visualMaterial

/**
 * 主题与定制页的「外观与材质」分区：按键形状、视觉样式、玻璃效果。
 *
 * 每一项都用**真实渲染**做预览（示例键帽按所选圆角/材质画），不是纯文字选项；
 * 点选直接改 Compose 状态（KeyShapes / VisualStyles / GlassEffect），键盘立即重绘。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ThemeAppearanceSection() {
    val context = LocalContext.current
    var keyShape by remember { mutableStateOf(SettingsPreferences.getKeyShape(context)) }
    var visualStyle by remember { mutableStateOf(SettingsPreferences.getVisualStyle(context)) }
    var glassOn by remember { mutableStateOf(SettingsPreferences.isGlassEffectEnabled(context)) }
    var liquidOn by remember { mutableStateOf(SettingsPreferences.isLiquidGlassEnabled(context)) }
    var liquidStrength by remember { mutableStateOf(SettingsPreferences.getLiquidGlassStrength(context)) }
    var topStyle by remember { mutableStateOf(SettingsPreferences.getKeyboardTopStyle(context)) }
    var roundedBottom by remember { mutableStateOf(SettingsPreferences.roundedKeyboardBottom(context)) }
    // 玻璃材质参数：按当前深浅读取，滑块直接写回 FrostedGlassPreferences
    val glassDark = MaterialTheme.colorScheme.surface.luminance() < .5f
    var keyOpacity by remember(glassDark) {
        mutableFloatStateOf(FrostedGlassPreferences.read(context, glassDark).keyOpacity)
    }

    // 预览键使用的"键面"颜色：与设置页一致的浅色/深色中性色
    val keyColor = MaterialTheme.colorScheme.surfaceVariant
    val keyText = MaterialTheme.colorScheme.onSurfaceVariant
    val accent = MaterialTheme.colorScheme.primary

    Column(modifier = Modifier.fillMaxWidth()) {
        SettingsSection(title = tr(R.string.s_anjianxingzhuang)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = tr(R.string.s_anjianxingzhuang_fu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    KeyShape.entries.forEach { shape ->
                        KeyShapePreview(
                            shape = shape,
                            selected = keyShape == shape,
                            keyColor = keyColor,
                            keyText = keyText,
                            accent = accent,
                            onClick = {
                                keyShape = shape
                                SettingsPreferences.setKeyShape(context, shape)
                            },
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        SettingsSection(title = tr(R.string.s_shijueyangshi)) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Text(
                    text = tr(R.string.s_shijueyangshi_fu),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    VisualStyle.offered.forEach { style ->
                        VisualStylePreview(
                            style = style,
                            selected = visualStyle == style,
                            keyColor = keyColor,
                            keyText = keyText,
                            accent = accent,
                            onClick = {
                                visualStyle = style
                                SettingsPreferences.setVisualStyle(context, style)
                            },
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
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
                // 玻璃效果本身也画一个示例键：半透明键面 + 玻璃高光/亮边
                Spacer(Modifier.height(8.dp))
                GlassPreviewSample(enabled = glassOn, keyColor = keyColor, keyText = keyText, accent = accent)
                if (glassOn) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "键面不透明度 ${(keyOpacity * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Slider(
                        value = keyOpacity,
                        onValueChange = { v ->
                            keyOpacity = v
                            FrostedGlassPreferences.writeOpacities(context, glassDark, keyOpacity = v)
                        },
                        valueRange = 0f..1f,
                    )
                }

                Spacer(Modifier.height(12.dp))
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
                            // 液态玻璃会把玻璃效果一起打开
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
                                keyText = keyText,
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

/** 键盘底部样式预览：示例块按是否圆角画下沿。 */
@Composable
private fun BottomStylePreview(
    rounded: Boolean,
    selected: Boolean,
    keyColor: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val radius = if (rounded) 12.dp else 0.dp
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
                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    shape = shape,
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = if (rounded) "底部圆角" else "底部直角",
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 键盘顶部样式预览：示例块按该样式的上沿画（圆角 / 直角 / 渐变柔化）。 */
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
                .clip(style.clipShape(12.dp))
                .background(keyColor)
                .then(
                    if (style == KeyboardTopStyle.SOFT) {
                        Modifier.drawWithContent {
                            drawContent()
                            val h = 12.dp.toPx()
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
                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    shape = style.clipShape(12.dp),
                ),
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = style.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 按键形状预览：一枚示例键，圆角按该形状钳制后真实绘制。 */
@Composable
private fun KeyShapePreview(
    shape: KeyShape,
    selected: Boolean,
    keyColor: Color,
    keyText: Color,
    accent: Color,
    onClick: () -> Unit,
) {
    val radius = shape.radius(6.dp)
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(4.dp),
    ) {
        Box(
            modifier = Modifier
                .size(width = 64.dp, height = 44.dp)
                .clip(RoundedCornerShape(radius))
                .background(keyColor)
                .border(
                    width = if (selected) 2.dp else 1.dp,
                    color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(radius),
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text("A", color = keyText, fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = shape.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 视觉样式预览：示例键用真实材质配方绘制（与键盘同一段代码）。 */
@Composable
private fun VisualStylePreview(
    style: VisualStyle,
    selected: Boolean,
    keyColor: Color,
    keyText: Color,
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
        CompositionLocalProvider(
            LocalMaterialPalette provides MaterialPalette(surface = keyColor, accent = accent)
        ) {
            Box(
                modifier = Modifier
                    .size(width = 84.dp, height = 44.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(keyColor)
                    .visualMaterial(style, 8.dp, level = MaterialLevel.BASE)
                    .border(
                        width = if (selected) 2.dp else 1.dp,
                        color = if (selected) accent else MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(8.dp),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text("Aa", color = keyText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = style.title,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 液态玻璃强度示例：用键盘同一段代码画这一档的亮边/厚度/高光。 */
@Composable
private fun LiquidStrengthPreview(
    strength: LiquidGlassStrength,
    selected: Boolean,
    keyColor: Color,
    keyText: Color,
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
            Text("Aa", color = keyText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(6.dp))
        Text(
            text = strength.label,
            style = MaterialTheme.typography.bodySmall,
            color = if (selected) accent else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (selected) FontWeight.Medium else FontWeight.Normal,
        )
    }
}

/** 玻璃效果示例：开时半透明键面 + 玻璃高光/亮边，关时普通键面。 */
@Composable
private fun GlassPreviewSample(enabled: Boolean, keyColor: Color, keyText: Color, accent: Color) {
    CompositionLocalProvider(
        LocalMaterialPalette provides MaterialPalette(surface = keyColor, accent = accent)
    ) {
        Box(
            modifier = Modifier
                .size(width = 84.dp, height = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(if (enabled) keyColor.copy(alpha = 0.62f) else keyColor)
                .then(
                    if (enabled) {
                        Modifier.visualMaterial(VisualStyle.GLASS, 10.dp, level = MaterialLevel.BASE)
                    } else {
                        Modifier
                    }
                )
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(10.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Text("Aa", color = keyText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }
    }
}
