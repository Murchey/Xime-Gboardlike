package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.speech.AsrPresetCatalog

/**
 * 内置在线识别配置卡：选择服务商、填 API Key / 模型 / Base URL。
 *
 * 放在「语音转文本 → 在线」列表首位，与插件服务商并列展示；
 * 内置项无需安装插件，填完即用。
 */
@Composable
fun BuiltinAsrConfigCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    var providerId by remember {
        mutableStateOf(SettingsPreferences.getAsrProvider(context).ifBlank { AsrPresetCatalog.default.id })
    }
    val preset = AsrPresetCatalog.findOrDefault(providerId)
    var apiKey by remember(providerId) { mutableStateOf(SettingsPreferences.getAsrApiKey(context, providerId)) }
    var model by remember(providerId) {
        mutableStateOf(SettingsPreferences.getAsrModel(context, providerId).ifBlank { preset.defaultModel })
    }
    var baseUrl by remember(providerId) {
        mutableStateOf(SettingsPreferences.getAsrBaseUrl(context, providerId).ifBlank { preset.baseUrl })
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text("内置在线识别", fontWeight = FontWeight.Medium, fontSize = 15.sp)
            Text(
                "选服务商、填 API Key 即可用，无需安装插件",
                fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))

            AsrPresetCatalog.all.forEach { p ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(
                        selected = providerId == p.id,
                        onClick = {
                            providerId = p.id
                            SettingsPreferences.setAsrProvider(context, p.id)
                        }
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(p.displayName, fontSize = 14.sp)
                        Text(
                            if (p.protocol.name == "DASHSCOPE_WS") "实时流式" else "整段转写",
                            fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = apiKey,
                onValueChange = {
                    apiKey = it
                    SettingsPreferences.setAsrApiKey(context, providerId, it)
                },
                label = { Text("API Key") },
                supportingText = { Text(preset.keyHelp, fontSize = 11.sp) },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password, imeAction = ImeAction.Done
                ),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = model,
                onValueChange = {
                    model = it
                    SettingsPreferences.setAsrModel(context, providerId, it)
                },
                label = { Text("模型") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            if (preset.modelOptions.size > 1) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    preset.modelOptions.forEach { option ->
                        TextButton(onClick = {
                            model = option
                            SettingsPreferences.setAsrModel(context, providerId, option)
                        }) { Text(option.substringAfterLast('/').take(24), fontSize = 11.sp) }
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            OutlinedTextField(
                value = baseUrl,
                onValueChange = {
                    baseUrl = it
                    SettingsPreferences.setAsrBaseUrl(context, providerId, it)
                },
                label = { Text("Base URL（可留空用默认）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
