package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.speech.AsrConnectionTester
import com.kingzcheung.xime.speech.AsrKeyStore
import com.kingzcheung.xime.speech.AsrPreset
import com.kingzcheung.xime.speech.AsrPresetCatalog
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 内置在线识别配置区（无需插件）。
 *
 * 交互刻意做成"选服务商 → 填 Key → 可选高级"三段，Key 只在失焦/离开时落盘，
 * 避免历史实现里"每敲一个字符写一次 SharedPreferences"。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BuiltinOnlineConfigSection(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var providerId by remember {
        mutableStateOf(SettingsPreferences.getAsrProvider(context).ifBlank { AsrPresetCatalog.default.id })
    }
    val preset: AsrPreset = AsrPresetCatalog.findOrDefault(providerId)
    val keyStore = remember { AsrKeyStore.of(context) }

    // Key 只在内存里编辑，落盘时机见下方 onFocusChanged / onDispose
    var apiKey by remember(providerId) { mutableStateOf(keyStore.get(providerId)) }
    var keyVisible by remember { mutableStateOf(false) }
    var model by remember(providerId) {
        mutableStateOf(
            SettingsPreferences.getAsrModel(context, providerId).ifBlank { preset.defaultModel }
        )
    }
    var baseUrl by remember(providerId) {
        mutableStateOf(SettingsPreferences.getAsrBaseUrl(context, providerId))
    }
    var language by remember(providerId) {
        mutableStateOf(SettingsPreferences.getAsrLanguage(context, providerId))
    }
    var customModel by remember(providerId) {
        mutableStateOf(SettingsPreferences.getAsrModel(context, providerId))
    }
    var showAdvanced by remember(providerId) { mutableStateOf(false) }
    var showPicker by remember { mutableStateOf(false) }

    var probing by remember { mutableStateOf(false) }
    var probeText by remember(providerId) { mutableStateOf<String?>(null) }
    var probeOk by remember(providerId) { mutableStateOf(false) }

    fun persistKey() {
        if (apiKey.trim() != keyStore.get(providerId).trim()) {
            keyStore.set(providerId, apiKey)
        }
    }

    // 离开页面时兜底落盘，避免用户填完直接返回丢 Key
    DisposableEffect(providerId) {
        onDispose { persistKey() }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = tr(R.string.s_yinqing_neizhianzaixian),
                fontWeight = FontWeight.Medium,
                fontSize = 15.sp,
            )
            Text(
                text = tr(R.string.s_zaixian_asr_fuwu_xuanzhuangyuy_74a1a),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(10.dp))

            // 服务商行：点开单选弹窗
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPicker = true }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(preset.displayName, fontSize = 14.sp)
                    Text(
                        text = protocolLabel(preset),
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (keyStore.has(providerId) || !preset.requiresKey) {
                        tr(R.string.s_yinqing_yijiu)
                    } else {
                        tr(R.string.s_yinqing_weitianmiyao)
                    },
                    fontSize = 12.sp,
                    color = if (keyStore.has(providerId) || !preset.requiresKey) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.outline
                    },
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (preset.requiresKey) {
                OutlinedTextField(
                    value = apiKey,
                    onValueChange = { apiKey = it },
                    label = { Text("API Key") },
                    supportingText = if (preset.keyHelp.isBlank()) null else {
                        { Text(preset.keyHelp, fontSize = 11.sp) }
                    },
                    singleLine = true,
                    visualTransformation = if (keyVisible) VisualTransformation.None
                    else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { keyVisible = !keyVisible }) {
                            Icon(
                                imageVector = if (keyVisible) Icons.Default.VisibilityOff
                                else Icons.Default.Visibility,
                                contentDescription = null,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { focus ->
                            if (!focus.isFocused) persistKey()
                        },
                )
                Spacer(Modifier.height(6.dp))
            }

            // 模型：优先给候选 chips；「自定义」直接输入
            if (preset.modelOptions.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    preset.modelOptions.forEach { option ->
                        FilterChip(
                            selected = model == option,
                            onClick = {
                                model = option
                                customModel = option
                                SettingsPreferences.setAsrModel(context, providerId, option)
                            },
                            label = { Text(option, fontSize = 11.sp) },
                        )
                    }
                }
            }
            if (preset.modelOptions.isEmpty() || !preset.modelOptions.contains(model)) {
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = if (preset.modelOptions.isEmpty()) customModel else model,
                    onValueChange = { value ->
                        if (preset.modelOptions.isEmpty()) {
                            customModel = value
                            SettingsPreferences.setAsrModel(context, providerId, value)
                        } else {
                            model = value
                            SettingsPreferences.setAsrModel(context, providerId, value)
                        }
                    },
                    label = { Text(tr(R.string.s_yuyin_zidingyimoxing)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedButton(
                    onClick = {
                        persistKey()
                        probing = true
                        probeText = null
                        val keySnapshot = apiKey
                        val baseSnapshot = baseUrl
                        scope.launch {
                            val result = withContext(Dispatchers.IO) {
                                AsrConnectionTester.probe(preset, keySnapshot, baseSnapshot)
                            }
                            probeOk = result is AsrConnectionTester.ProbeResult.Ok
                            probeText = describeProbe(result)
                            probing = false
                        }
                    },
                    enabled = !probing && (apiKey.isNotBlank() || !preset.requiresKey),
                ) {
                    if (probing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(14.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(tr(R.string.s_yuyin_lianjiezhong), fontSize = 13.sp)
                    } else {
                        Text(tr(R.string.s_yuyin_ceshilianjie), fontSize = 13.sp)
                    }
                }
                Spacer(Modifier.width(12.dp))
                TextButton(onClick = { showAdvanced = !showAdvanced }) {
                    Text(tr(R.string.s_yuyin_gaojishezhi), fontSize = 13.sp)
                }
            }
            probeText?.let { text ->
                Text(
                    text = text,
                    fontSize = 12.sp,
                    color = if (probeOk) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }

            if (showAdvanced) {
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = baseUrl,
                    onValueChange = {
                        baseUrl = it
                        SettingsPreferences.setAsrBaseUrl(context, providerId, it)
                    },
                    label = { Text("Base URL") },
                    placeholder = { Text(preset.baseUrl.ifBlank { "https://" }) },
                    supportingText = {
                        Text(
                            text = "留空使用默认：${preset.baseUrl.ifBlank { "—" }}",
                            fontSize = 11.sp,
                        )
                    },
                    singleLine = true,
                    enabled = preset.baseUrlEditable,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(6.dp))
                OutlinedTextField(
                    value = language,
                    onValueChange = {
                        language = it
                        SettingsPreferences.setAsrLanguage(context, providerId, it)
                    },
                    label = { Text(tr(R.string.s_yuyin_yuyantishi)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
    }

    if (showPicker) {
        SettingsSingleChoiceDialog(
            title = tr(R.string.s_yuyin_fuwushang),
            options = AsrPresetCatalog.all.map { p ->
                SettingsChoiceOption(
                    id = p.id,
                    title = p.displayName,
                    subtitle = protocolLabel(p) +
                        if (keyStore.has(p.id) || !p.requiresKey) {
                            tr(R.string.s_nyipeizhi)
                        } else {
                            tr(R.string.s_nweipeizhi)
                        },
                )
            },
            selectedId = providerId,
            onSelect = { pickedId ->
                persistKey()
                showPicker = false
                providerId = pickedId
                SettingsPreferences.setAsrProvider(context, pickedId)
            },
            onDismiss = { showPicker = false },
        )
    }
}

private fun protocolLabel(preset: AsrPreset): String = when (preset.protocol) {
    com.kingzcheung.xime.speech.AsrProtocol.DASHSCOPE_WS -> "实时流式"
    com.kingzcheung.xime.speech.AsrProtocol.OPENAI_TRANSCRIPTIONS -> "整段转写"
    com.kingzcheung.xime.speech.AsrProtocol.OPENAI_CHAT_AUDIO -> "整段转写（对话接口）"
}

private fun describeProbe(result: AsrConnectionTester.ProbeResult): String = when (result) {
    is AsrConnectionTester.ProbeResult.Ok -> tr(R.string.s_yuyin_lianjiechenggong)
    is AsrConnectionTester.ProbeResult.Unauthorized -> tr(R.string.s_yuyin_miyaowuxiao)
    is AsrConnectionTester.ProbeResult.EndpointNotFound -> tr(R.string.s_yuyin_dizhibudui)
    is AsrConnectionTester.ProbeResult.NoKey -> tr(R.string.s_yuyin_qingtianxiekey)
    is AsrConnectionTester.ProbeResult.Failed -> result.message
}
