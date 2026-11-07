package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.GramActivation
import com.kingzcheung.xime.settings.GramPreferences
import com.kingzcheung.xime.settings.GrammarProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语法参数二级页：每个数值用文本框输入，带范围校验。
 *
 * 参数按模型保存；只有该模型已启用时才立即写入 patch 并部署，
 * 否则仅保存，等用户在上一页启用后生效。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarParamsScreen(
    language: String,
    isActive: Boolean,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val initial = remember(language) { GramPreferences.profile(context, language) }

    var maxLength by remember(language) { mutableStateOf(initial.collocationMaxLength.toString()) }
    var minLength by remember(language) { mutableStateOf(initial.collocationMinLength.toString()) }
    var collocation by remember(language) { mutableStateOf(formatNumber(initial.collocationPenalty)) }
    var nonCollocation by remember(language) { mutableStateOf(formatNumber(initial.nonCollocationPenalty)) }
    var weakCollocation by remember(language) { mutableStateOf(formatNumber(initial.weakCollocationPenalty)) }
    var rearPenalty by remember(language) { mutableStateOf(formatNumber(initial.rearPenalty)) }
    var contextual by remember(language) { mutableStateOf(initial.contextualSuggestions) }
    var saving by remember { mutableStateOf(false) }

    val maxError = validateInt(maxLength, GrammarProfile.MAX_LENGTH_RANGE)
    val maxValue = maxLength.toIntOrNull()
    val minError = if (maxValue == null) null else validateInt(minLength, 1..maxValue)
    val collocationError = validatePenalty(collocation)
    val nonCollocationError = validatePenalty(nonCollocation)
    val weakError = validatePenalty(weakCollocation)
    val rearError = validatePenalty(rearPenalty)
    val hasError = maxError != null || minError != null || collocationError != null ||
        nonCollocationError != null || weakError != null || rearError != null

    fun resetToRecommended() {
        val recommended = GrammarProfile.recommended(language)
        maxLength = recommended.collocationMaxLength.toString()
        minLength = recommended.collocationMinLength.toString()
        collocation = formatNumber(recommended.collocationPenalty)
        nonCollocation = formatNumber(recommended.nonCollocationPenalty)
        weakCollocation = formatNumber(recommended.weakCollocationPenalty)
        rearPenalty = formatNumber(recommended.rearPenalty)
        contextual = recommended.contextualSuggestions
    }

    fun save() {
        if (saving || hasError) return
        val profile = GrammarProfile(
            collocationMaxLength = maxLength.toInt(),
            collocationMinLength = minLength.toInt(),
            collocationPenalty = collocation.toDouble(),
            nonCollocationPenalty = nonCollocation.toDouble(),
            weakCollocationPenalty = weakCollocation.toDouble(),
            rearPenalty = rearPenalty.toDouble(),
            contextualSuggestions = contextual,
        ).coerce()
        saving = true
        scope.launch {
            withContext(Dispatchers.IO) {
                GramPreferences.setProfile(context, language, profile)
                if (isActive) {
                    GramActivation.applyGlobal(context, profile)
                    RimeConfigHelper.deployInstalledSchema(context)
                }
            }
            saving = false
            Toast.makeText(
                context,
                tr(if (isActive) R.string.s_bushuwancheng else R.string.s_canshu_yibaocun_qingqiyong),
                Toast.LENGTH_SHORT,
            ).show()
            onBack()
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(tr(R.string.s_yufamoxing_canshu))
                        Text(
                            language,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = tr(R.string.s_fanhui),
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                ),
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Text(
                    tr(R.string.s_yufamoxing_canshu_shuoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_zuida_shangwen_changdu),
                    value = maxLength,
                    error = maxError,
                    onValueChange = { maxLength = it },
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_zuixiao_zuhe_changdu),
                    value = minLength,
                    error = minError,
                    onValueChange = { minLength = it },
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_zuhe_chengfa),
                    value = collocation,
                    error = collocationError,
                    onValueChange = { collocation = it },
                    hint = tr(R.string.s_shuzhi_fanwei, "-40", "0"),
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_feizuhe_chengfa),
                    value = nonCollocation,
                    error = nonCollocationError,
                    onValueChange = { nonCollocation = it },
                    hint = tr(R.string.s_shuzhi_fanwei, "-40", "0"),
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_ruozuhe_chengfa),
                    value = weakCollocation,
                    error = weakError,
                    onValueChange = { weakCollocation = it },
                    hint = tr(R.string.s_shuzhi_fanwei, "-40", "0"),
                )
            }
            item {
                NumberField(
                    label = tr(R.string.s_weibu_chengfa),
                    value = rearPenalty,
                    error = rearError,
                    onValueChange = { rearPenalty = it },
                    hint = tr(R.string.s_shuzhi_fanwei, "-40", "0"),
                )
            }
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        tr(R.string.s_shangxiawen_lianxiang),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = contextual,
                        onCheckedChange = { contextual = it },
                    )
                }
            }
            item {
                TextButton(onClick = { resetToRecommended() }) {
                    Text(tr(R.string.s_huifutuijianzhi))
                }
            }
            item {
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = { save() },
                    enabled = !saving && !hasError,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    if (saving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                        )
                        Spacer(Modifier.width(8.dp))
                    }
                    Text(tr(R.string.s_baocunbingbushu))
                }
            }
        }
    }
}

@Composable
private fun NumberField(
    label: String,
    value: String,
    error: String?,
    onValueChange: (String) -> Unit,
    hint: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        singleLine = true,
        isError = error != null,
        supportingText = {
            val text = error ?: hint
            if (text != null) Text(text)
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = Modifier.fillMaxWidth(),
    )
}

private fun validateInt(text: String, range: IntRange): String? {
    val value = text.toIntOrNull()
        ?: return tr(R.string.s_zhengshu_fanwei, range.first, range.last)
    return if (value in range) null else tr(R.string.s_zhengshu_fanwei, range.first, range.last)
}

private fun validatePenalty(text: String): String? {
    val value = text.toDoubleOrNull()
        ?: return tr(R.string.s_shuzhi_fanwei, "-40", "0")
    return if (value in GrammarProfile.PENALTY_RANGE) null
    else tr(R.string.s_shuzhi_fanwei, "-40", "0")
}

private fun formatNumber(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()
