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
import androidx.compose.material.icons.twotone.Devices
import androidx.compose.material.icons.twotone.Extension
import androidx.compose.material.icons.twotone.GraphicEq
import androidx.compose.material.icons.twotone.Info
import androidx.compose.material.icons.twotone.Keyboard
import androidx.compose.material.icons.twotone.KeyboardAlt
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
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
    onNavigateToKeyboardLayout: () -> Unit = {},
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
    onNavigateToDeviceLink: () -> Unit = {},
    onNavigateToBackup: () -> Unit = {}
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        MarketUpdateChecker.maybeRefresh(context)
    }
    val marketUpdates by MarketUpdateChecker.summary.collectAsStateWithLifecycle()

    // 一级页只放"入口 + 当前状态"，副标题直接告诉用户现在是什么（对齐 Gboard 的做法）
    val currentSchemaSummary = remember {
        val id = SettingsPreferences.getCurrentSchema(context)
        com.kingzcheung.xime.settings.SchemaManager.getSchemaDisplayName(context, id) ?: id
    }
    val keyboardLayoutSummary = remember {
        val preset = SettingsPreferences.getKeyboardLayoutPreset(context)
        val order = if (SettingsPreferences.getKeyboardLetterOrder(context) == "abcde") "ABCDE" else "QWERTY"
        preset + " 键 · " + order
    }

    var showStats by remember { mutableStateOf(false) }
    if (showStats) {
        TypingStatsScreen(onBack = { showStats = false })
        return
    }
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
                SettingsGroup(title = tr(R.string.s_yuyanyujianpan)) {
                    SettingsNavRow(
                        title = tr(R.string.s_shurufangan),
                        summary = currentSchemaSummary,
                        icon = Icons.TwoTone.KeyboardAlt,
                        onClick = onNavigateToSchema,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_jianpanbujushezhi),
                        summary = keyboardLayoutSummary,
                        icon = Icons.TwoTone.Keyboard,
                        onClick = onNavigateToKeyboardLayout,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_mohuyin),
                        icon = Icons.TwoTone.Tune,
                        onClick = onNavigateToFuzzyPinyin,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_cikuguanli),
                        icon = Icons.TwoTone.Ballot,
                        onClick = onNavigateToDictionary,
                    )
                }
            }

            item {
                SettingsGroup(title = tr(R.string.s_shuruyulianxiang)) {
                    SettingsNavRow(
                        title = tr(R.string.s_zhinenglianxiang),
                        summary = tr(R.string.s_jiyu_ai_moxingdezhinenglianxia_5cce9),
                        icon = Icons.TwoTone.AutoAwesome,
                        onClick = onNavigateToSmartPrediction,
                    )
                    SettingsNavRow(
                        title = tr(R.string.s_yufamoxing),
                        summary = tr(R.string.s_yufamoxing_fu),
                        icon = Icons.TwoTone.Sort,
                        onClick = onNavigateToGrammarModel,
                    )
                    SettingsNavRow(
                        title = "输入统计",
                        summary = "打字字数、速度与输入方式（本机）",
                        icon = Icons.TwoTone.Tune,
                        onClick = { showStats = true },
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
                        }
                    )
                }
            }

            item {
                // 分组含"本地数据/数据流向"设置：剪贴板（内容与同步）、设备互联（配对）、
                // 词典与配置的同步备份。设备互联虽然也服务于剪贴板同步，但配对是独立任务，
                // 需要从主设置页一键直达；埋在 剪贴板 → 剪贴板同步 第三层时几乎找不到入口。
                SettingsSection(title = tr(R.string.s_shujuyutongbu), content = {
                    SettingsItem(
                        icon = Icons.TwoTone.ContentPaste,
                        title = tr(R.string.s_jiantieban),
                        subtitle = tr(R.string.s_tupianjilu_tijixianzhi_zhanyon_fa4c6),
                        onClick = onNavigateToClipboard,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Devices,
                        title = tr(R.string.s_shebeihulian),
                        subtitle = tr(R.string.s_shebeihulian_shuoming),
                        onClick = onNavigateToDeviceLink,
                        showArrow = true
                    )
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    SettingsItem(
                        icon = Icons.TwoTone.Backup,
                        title = tr(R.string.s_tongbuyubeifen),
                        subtitle = tr(R.string.s_duoshebeicidianhutongyupeizhib_1efa8),
                        onClick = onNavigateToBackup,
                        showArrow = true
                    )
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_guanyu), content = {
                    SettingsItem(
                        icon = Icons.TwoTone.Description,
                        title = tr(R.string.s_shiyongwendang),
                        subtitle = "ime.ximei.me",
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://ime.ximei.me"))
                            context.startActivity(intent)
                        },
                    )
                })
            }

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
                        title = tr(R.string.s_jianpanpianhao),
                        summary = tr(R.string.s_houxuancixianshi_jianpanbujudeng),
                        icon = Icons.TwoTone.TableChart,
                        onClick = onNavigateToLayoutDisplay,
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
            .padding(vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        // 与设置卡片同一套观感：surfaceContainer 底色 + 24dp 大圆角，不要描边
        TextField(
            value = testText,
            onValueChange = { testText = it },
            modifier = Modifier.fillMaxWidth(),
            minLines = 3,
            maxLines = 5,
            shape = RoundedCornerShape(SettingsDimens.CardCorner),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = settingsCardContainerColor(),
                unfocusedContainerColor = settingsCardContainerColor(),
                disabledContainerColor = settingsCardContainerColor(),
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                disabledIndicatorColor = Color.Transparent,
            ),
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