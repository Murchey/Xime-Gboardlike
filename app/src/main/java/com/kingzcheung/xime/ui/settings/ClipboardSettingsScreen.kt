package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import android.text.format.Formatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.DataUsage
import androidx.compose.material.icons.twotone.Image
import androidx.compose.material.icons.twotone.Lock
import androidx.compose.material.icons.twotone.Sync
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.clipboard.ClipboardManager
import com.kingzcheung.xime.clipboard.ImageUsage
import com.kingzcheung.xime.plugin.ActivePluginSelection
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 剪贴板设置（设置 → 数据与同步 → 剪贴板）。
 *
 * 页面只保留"需要用户决定/只有此处能看到"的东西：图片采集开关、敏感内容开关、单张上限，
 * 以及调上限时作为参照的图片占用；**清理不在这里**——剪贴板历史（文字 + 图片 + 残留）
 * 统一在 关于 → 存储空间 → 「剪贴板历史」清理，避免两个入口做同一件事。
 * 其余限制（最长边 / 保留数量 / 总容量）是内置常量，不暴露也不罗列。
 * 「剪贴板同步」的**总开关就在本页**（就地可切），整行点击进入内页做「选服务 + 配置」——
 * 同步是剪贴板的一种流向，不另立设置分组；开关上提后无需为开关专门进一层页面。
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun ClipboardSettingsContent(
    onBack: () -> Unit,
    onNavigateToClipboardSync: () -> Unit,
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val clipboardManager = remember { ClipboardManager.getInstance(context) }

    var captureEnabled by remember {
        mutableStateOf(SettingsPreferences.isClipboardImageCaptureEnabled(context))
    }
    var sensitiveCaptureEnabled by remember {
        mutableStateOf(SettingsPreferences.isClipboardSensitiveCaptureEnabled(context))
    }
    var maxMb by remember {
        mutableFloatStateOf(SettingsPreferences.getClipboardImageMaxMb(context).toFloat())
    }
    val syncEnabled = remember { SettingsPreferences.isClipboardSyncEnabled(context) }
    // 实际生效的同步服务（与引擎同一判定规则 ActivePluginSelection）：副标题要说明"谁在跑 + 能不能同步图片"
    val activeSyncPlugin = remember {
        val activeId = ActivePluginSelection.resolve(
            SettingsPreferences.getClipboardSyncPluginId(context),
            ExtensionManager.getEnabledClipboardSyncPlugins(context).map { it.first }
        )
        ExtensionManager.getAllInstalledPlugins().firstOrNull { it.id == activeId }
    }
    var usage by remember { mutableStateOf<ImageUsage?>(null) }
    var syncOn by remember { mutableStateOf(syncEnabled) }
    var maxItems by remember {
        mutableFloatStateOf(SettingsPreferences.getClipboardMaxItems(context).toFloat())
    }
    var maxItemsText by remember {
        mutableStateOf(SettingsPreferences.getClipboardMaxItems(context).toString())
    }
    var maxItemsFieldFocused by remember { mutableStateOf(false) }
    var retentionMinutes by remember {
        mutableStateOf(SettingsPreferences.getClipboardRetentionMinutes(context))
    }

    /** 提交手动输入的条数：夹到合法区间，同步滑块并立即裁剪历史。 */
    fun commitMaxItems() {
        val range = SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE
        val parsed = maxItemsText.toIntOrNull() ?: maxItems.toInt()
        val clamped = parsed.coerceIn(range.first, range.last)
        maxItems = clamped.toFloat()
        maxItemsText = clamped.toString()
        SettingsPreferences.setClipboardMaxItems(context, clamped)
        clipboardManager.enforceItemLimit()
        clipboardManager.purgeExpired()
    }

    // 占用统计读的是数据库 + 图片目录，放到 IO 线程
    LaunchedEffect(Unit) {
        usage = withContext(Dispatchers.IO) { clipboardManager.imageUsage() }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_jiantieban)) },
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
                ),
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            SettingsSection(
                title = tr(R.string.s_tupianjilu),
                content = {
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Image,
                        title = tr(R.string.s_shoujifuzhidetupian),
                        subtitle = tr(R.string.s_fuzhitupianshibaocundaojiantie_6d667),
                        checked = captureEnabled,
                        onCheckedChange = { checked ->
                            captureEnabled = checked
                            SettingsPreferences.setClipboardImageCaptureEnabled(context, checked)
                        }
                    )
                    if (!captureEnabled) {
                        Text(
                            text = tr(R.string.s_yiguanbi_zhishoujiwenzi_fuzhit_8bf4d),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            )

            SettingsSection(
                title = tr(R.string.s_minganneirong),
                content = {
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Lock,
                        title = tr(R.string.s_jilumimadengminganneirong),
                        subtitle = tr(R.string.s_morenbubaocunmimaguanliqibiaoj_9d5df),
                        checked = sensitiveCaptureEnabled,
                        onCheckedChange = { checked ->
                            sensitiveCaptureEnabled = checked
                            SettingsPreferences.setClipboardSensitiveCaptureEnabled(context, checked)
                        }
                    )
                    if (sensitiveCaptureEnabled) {
                        Text(
                            text = tr(R.string.s_yikaiqi_minganneironghuibaocun_3f987),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                        )
                    }
                }
            )

            SettingsSection(
                title = tr(R.string.s_tupianshangxian),
                content = {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 12.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr(R.string.s_danzhangzuidatiji),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${maxMb.toInt()} MB",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = maxMb,
                            onValueChange = { maxMb = it },
                            onValueChangeFinished = {
                                SettingsPreferences.setClipboardImageMaxMb(context, maxMb.toInt())
                            },
                            valueRange = SettingsPreferences.CLIPBOARD_IMAGE_MAX_MB_RANGE.first.toFloat()..
                                SettingsPreferences.CLIPBOARD_IMAGE_MAX_MB_RANGE.last.toFloat(),
                            steps = 18
                        )
                        Text(
                            text = tr(R.string.s_chaoguoshangxiandetupianbuhuib_f2e2d),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 56.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.TwoTone.DataUsage,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = tr(R.string.s_tupianzhanyong),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = usage?.let {
                                tr(R.string.s_zhang, it.count, Formatter.formatFileSize(context, it.fileBytes))
                            } ?: tr(R.string.s_tongjizhong),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            SettingsSection(
                title = tr(R.string.s_jiantiebanbaoliuguize),
                content = {
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 16.dp,
                            bottom = 12.dp
                        )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = tr(R.string.s_zuiduobaoliutiaoshu),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = maxItemsText,
                                onValueChange = { input ->
                                    val digits = input.filter { it.isDigit() }.take(4)
                                    maxItemsText = digits
                                    digits.toIntOrNull()
                                        ?.takeIf { it in SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE }
                                        ?.let { maxItems = it.toFloat() }
                                },
                                modifier = Modifier
                                    .width(100.dp)
                                    .onFocusChanged { focusState ->
                                        if (maxItemsFieldFocused && !focusState.isFocused) {
                                            commitMaxItems()
                                        }
                                        maxItemsFieldFocused = focusState.isFocused
                                    },
                                singleLine = true,
                                label = { Text(tr(R.string.s_tiaoshu_danwei)) },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(
                                    textAlign = TextAlign.End
                                ),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Number,
                                    imeAction = ImeAction.Done
                                ),
                                keyboardActions = KeyboardActions(
                                    onDone = { focusManager.clearFocus() }
                                ),
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Slider(
                            value = maxItems,
                            onValueChange = {
                                maxItems = it
                                maxItemsText = it.toInt().toString()
                            },
                            onValueChangeFinished = {
                                SettingsPreferences.setClipboardMaxItems(context, maxItems.toInt())
                                clipboardManager.enforceItemLimit()
                                clipboardManager.purgeExpired()
                            },
                            valueRange = SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.first.toFloat()..
                                SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.last.toFloat(),
                            steps = (SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.last -
                                SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.first) / 50 - 1,
                        )
                        Text(
                            text = tr(
                                R.string.s_tiaoshu_fanwei,
                                SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.first,
                                SettingsPreferences.CLIPBOARD_MAX_ITEMS_RANGE.last
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = tr(R.string.s_baoliutiaoshu_tishi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    HorizontalDivider(
                        modifier = Modifier.padding(start = 16.dp),
                        thickness = 0.5.dp,
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                    )
                    Column(
                        modifier = Modifier.padding(
                            start = 16.dp,
                            end = 16.dp,
                            top = 12.dp,
                            bottom = 16.dp
                        )
                    ) {
                        Text(
                            text = tr(R.string.s_zidongqingli),
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                        ) {
                            SettingsPreferences.CLIPBOARD_RETENTION_MINUTE_OPTIONS.forEach { minutes ->
                                FilterChip(
                                    selected = retentionMinutes == minutes,
                                    onClick = {
                                        retentionMinutes = minutes
                                        SettingsPreferences.setClipboardRetentionMinutes(context, minutes)
                                        clipboardManager.purgeExpired()
                                    },
                                    label = { Text(clipboardRetentionLabel(minutes)) }
                                )
                            }
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = tr(R.string.s_zidongqingli_tishi),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            )

            SettingsSection(
                title = tr(R.string.s_tongbu),
                content = {
                    // 开关就地上提到本页（原先藏在子页里），整行点击进子页做"选服务 + 配置"
                    val activePlugin = activeSyncPlugin
                    val subtitle = when {
                        !syncOn -> tr(R.string.s_tongguochajianyuyuanduanshebei_430fe)
                        activePlugin == null -> tr(R.string.s_yiqiyong_weixuanzetongbufuwu_d_e4fe4)
                        else -> {
                            // 能力按实际生效插件说明，避免出现"文案说仅文字、其实能同步图片"
                            val supportsImages =
                                activePlugin.capabilities?.clipboardSync?.attachments == true
                            "已启用 · ${activePlugin.name}（${
                                if (supportsImages) tr(R.string.s_wenziyutupian) else tr(R.string.s_jinwenzi)
                            }） · 点按进入服务与配置"
                        }
                    }
                    SettingsToggleItem(
                        icon = Icons.TwoTone.Sync,
                        title = tr(R.string.s_jiantiebantongbu),
                        subtitle = subtitle,
                        checked = syncOn,
                        onCheckedChange = { checked ->
                            syncOn = checked
                            SettingsPreferences.setClipboardSyncEnabled(context, checked)
                        },
                        onClick = onNavigateToClipboardSync,
                        // 右箭头表明"这一行还有下级页面"（与「语音转文本」行同一惯例）
                        showArrow = true
                    )
                }
            )
        }
    }
}

/** 自动清理时间标签：整天的显示「N天」，其余显示「N小时」。 */
private fun clipboardRetentionLabel(minutes: Int): String = when {
    minutes <= 0 -> tr(R.string.s_buzidongqingli)
    minutes % 1440 == 0 -> tr(R.string.s_tian, minutes / 1440)
    else -> tr(R.string.s_xiaoshi, minutes / 60)
}
