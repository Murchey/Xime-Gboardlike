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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.twotone.Straighten
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.ui.draw.rotate
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
import com.kingzcheung.xime.settings.declarative.SettingValue

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

    // 滑动输入相关选项：点条目在下方自然展开（不是侧边抽屉）
    var glideExpanded by remember { mutableStateOf(false) }
    SettingsPageScaffold(
        title = tr(R.string.s_bujuyuxianshi),
        onBack = onBack,
    ) { paddingValues ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = paddingValues,
            verticalArrangement = Arrangement.spacedBy(SettingsDimens.GroupGap)
        ) {
            item {
                SettingsGroup(title = tr(R.string.s_jianpanbuju), content = {
                    // 滑动输入：点条目在下方自然展开相关开关（不是侧边抽屉）
                    val chevronRotation by animateFloatAsState(
                        targetValue = if (glideExpanded) 90f else 0f,
                        label = "glideChevron",
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { glideExpanded = !glideExpanded }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = tr(R.string.s_huadongshuru),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = tr(R.string.s_huadongshuru_fu),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.rotate(chevronRotation)
                        )
                    }
                    AnimatedVisibility(visible = glideExpanded) {
                        Column {
                            DeclarativeSettingItems(
                                specs = LayoutDisplaySettingsSpecs.glide(),
                                store = settingsStore,
                                // 打开滑动输入时默认连带打开神经解码；之后仍可单独关闭神经解码
                                linkedWrites = { spec, value ->
                                    if (spec.key == SettingsPreferences.KEY_GLIDE_TYPING &&
                                        value == SettingValue.Flag(true)
                                    ) {
                                        listOf(
                                            SettingsPreferences.KEY_GLIDE_NEURAL to SettingValue.Flag(true)
                                        )
                                    } else {
                                        emptyList()
                                    }
                                },
                            )
                        }
                    }
                    DeclarativeSettingItems(
                        specs = androidx.compose.runtime.remember(layoutSpecsSchema) {
                            LayoutDisplaySettingsSpecs.keyboardLayout(layoutSpecsSchema)
                        },
                        store = settingsStore,
                        // 打开滑动输入时默认连带打开神经解码；之后仍可单独关闭神经解码
                        linkedWrites = { spec, value ->
                            if (spec.key == SettingsPreferences.KEY_GLIDE_TYPING &&
                                value == SettingValue.Flag(true)
                            ) {
                                listOf(
                                    SettingsPreferences.KEY_GLIDE_NEURAL to SettingValue.Flag(true)
                                )
                            } else {
                                emptyList()
                            }
                        },
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
                SettingsGroup(title = tr(R.string.s_houxuanci), content = {
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


                    // 立即生效：拖动结束改方案配置并重建 Schema（与会话启动同一条路径）
                    val pageSizeScope = rememberCoroutineScope()
                    DeclarativeSettingItems(
                        specs = LayoutDisplaySettingsSpecs.pageSize,
                        store = settingsStore,
                        onChangeFinished = { _, value ->
                            val size = (value as SettingValue.Number).value
                            pageSizeScope.launch(Dispatchers.IO) {
                                if (RimeEngine.isInitialized()) {
                                    RimeEngine.getInstance().setPageSize(
                                        SettingsPreferences.getCurrentSchema(context), size
                                    )
                                }
                            }
                        }
                    )
                    Text(
                        text = tr(R.string.s_xiugaihouxudaofanganshezhizhon_603c8),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                    )
                })
            }
            item {
                SettingsGroup(title = tr(R.string.s_zhanakaiye_buju), content = {
                    DeclarativeSettingItems(
                        specs = LayoutDisplaySettingsSpecs.expandedLayout,
                        store = settingsStore,
                    )
                })
            }
            item {
                SettingsGroup(title = tr(R.string.s_shurukuangshipei), content = {
                    DeclarativeSettingItems(
                        specs = LayoutDisplaySettingsSpecs.inputField,
                        store = settingsStore,
                    )
                })
            }
        }
    }
}
