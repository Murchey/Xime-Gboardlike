package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.rime.RimeEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import com.kingzcheung.xime.settings.DoublePinyinFinals
import com.kingzcheung.xime.settings.SettingsPreferences

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LayoutDisplaySettingsContent(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    // 声明式设置层：与 SettingsPreferences 共用同一份 SharedPreferences
    val settingsStore = androidx.compose.runtime.remember(context) {
        com.kingzcheung.xime.settings.declarative.SharedPreferencesSettingsStore(
            SettingsPreferences.getPrefsPublic(context)
        )
    }
    // 双拼开关的可见性依赖当前方案
    val layoutSpecsSchema = SettingsPreferences.getCurrentSchema(context)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_bujuyuxianshi)) },
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
                SettingsSection(title = tr(R.string.s_jianpanbuju), content = {
                    DeclarativeSettingItems(
                        specs = androidx.compose.runtime.remember(layoutSpecsSchema) {
                            LayoutDisplaySettingsSpecs.keyboardLayout(layoutSpecsSchema)
                        },
                        store = settingsStore,
                    )

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var keyboardTextScale by remember {
                        mutableStateOf(SettingsPreferences.getKeyboardTextScale(context).toFloat())
                    }
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = tr(R.string.s_jianpanxianshizihao),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            text = tr(R.string.s_jianpanxianshizihao_fu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        KeyboardTextSizeCard(
                            textScalePercent = keyboardTextScale,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "${keyboardTextScale.toInt()}%",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Slider(
                            value = keyboardTextScale,
                            onValueChange = { keyboardTextScale = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setKeyboardTextScale(
                                    context,
                                    keyboardTextScale.toInt()
                                )
                            },
                            valueRange = 80f..130f,
                            steps = 9
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var doubleSpacePunctuationEnabled by remember {
                        mutableStateOf(SettingsPreferences.isDoubleSpacePunctuationEnabled(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(R.string.s_lianianliangcikonggecharubiaodian),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = tr(R.string.s_lianianliangcikonggecharubiaodian_fu),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = doubleSpacePunctuationEnabled,
                            onCheckedChange = { enabled ->
                                doubleSpacePunctuationEnabled = enabled
                                SettingsPreferences.setDoubleSpacePunctuationEnabled(context, enabled)
                            }
                        )
                    }

                    if (doubleSpacePunctuationEnabled) {
                        HorizontalDivider(
                            modifier = Modifier.padding(start = 16.dp),
                            thickness = 0.5.dp,
                            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                        )
                        var punctuation by remember {
                            mutableStateOf(SettingsPreferences.getDoubleSpacePunctuation(context))
                        }
                        var showPunctuationPicker by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { showPunctuationPicker = true }
                                .padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = tr(R.string.s_charubiaodian),
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = tr(R.string.s_dangqianbiaodian, punctuation),
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = punctuation,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        if (showPunctuationPicker) {
                            SettingsSingleChoiceDialog(
                                title = tr(R.string.s_xuanzebiaodian),
                                options = listOf("。", "，", "、", "？", "！", "；", "：", ".", ",", "?", "!")
                                    .map { SettingsChoiceOption(id = it, title = it) },
                                selectedId = punctuation,
                                onSelect = { selected ->
                                    punctuation = selected
                                    SettingsPreferences.setDoubleSpacePunctuation(context, selected)
                                    showPunctuationPicker = false
                                },
                                onDismiss = { showPunctuationPicker = false }
                            )
                        }
                    }
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_houxuanci), content = {
                    val candidateTextSizePref = SettingsPreferences.getCandidateTextSize(context)
                    var candidateTextSize by remember(candidateTextSizePref) {
                        mutableStateOf(candidateTextSizePref.toFloat())
                    }

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Text(
                            text = tr(R.string.s_houxuanzidaxiao),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        CandidateTextSizeCard(
                            candidateTextSize = candidateTextSize,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Slider(
                            value = candidateTextSize,
                            onValueChange = { candidateTextSize = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setCandidateTextSize(context, candidateTextSize.toInt())
                            },
                            valueRange = 12f..22f,
                            steps = 9
                        )
                    }

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var showComments by remember {
                        mutableStateOf(SettingsPreferences.showCandidateComments(context))
                    }

                    Text(
                        text = tr(R.string.s_bianmazhushi),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    Text(
                        text = tr(R.string.s_zaihouxuancipangxianshiduiying_de529),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(100.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CommentDisplayCard(
                            title = tr(R.string.s_xianshi),
                            isSelected = showComments,
                            showComment = true,
                            onClick = {
                                showComments = true
                                SettingsPreferences.setShowCandidateComments(context, true)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CommentDisplayCard(
                            title = tr(R.string.s_yincang),
                            isSelected = !showComments,
                            showComment = false,
                            onClick = {
                                showComments = false
                                SettingsPreferences.setShowCandidateComments(context, false)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    DeclarativeSettingItems(
                        specs = LayoutDisplaySettingsSpecs.candidateComments,
                        store = settingsStore,
                    )
                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    var inputTextLocation by remember {
                        mutableStateOf(SettingsPreferences.getInputTextLocation(context))
                    }

                    Text(
                        text = tr(R.string.s_bianmaxianshi),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp)
                    )
                    Text(
                        text = tr(R.string.s_xuanzeshurubianmadexianshiweizhi),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 16.dp, bottom = 12.dp)
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(150.dp)
                            .padding(horizontal = 16.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        CodeDisplayCard(
                            title = tr(R.string.s_xianshizaishurukuang),
                            isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_INPUT_BOX,
                            showCodeInInputBox = true,
                            onClick = {
                                inputTextLocation = SettingsPreferences.INPUT_TEXT_INPUT_BOX
                                SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_INPUT_BOX)
                            },
                            modifier = Modifier.weight(1f)
                        )
                        CodeDisplayCard(
                            title = tr(R.string.s_xianshizaihouxuanlan),
                            isSelected = inputTextLocation == SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR,
                            showCodeInInputBox = false,
                            onClick = {
                                inputTextLocation = SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR
                                SettingsPreferences.setInputTextLocation(context, SettingsPreferences.INPUT_TEXT_CANDIDATE_BAR)
                            },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )

                    val pageSizePref = SettingsPreferences.getPageSize(context)
                    val effectiveValue = pageSizePref.toFloat()
                    var pageSizeSlider by remember(effectiveValue) {
                        mutableStateOf(effectiveValue)
                    }
                    // 立即生效：引擎已初始化时直接改方案配置并重建 Schema（与会话启动同一条
                    // 路径 applyPageSizeOverride），不再要求用户去方案页重新部署。
                    val pageSizeScope = rememberCoroutineScope()

                    Column(
                        modifier = Modifier.padding(16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = tr(R.string.s_meiyehouxuancishu),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = tr(R.string.s_ge, pageSizeSlider.toInt()),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        Slider(
                            value = pageSizeSlider,
                            onValueChange = { pageSizeSlider = it },
                            onValueChangeFinished = {
                                val intValue = pageSizeSlider.toInt()
                                SettingsPreferences.setPageSize(context, intValue)
                                pageSizeScope.launch(Dispatchers.IO) {
                                    if (RimeEngine.isInitialized()) {
                                        RimeEngine.getInstance().setPageSize(
                                            SettingsPreferences.getCurrentSchema(context), intValue
                                        )
                                    }
                                }
                            },
                            valueRange = 5f..SettingsPreferences.MAX_PAGE_SIZE.toFloat(),
                            steps = SettingsPreferences.MAX_PAGE_SIZE - 6
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(R.string.s_xiugaihouxudaofanganshezhizhon_603c8),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                    }
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_zhanakaiye_buju), content = {
                    var expandedStyle by remember {
                        mutableStateOf(SettingsPreferences.expandedCandidateStyle(context))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 分组标题已说明「展开页布局」，行内只放说明文字，避免重复
                        Text(
                            text = tr(R.string.s_zhanakaiye_buju_fu),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        FilterChip(
                            selected = expandedStyle == SettingsPreferences.EXPANDED_STYLE_GRID,
                            onClick = {
                                expandedStyle = SettingsPreferences.EXPANDED_STYLE_GRID
                                SettingsPreferences.setExpandedCandidateStyle(context, expandedStyle)
                            },
                            label = { Text(tr(R.string.s_wangge)) }
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        FilterChip(
                            selected = expandedStyle == SettingsPreferences.EXPANDED_STYLE_FLOW,
                            onClick = {
                                expandedStyle = SettingsPreferences.EXPANDED_STYLE_FLOW
                                SettingsPreferences.setExpandedCandidateStyle(context, expandedStyle)
                            },
                            label = { Text(tr(R.string.s_liushi)) }
                        )
                    }
                })
            }

            item {
                SettingsSection(title = tr(R.string.s_shurukuangshipei), content = {
                    DeclarativeSettingItems(
                        specs = LayoutDisplaySettingsSpecs.inputField,
                        store = settingsStore,
                    )
                })
            }
        }
    }
}
