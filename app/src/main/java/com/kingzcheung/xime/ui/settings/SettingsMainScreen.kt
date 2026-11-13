package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.i18n.tr

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.twotone.AutoAwesome
import androidx.compose.material.icons.twotone.AutoFixHigh
import androidx.compose.material.icons.twotone.Backup
import androidx.compose.material.icons.twotone.Ballot
import androidx.compose.material.icons.twotone.ContentPaste
import androidx.compose.material.icons.twotone.Description
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material.icons.twotone.Palette
import androidx.compose.material.icons.twotone.Sort
import androidx.compose.material.icons.twotone.Storefront
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material.icons.twotone.TableChart
import androidx.compose.material.icons.twotone.ToggleOn
import androidx.compose.material.icons.twotone.Tune
import androidx.compose.material.icons.twotone.TypeSpecimen
import androidx.compose.material.icons.twotone.Vibration
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.MarketUpdateChecker
import com.kingzcheung.xime.settings.SettingsPreferences

/**
 * 设置主页面：Google 设置那种「大标题 + 分组白卡」结构。
 * 每个分组是一张圆角卡片，行尾统一 slot，分隔线由 SettingsCard 自动绘制。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsMainContent(
    onNavigateToSchema: () -> Unit,
    onNavigateToMarket: () -> Unit = {},
    onNavigateToTheme: () -> Unit,
    onNavigateToFont: () -> Unit = {},
    onNavigateToKeyEffect: () -> Unit,
    onNavigateToLayoutDisplay: () -> Unit,
    onNavigateToDictionary: () -> Unit,
    onNavigateToPlugins: () -> Unit,
    onNavigateToModelLocal: () -> Unit = {},
    onNavigateToSmartPrediction: () -> Unit,
    onNavigateToCorrection: () -> Unit = {},
    onNavigateToFuzzyPinyin: () -> Unit = {},
    onNavigateToGrammarModel: () -> Unit = {},
    onNavigateToSpeechToText: () -> Unit,
    onNavigateToAbout: () -> Unit,
    onNavigateToClipboard: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        MarketUpdateChecker.maybeRefresh(context)
    }
    val marketUpdates by MarketUpdateChecker.summary.collectAsStateWithLifecycle()

    SettingsPageScaffold(title = stringResource(R.string.settings_title)) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .imePadding(),
            contentPadding = innerPadding,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            item {
                SettingsGroup(title = tr(R.string.s_shurufa)) {
                    SettingsNavRow(
                        title = tr(R.string.s_qiyongshurufa),
                        summary = tr(R.string.s_zaixitongshezhizhongqiyongxima_44550),
                        icon = Icons.TwoTone.Keyboard,
                        onClick = {
                            context.startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS))
                        },
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_xuanzeshurufa),
                        summary = tr(R.string.s_jiangximasheweidangqianshurufa),
                        icon = Icons.TwoTone.ToggleOn,
                        onClick = {
                            val imm = context.getSystemService(android.content.Context.INPUT_METHOD_SERVICE)
                                as InputMethodManager
                            imm.showInputMethodPicker()
                        },
                    )
                }
            }

            // 输入测试单独成卡：不属于设置项，避免和"启用/选择输入法"混在一张卡里
            item { InputTestCard() }

            item {
                SettingsGroup(title = stringResource(R.string.settings_section_appearance)) {
                    SettingsNavRow(
                        title = tr(R.string.s_zhutiyudingzhi),
                        summary = tr(R.string.s_zidingyiwaiguanheyangshi),
                        icon = Icons.TwoTone.Palette,
                        onClick = onNavigateToTheme,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_zitiguanli),
                        summary = tr(R.string.s_ziti_guanli_fu),
                        icon = Icons.TwoTone.TypeSpecimen,
                        onClick = onNavigateToFont,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_anjianxiaoguo),
                        summary = tr(R.string.s_anjianyinxiaohezhendongfankui),
                        icon = Icons.TwoTone.Vibration,
                        onClick = onNavigateToKeyEffect,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_bujuyuxianshi),
                        summary = tr(R.string.s_houxuancixianshi_jianpanbujudeng),
                        icon = Icons.TwoTone.TableChart,
                        onClick = onNavigateToLayoutDisplay,
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_fanganyuciku)) {
                    SettingsNavRow(
                        title = tr(R.string.s_shurufangan),
                        summary = tr(R.string.s_guanlishurufangan),
                        icon = Icons.TwoTone.KeyboardAlt,
                        onClick = onNavigateToSchema,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_mohuyin),
                        summary = tr(R.string.s_mohuyin_fu),
                        icon = Icons.TwoTone.Tune,
                        onClick = onNavigateToFuzzyPinyin,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_yufamoxing),
                        summary = tr(R.string.s_yufamoxing_fu),
                        icon = Icons.TwoTone.Sort,
                        onClick = onNavigateToGrammarModel,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_cikuguanli),
                        summary = tr(R.string.s_yonghuciku_kuaijieduanyuyuciti_be6d2),
                        icon = Icons.TwoTone.Ballot,
                        onClick = onNavigateToDictionary,
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_zhineng)) {
                    SettingsNavRow(
                        title = tr(R.string.s_zhinenglianxiang),
                        summary = tr(R.string.s_jiyu_ai_moxingdezhinenglianxia_5cce9),
                        icon = Icons.TwoTone.AutoAwesome,
                        onClick = onNavigateToSmartPrediction,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_zhinengjiucuo_shiyanxing),
                        summary = tr(R.string.s_ancuoxianglinjianshigeichujiuz_834ec),
                        icon = Icons.TwoTone.AutoFixHigh,
                        onClick = onNavigateToCorrection,
                    )
                    var sttEnabled by remember { mutableStateOf(SettingsPreferences.isSttEnabled(context)) }
                    SettingsSwitchRow(
                        title = tr(R.string.s_yuyinzhuanwenben),
                        summary = tr(R.string.s_zaixian_asr_fuwu_xuanzhuangyuy_74a1a),
                        icon = Icons.TwoTone.GraphicEq,
                        checked = sttEnabled,
                        onClick = { if (sttEnabled) onNavigateToSpeechToText() },
                        onCheckedChange = { enabled ->
                            sttEnabled = enabled
                            SettingsPreferences.setSttEnabled(context, enabled)
                        },
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_kuozhan)) {
                    SettingsNavRow(
                        title = tr(R.string.s_kuozhanshangdian),
                        summary = tr(R.string.s_xiazaishurufangan_moxing_chajian),
                        icon = Icons.TwoTone.Storefront,
                        onClick = onNavigateToMarket,
                        badge = marketUpdates.totalUpdates.takeIf { it > 0 }?.let { count ->
                            { SettingsBadge(text = tr(R.string.s_xiangkegengxin, count)) }
                        },
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_chajianguanli),
                        summary = tr(R.string.s_guanliyianzhuangdechajian),
                        icon = Icons.TwoTone.Extension,
                        onClick = onNavigateToPlugins,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_moxingguanli),
                        summary = tr(R.string.s_guanlibendixiazaidezhinengmoxing),
                        icon = Icons.TwoTone.Straighten,
                        onClick = onNavigateToModelLocal,
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_shujuyutongbu)) {
                    SettingsNavRow(
                        title = tr(R.string.s_jiantieban),
                        summary = tr(R.string.s_tupianjilu_tijixianzhi_zhanyon_fa4c6),
                        icon = Icons.TwoTone.ContentPaste,
                        onClick = onNavigateToClipboard,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_tongbuyubeifen),
                        summary = tr(R.string.s_duoshebeicidianhutongyupeizhib_1efa8),
                        icon = Icons.TwoTone.Backup,
                        onClick = onNavigateToBackup,
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_guanyu)) {
                    SettingsNavRow(
                        title = tr(R.string.s_shiyongwendang),
                        summary = "ime.ximei.me",
                        icon = Icons.TwoTone.Description,
                        onClick = {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse("https://ime.ximei.me"))
                            )
                        },
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_guanyuxima),
                        summary = tr(R.string.s_banbenxinxi_kaifazhe_lianxifangshi),
                        icon = Icons.TwoTone.Info,
                        onClick = onNavigateToAbout,
                    )
                }
            }

            // 底部呼吸空间：与系统设置一致的 80dp 留白
            item { Column(modifier = Modifier.padding(bottom = 80.dp)) {} }
        }
    }
}

/** 输入测试区：M3 多行输入框 + 清空按钮。 */
@Composable
private fun InputTestCard() {
    var testText by remember { mutableStateOf("") }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = SettingsDimens.RowHorizontal, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        OutlinedTextField(
            value = testText,
            onValueChange = { testText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            placeholder = {
                Text(
                    text = tr(R.string.s_dianjicichukaishishuruceshi),
                    style = MaterialTheme.typography.bodyMedium,
                )
            },
        )
        if (testText.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End,
            ) {
                TextButton(onClick = { testText = "" }) {
                    Text(tr(R.string.s_qingchu), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
