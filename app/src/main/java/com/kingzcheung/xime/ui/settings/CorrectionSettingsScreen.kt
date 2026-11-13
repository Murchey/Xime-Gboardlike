package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.RadioButton
import androidx.compose.ui.Alignment
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.correction.CorrectionPriors
import com.kingzcheung.xime.settings.SettingsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CorrectionSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    var enabled by remember { mutableStateOf(SettingsPreferences.isCorrectorEnabled(context)) }
    var engine by remember { mutableStateOf(SettingsPreferences.getCorrectionEngine(context)) }

    // 页面进入时把持久化值同步到运行时，避免冷启动后引擎回落到默认
    remember { CorrectionPriors.engine = engine; true }

    Scaffold(
        containerColor = settingsPageContainerColor(),
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_zhinengjiucuo)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr(R.string.s_fanhui)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = settingsPageContainerColor(),
                    titleContentColor = MaterialTheme.colorScheme.onSurface
                ),
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 8.dp)
        ) {
            item {
                SettingsSection(title = tr(R.string.s_gongnengkaiguan), content = {
                    SettingsToggleItem(
                        icon = Icons.Default.AutoFixHigh,
                        title = tr(R.string.s_qiyongzhinengjiucuo),
                        subtitle = tr(R.string.s_ancuoxianglinjianshi_zaihouxua_df488),
                        checked = enabled,
                        onCheckedChange = {
                            enabled = it
                            SettingsPreferences.setCorrectorEnabled(context, it)
                            // 插件版：开/关先验推送器（立即生效，无需重启）
                            CorrectionPriors.enabled = it
                        }
                    )
                    Text(
                        text = "滑动输入默认使用高斯热区纠错；此处的开关和引擎选择用于普通点按。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                })
            }

            item {
                SettingsSection(title = "纠错引擎", content = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        CorrectionEngineOption(
                            selected = engine == 0,
                            title = "Xime 原版",
                            subtitle = "单键偏移 + 神经网络信道模型；上游实现，稳定保守",
                            onClick = {
                                engine = 0
                                SettingsPreferences.setCorrectionEngine(context, 0)
                                CorrectionPriors.engine = 0
                            },
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CorrectionEngineOption(
                            selected = engine == 1,
                            title = "Gboard 仿版",
                            subtitle = "高斯热区 + 邻键竞争；更接近 Gboard 的「按键不精确也能纠正」",
                            onClick = {
                                engine = 1
                                SettingsPreferences.setCorrectionEngine(context, 1)
                                CorrectionPriors.engine = 1
                            },
                        )
                        if (engine == 1) {
                            Text(
                                text = "需要键盘布局完成一次测量（进入输入状态后自动收集），首次使用可能尚未生效。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.outline,
                                modifier = Modifier.padding(top = 8.dp),
                            )
                        }
                    }
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_shuoming), content = {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr(R.string.s_morenzhichi_wubi86_wubipinyin_048a6),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = if (engine == 0) tr(R.string.s_jiucuoyinqing_xime_yuanban_shuo)
                                   else tr(R.string.s_jiucuoyinqing_gboard_fangban_shuo),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = tr(R.string.s_jinggao1_cigongnengshiyanxingz_daa71),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = tr(R.string.s_jinggao2_buzhichirenhezidingyi_aaabd),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = tr(R.string.s_jiuzhenghouxuanbiaozhu_jiucuo_23e5d),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                        Text(
                            text = tr(R.string.s_buhuizidongtihuannishurudeneir_d0291),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                })
            }
        }
    }
}

/** 纠错引擎单选行。 */
@Composable
private fun CorrectionEngineOption(
    selected: Boolean,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}