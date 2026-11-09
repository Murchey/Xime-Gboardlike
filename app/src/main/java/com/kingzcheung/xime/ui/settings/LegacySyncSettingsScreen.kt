package com.kingzcheung.xime.ui.settings

import androidx.compose.runtime.Composable

/** 旧路由保留为插件同步入口。 */
@Composable
fun LegacySyncSettingsContent(
    onBack: () -> Unit,
    onNavigateToMarket: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToPluginSettings: (String) -> Unit,
) = ClipboardSyncSettingsContent(onBack, onNavigateToMarket)
