package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr

/**
 * 上游插件式同步/备份的二级页：把 WebDAV 等旧入口从主设置收拢到这里，
 * 主页面只保留内置对象存储卡（用户要求「保持软件功能设置页面显示简明」）。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LegacySyncSettingsContent(
    onBack: () -> Unit,
    onNavigateToMarket: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToPluginSettings: (String) -> Unit,
) {
    var tab by remember { mutableIntStateOf(0) }
    // 不套外层 Scaffold：两个子 Content 各自带 TopAppBar，双层顶栏会挤占空间。
    // 这里只加一行 Tab 做切换，视觉上仍是一次「进入二级页」。
    Column(modifier = Modifier.fillMaxSize()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text(tr(R.string.s_jiantiebantongbu)) })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text(tr(R.string.s_tongbuyubeifen)) })
        }
        when (tab) {
            0 -> ClipboardSyncSettingsContent(
                onBack = onBack,
                onNavigateToMarket = onNavigateToMarket,
            )
            else -> BackupSettingsContent(
                onBack = onBack,
                onNavigateToPlugins = onNavigateToPlugins,
                onNavigateToMarket = onNavigateToMarket,
                onNavigateToPluginSettings = onNavigateToPluginSettings,
                legacyOnly = true,
            )
        }
    }
}
