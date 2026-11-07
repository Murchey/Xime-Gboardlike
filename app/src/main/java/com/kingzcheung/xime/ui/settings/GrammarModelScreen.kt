package com.kingzcheung.xime.ui.settings

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.DeleteOutline
import androidx.compose.material.icons.twotone.FileDownload
import androidx.compose.material.icons.twotone.FileUpload
import androidx.compose.material.icons.twotone.RadioButtonChecked
import androidx.compose.material.icons.twotone.RadioButtonUnchecked
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.GramActivation
import com.kingzcheung.xime.settings.GramImporter
import com.kingzcheung.xime.settings.GramPreferences
import com.kingzcheung.xime.settings.GrammarProfile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 语法模型管理页：导入/导出/删除 .gram，选择一个全局启用（对所有方案生效），
 * 保存后写入各方案 <schema>.custom.yaml 的 grammar patch 并重新部署。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GrammarModelSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var models by remember { mutableStateOf(GramImporter.list(context)) }
    var selected by remember { mutableStateOf(GramPreferences.activeLanguage(context)) }
    // 语法参数按模型分别保存：切换模型时自动载入该模型上次保存的值（未保存过则为推荐值）
    var profile by remember(selected) { mutableStateOf(GramPreferences.profile(context, selected)) }
    var saving by remember { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<GramImporter.GramFile?>(null) }
    var exportTarget by remember { mutableStateOf<GramImporter.GramFile?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    runCatching { GramImporter.importFromUri(context, uri) }.isSuccess
                }
                if (ok) models = GramImporter.list(context)
                Toast.makeText(
                    context,
                    if (ok) tr(R.string.s_daoruchenggong_yifangru_rime_mulu) else tr(R.string.s_daorushibai),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        val target = exportTarget
        exportTarget = null
        if (uri != null && target != null) {
            scope.launch {
                val ok = withContext(Dispatchers.IO) {
                    GramExporter.export(context, target, uri)
                }
                Toast.makeText(
                    context,
                    if (ok) tr(R.string.s_yufamoxing_daochuchenggong) else tr(R.string.s_daorushibai),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_yufamoxing)) },
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
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    tr(R.string.s_yufamoxing_shuoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(8.dp))
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            selected = ""
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    GramPreferences.setActiveLanguage(context, "")
                                    GramActivation.applyGlobal(context)
                                    RimeConfigHelper.deployInstalledSchema(context)
                                }
                            }
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(
                        imageVector = if (selected.isEmpty()) {
                            Icons.TwoTone.RadioButtonChecked
                        } else {
                            Icons.TwoTone.RadioButtonUnchecked
                        },
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(tr(R.string.s_yufamoxing_tingyong))
                }
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                )
            }

            items(models, key = { it.name }) { model ->
                val active = selected == GramImporter.displayName(model.name)
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                selected = GramImporter.displayName(model.name)
                            }
                            .padding(vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            imageVector = if (active) {
                                Icons.TwoTone.RadioButtonChecked
                            } else {
                                Icons.TwoTone.RadioButtonUnchecked
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                GramImporter.displayName(model.name),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                            Text(
                                GramImporter.formatSize(model.sizeBytes),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        IconButton(onClick = {
                            exportTarget = model
                            exportLauncher.launch(model.name)
                        }) {
                            Icon(
                                imageVector = Icons.TwoTone.FileDownload,
                                contentDescription = tr(R.string.s_yufamoxing_daochu),
                            )
                        }
                        IconButton(onClick = { pendingDelete = model }) {
                            Icon(
                                imageVector = Icons.TwoTone.DeleteOutline,
                                contentDescription = tr(R.string.s_yufamoxing_shanchu),
                                tint = MaterialTheme.colorScheme.error,
                            )
                        }
                    }
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f),
                    )
                }
            }

            if (selected.isNotEmpty()) {
                item {
                    GrammarProfileCard(
                        profile = profile,
                        onChange = { profile = it },
                        onReset = { profile = GrammarProfile.recommended(selected) },
                    )
                }
            }

            item {
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(
                        imageVector = Icons.TwoTone.FileUpload,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(tr(R.string.s_yufamoxing_daoru))
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            withContext(Dispatchers.IO) {
                                GramPreferences.setActiveLanguage(context, selected)
                                val effective = if (selected.isEmpty()) GrammarProfile() else profile.coerce()
                                if (selected.isNotEmpty()) {
                                    GramPreferences.setProfile(context, selected, effective)
                                }
                                val failed = GramActivation.applyGlobal(context, effective)
                                if (failed == 0) {
                                    RimeConfigHelper.deployInstalledSchema(context)
                                }
                            }
                            saving = false
                            Toast.makeText(
                                context,
                                tr(R.string.s_bushuwancheng),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    enabled = !saving,
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

    pendingDelete?.let { model ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr(R.string.s_yufamoxing_shanchu)) },
            text = { Text(tr(R.string.s_yufamoxing_shanchuqueren)) },
            confirmButton = {
                TextButton(onClick = {
                    val target = model
                    pendingDelete = null
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            GramImporter.delete(context, target.name)
                            // 删掉的正是启用中的模型：立即停用并重部署，不留悬空引用
                            if (selected == GramImporter.displayName(target.name)) {
                                selected = ""
                                GramPreferences.setActiveLanguage(context, "")
                                GramActivation.applyGlobal(context)
                                RimeConfigHelper.deployInstalledSchema(context)
                            }
                        }
                        models = GramImporter.list(context)
                    }
                }) {
                    Text(tr(R.string.s_yufamoxing_shanchu))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text(tr(R.string.action_cancel))
                }
            },
        )
    }
}


@Composable
private fun GrammarProfileCard(
    profile: GrammarProfile,
    onChange: (GrammarProfile) -> Unit,
    onReset: () -> Unit,
) {
    Card(Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                tr(R.string.s_yufamoxing_canshu),
                style = MaterialTheme.typography.titleMedium,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                tr(R.string.s_yufamoxing_canshu_shuoming),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            IntParamSlider(
                label = tr(R.string.s_zuida_shangwen_changdu),
                value = profile.collocationMaxLength,
                range = GrammarProfile.MAX_LENGTH_RANGE,
            ) { onChange(profile.copy(collocationMaxLength = it)) }
            IntParamSlider(
                label = tr(R.string.s_zuixiao_zuhe_changdu),
                value = profile.collocationMinLength,
                range = 1..profile.collocationMaxLength,
            ) { onChange(profile.copy(collocationMinLength = it)) }
            PenaltyParamSlider(
                label = tr(R.string.s_zuhe_chengfa),
                value = profile.collocationPenalty,
            ) { onChange(profile.copy(collocationPenalty = it)) }
            PenaltyParamSlider(
                label = tr(R.string.s_feizuhe_chengfa),
                value = profile.nonCollocationPenalty,
            ) { onChange(profile.copy(nonCollocationPenalty = it)) }
            PenaltyParamSlider(
                label = tr(R.string.s_ruozuhe_chengfa),
                value = profile.weakCollocationPenalty,
            ) { onChange(profile.copy(weakCollocationPenalty = it)) }
            PenaltyParamSlider(
                label = tr(R.string.s_weibu_chengfa),
                value = profile.rearPenalty,
            ) { onChange(profile.copy(rearPenalty = it)) }
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    tr(R.string.s_shangxiawen_lianxiang),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.weight(1f))
                Switch(
                    checked = profile.contextualSuggestions,
                    onCheckedChange = { onChange(profile.copy(contextualSuggestions = it)) },
                )
            }
            TextButton(onClick = onReset) {
                Text(tr(R.string.s_huifutuijianzhi))
            }
        }
    }
}

@Composable
private fun IntParamSlider(
    label: String,
    value: Int,
    range: IntRange,
    onValueChange: (Int) -> Unit,
) {
    ParamHeader(label, value.toString())
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().coerceIn(range)) },
        valueRange = range.first.toFloat()..range.last.toFloat(),
        steps = (range.last - range.first - 1).coerceAtLeast(0),
    )
}

@Composable
private fun PenaltyParamSlider(
    label: String,
    value: Double,
    onValueChange: (Double) -> Unit,
) {
    ParamHeader(label, formatPenalty(value))
    Slider(
        value = value.toFloat(),
        onValueChange = { onValueChange(it.roundToInt().toDouble()) },
        valueRange = GrammarProfile.PENALTY_RANGE.start.toFloat()..
            GrammarProfile.PENALTY_RANGE.endInclusive.toFloat(),
        steps = 39,
    )
}

@Composable
private fun ParamHeader(label: String, valueText: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium)
        Text(
            valueText,
            style = MaterialTheme.typography.bodyMedium,
            fontFamily = FontFamily.Monospace,
        )
    }
}

private fun formatPenalty(value: Double): String =
    if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString()

/** 导出辅助：SAF 写出，单文件复制。 */
private object GramExporter {
    fun export(
        context: android.content.Context,
        model: GramImporter.GramFile,
        uri: android.net.Uri,
    ): Boolean = GramImporter.exportToUri(context, model.name, uri)
}
