package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.ui.theme.KeyboardColorScheme
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.viewmodel.ThemeSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeSettingsContent(
    onBack: () -> Unit,
    onNavigateToEditor: () -> Unit = {},
    onThemeChanged: () -> Unit = {}
) {
    val viewModel: ThemeSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val colorThemes = KeyboardThemes.themes

    // 直接读实际选择：从调色面板返回后能立即刷新；两套主题互不影响。
    val lightThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = false)
    val darkThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = true)
    val lightTheme = colorThemes.firstOrNull { it.id == lightThemeId } ?: colorThemes.first()
    val darkTheme = colorThemes.firstOrNull { it.id == darkThemeId } ?: colorThemes.first()

    val systemDark = isSystemInDarkTheme()
    val resolvedDark = uiState.darkMode == 1 || (uiState.darkMode == 2 && systemDark)
    // 当前正在编辑的目标色槽：浅色 or 深色
    var editingDark by remember { mutableStateOf(resolvedDark) }
    var previewTheme by remember { mutableStateOf<KeyboardColorScheme?>(null) }
    var previewIsDark by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_zhutiyudingzhi)) },
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
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = tr(R.string.s_xianshimoshi),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
            }

            item {
                val systemTheme = if (resolvedDark) darkTheme else lightTheme
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    KeyboardThemeCard(
                        theme = systemTheme,
                        isSelected = uiState.darkMode == 2,
                        onClick = {
                            viewModel.setDarkMode(2)
                            editingDark = resolvedDark
                            onThemeChanged()
                        },
                        modifier = Modifier.weight(1f),
                        title = tr(R.string.s_gensuixitong)
                    )
                    ThemeCard(
                        title = tr(R.string.s_qianse),
                        isSelected = uiState.darkMode == 0,
                        isDark = false,
                        accentColor = lightTheme.accentLight,
                        keyBgColor = lightTheme.keyBgLight,
                        onClick = {
                            viewModel.setDarkMode(0)
                            editingDark = false
                            onThemeChanged()
                        },
                        modifier = Modifier.weight(1f)
                    )
                    ThemeCard(
                        title = tr(R.string.s_shense),
                        isSelected = uiState.darkMode == 1,
                        isDark = true,
                        accentColor = darkTheme.accentDark,
                        keyBgColor = darkTheme.keyBgDark,
                        onClick = {
                            viewModel.setDarkMode(1)
                            editingDark = true
                            onThemeChanged()
                        },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            item {
                SettingsSection(title = tr(R.string.s_zhutiyanse)) {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 12.dp
                        )
                    ) {
                        Text(
                            text = tr(R.string.s_zhutiyanse_fenbieshezhi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            FilterChip(
                                selected = !editingDark,
                                onClick = { editingDark = false },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        "${tr(R.string.s_qianse)} · ${lightTheme.name}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            )
                            FilterChip(
                                selected = editingDark,
                                onClick = { editingDark = true },
                                modifier = Modifier.weight(1f),
                                label = {
                                    Text(
                                        "${tr(R.string.s_shense)} · ${darkTheme.name}",
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                    )
                                }
                            )
                        }
                    }
                }
            }

            item {
                SettingsSection(title = tr(R.string.s_gexinghua)) {
                    SettingsItem(
                        icon = Icons.TwoTone.Palette,
                        title = tr(R.string.s_diaoseban),
                        subtitle = tr(R.string.s_diaoseban_rukou_fu),
                        onClick = onNavigateToEditor,
                        showArrow = true
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr(R.string.s_peisefangan),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            item {
                Text(
                    text = tr(
                        R.string.s_zhengzaishezhi,
                        tr(if (editingDark) R.string.s_shense else R.string.s_qianse)
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
                Text(
                    text = tr(R.string.s_dianjipeiseyulanwanzhengjianpa_7949f),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            val activeThemeId = if (editingDark) darkThemeId else lightThemeId
            colorThemes.chunked(3).forEach { rowThemes ->
                item {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowThemes.forEach { theme ->
                            KeyboardThemeCard(
                                theme = theme,
                                isSelected = activeThemeId == theme.id,
                                onClick = {
                                    previewTheme = theme
                                    previewIsDark = editingDark
                                },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        repeat(3 - rowThemes.size) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(1.dp))
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr(R.string.s_tishi_peiseqiehuanhoushezhiyem_f78e8),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.6f),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                )
            }
        }

        previewTheme?.let { theme ->
            ThemePreviewSheet(
                theme = theme,
                targetIsDark = previewIsDark,
                onApply = {
                    viewModel.setColorTheme(theme.id, isDark = previewIsDark)
                    previewTheme = null
                    onThemeChanged()
                },
                onDismiss = { previewTheme = null },
            )
        }
    }
}
