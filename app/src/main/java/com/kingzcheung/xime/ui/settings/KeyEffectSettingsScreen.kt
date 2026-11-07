package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.declarative.SettingValue
import com.kingzcheung.xime.viewmodel.KeyEffectSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KeyEffectSettingsContent(
    onBack: () -> Unit
) {
    val viewModel: KeyEffectSettingsViewModel = viewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // 声明式设置层：与 SettingsPreferences 共用同一份 SharedPreferences
    val context = androidx.compose.ui.platform.LocalContext.current
    val settingsStore = androidx.compose.runtime.remember(context) {
        com.kingzcheung.xime.settings.declarative.SharedPreferencesSettingsStore(
            SettingsPreferences.getPrefsPublic(context)
        )
    }

    // 振幅控制是设备能力，参与滑杆可见性；规格随能力变化重建
    val hapticSpecs = androidx.compose.runtime.remember(uiState.hasAmplitudeControl) {
        KeyEffectSettingsSpecs.haptic(uiState.hasAmplitudeControl)
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_anjianxiaoguo)) },
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
                SettingsSection(title = tr(R.string.s_changganqipao), content = {
                    DeclarativeSettingItems(
                        specs = KeyEffectSettingsSpecs.longPressBubble,
                        store = settingsStore,
                        onChange = { spec, value ->
                            if (spec.key == SettingsPreferences.KEY_LONG_PRESS_BUBBLE_DELAY_MS) {
                                viewModel.setLongPressBubbleDelayMs((value as SettingValue.Number).value)
                            }
                        }
                    )
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_anjianyinxiao), content = {
                    DeclarativeSettingItems(
                        specs = KeyEffectSettingsSpecs.sound,
                        store = settingsStore,
                    )
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_zhendongfankui), content = {
                    DeclarativeSettingItems(
                        specs = hapticSpecs,
                        store = settingsStore,
                        onChange = { spec, value ->
                            when (spec.key) {
                                SettingsPreferences.KEY_HAPTIC_MODE ->
                                    viewModel.setHapticMode((value as SettingValue.Text).value)
                                SettingsPreferences.KEY_HAPTIC_ON_KEYUP ->
                                    viewModel.setHapticOnKeyUp((value as SettingValue.Flag).value)
                                SettingsPreferences.KEY_VIBRATION_PRESS_DURATION ->
                                    viewModel.setPressDuration((value as SettingValue.Number).value)
                                SettingsPreferences.KEY_VIBRATION_LONG_PRESS_DURATION ->
                                    viewModel.setLongPressDuration((value as SettingValue.Number).value)
                                SettingsPreferences.KEY_VIBRATION_PRESS_AMPLITUDE ->
                                    viewModel.setPressAmplitude((value as SettingValue.Number).value)
                                SettingsPreferences.KEY_VIBRATION_LONG_PRESS_AMPLITUDE ->
                                    viewModel.setLongPressAmplitude((value as SettingValue.Number).value)
                            }
                        }
                    )
                    if (uiState.hapticMode == KeyEffectSettingsSpecs.HAPTIC_ENABLED) {
                        Text(
                            text = tr(R.string.s_dangzidingyishizhanghezhenfuju_2bff0),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                })
            }
        }
    }
}
