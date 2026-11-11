package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.plugin.ExtensionManager
import com.kingzcheung.xime.plugin.core.api.AsrPlugin
import com.kingzcheung.xime.plugin.core.api.PluginIcon
import com.kingzcheung.xime.plugin.core.model.PluginCategory
import com.kingzcheung.xime.plugin.core.runtime.PluginManager
import com.kingzcheung.xime.speech.AsrBackendFactory
import com.kingzcheung.xime.speech.SttEngine
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class AsrProvider(
    val id: String,
    val name: String,
    val description: String,
    val icon: ImageVector? = null,
    val pluginIcon: PluginIcon? = null,
    val isConfigured: Boolean,
    val isActive: Boolean = false,
    val features: List<String> = emptyList(),
)

/**
 * 语音转文本设置页。
 *
 * 结构：**先选引擎**（本地 / 内置在线 / 已装插件，三选一），再按引擎展开对应配置。
 * 历史实现把"本地开关"和"在线服务商列表"上下叠在一起，用户分不清当前到底在用哪一个，
 * 而且列表里还挂着一个并不存在的 `builtin_asr` 伪插件。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SpeechToTextSettingsContent(
    onBack: () -> Unit,
    onNavigateToPluginSettings: (String) -> Unit = {},
    onNavigateToPlugins: () -> Unit = {},
    /** 跳转扩展商店的模型页（本地模型未安装时，模型卡片的去下载去处）。 */
    onNavigateToModelMarket: () -> Unit = {},
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var engine by remember { mutableStateOf(SettingsPreferences.getSttEngine(context)) }
    var keepEngineAlive by remember {
        mutableStateOf(SettingsPreferences.isSttKeepEngineAlive(context))
    }
    var activePluginId by remember {
        mutableStateOf(SettingsPreferences.getSttOnlinePluginId(context))
    }
    var localReady by remember { mutableStateOf(AsrBackendFactory.getLocalName(context) != null) }
    var builtinReady by remember { mutableStateOf(AsrBackendFactory.isBuiltinConfigured(context)) }
    var builtinName by remember { mutableStateOf(AsrBackendFactory.getBuiltinName(context)) }
    // 系统语音引擎探测：结果因机型差异大，需在页面上如实展示可用性。
    var systemReady by remember { mutableStateOf(AsrBackendFactory.isSystemAvailable(context)) }
    var systemLabel by remember { mutableStateOf(AsrBackendFactory.getSystemName(context)) }

    // 插件列表在 IO 组装：isConfigured() 会进插件运行时（QuickJS），组合期同步调用会阻塞主线程
    var plugins by remember { mutableStateOf<List<AsrProvider>>(emptyList()) }
    var pluginsLoaded by remember { mutableStateOf(false) }
    LaunchedEffect(activePluginId, engine) {
        plugins = withContext(Dispatchers.IO) {
            runCatching {
                ExtensionManager.getAllInstalledPlugins()
                    .filter { it.category == PluginCategory.ASR }
                    .map { info ->
                        val instance = PluginManager.getPluginInstance(info.id) as? AsrPlugin
                        if (instance != null) {
                            val caps = instance.getCapabilities()
                            AsrProvider(
                                id = info.id,
                                name = info.name,
                                description = tr(R.string.s_zaixianyuyinshibiechajian),
                                isConfigured = instance.isConfigured(),
                                isActive = info.id == activePluginId,
                                pluginIcon = ExtensionManager.extractPluginIcon(context, info.id, instance, info),
                                features = buildList {
                                    add(
                                        if (caps.inputMode == "streaming") {
                                            tr(R.string.s_shishiliushi)
                                        } else {
                                            tr(R.string.s_wenjianshibie)
                                        }
                                    )
                                    if (caps.supportsPartialResults) add(tr(R.string.s_zhongjianjieguo))
                                    if (caps.requiresNetwork) add(tr(R.string.s_zaixian))
                                },
                            )
                        } else {
                            AsrProvider(
                                id = info.id,
                                name = info.name,
                                description = info.description.ifBlank {
                                    tr(R.string.s_zaixianyuyinshibiechajian)
                                },
                                isConfigured = false,
                                isActive = info.id == activePluginId,
                                features = listOf(tr(R.string.s_zaixian)),
                            )
                        }
                    }
            }.getOrElse { emptyList() }
        }
        pluginsLoaded = true
    }

    fun refreshReadiness() {
        localReady = AsrBackendFactory.getLocalName(context) != null
        builtinReady = AsrBackendFactory.isBuiltinConfigured(context)
        builtinName = AsrBackendFactory.getBuiltinName(context)
        systemReady = AsrBackendFactory.isSystemAvailable(context)
        systemLabel = AsrBackendFactory.getSystemName(context)
    }

    fun selectEngine(target: SttEngine) {
        engine = target
        SettingsPreferences.setSttEngine(context, target)
        when (target) {
            SttEngine.LOCAL -> if (localReady) {
                scope.launch(Dispatchers.IO) { AsrBackendFactory.warmup(context) }
            }

            SttEngine.BUILTIN_ONLINE, SttEngine.PLUGIN, SttEngine.SYSTEM -> Unit
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_yuyinzhuanwenben)) },
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
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            Spacer(Modifier.height(8.dp))

            SettingsSection(
                title = tr(R.string.s_shibieyinqing),
                modifier = Modifier.padding(horizontal = 16.dp),
                content = {
                    EngineRow(
                        title = tr(R.string.s_yinqing_bendimoxing),
                        status = if (localReady) {
                            AsrBackendFactory.getLocalName(context)
                                ?: tr(R.string.s_yinqing_yijiu)
                        } else {
                            tr(R.string.s_yinqing_weixiazaimoxing)
                        },
                        ready = localReady,
                        selected = engine == SttEngine.LOCAL,
                        onSelect = { selectEngine(SttEngine.LOCAL) },
                    )
                    EngineRow(
                        title = tr(R.string.s_yinqing_neizhianzaixian),
                        status = if (builtinReady) {
                            tr(R.string.s_neizhianzaixian_shi, builtinName)
                        } else {
                            tr(R.string.s_yinqing_weitianmiyao)
                        },
                        ready = builtinReady,
                        selected = engine == SttEngine.BUILTIN_ONLINE,
                        onSelect = {
                            selectEngine(SttEngine.BUILTIN_ONLINE)
                            refreshReadiness()
                        },
                    )
                    EngineRow(
                        title = tr(R.string.s_yinqing_chajian),
                        status = if (plugins.isEmpty()) {
                            tr(R.string.s_yinqing_weianzhuangchajian)
                        } else {
                            plugins.firstOrNull { it.id == activePluginId }?.name
                                ?: plugins.first().name
                        },
                        ready = plugins.isNotEmpty(),
                        selected = engine == SttEngine.PLUGIN,
                        onSelect = { selectEngine(SttEngine.PLUGIN) },
                    )
                    EngineRow(
                        title = tr(R.string.s_xitongyuyin),
                        status = if (systemReady) {
                            // 服务名常与标题同名（如小米的「系统语音引擎」），
                            // 相同就不重复展示，避免「系统语音引擎 · 系统语音引擎」。
                            val label = systemLabel
                            if (label.isNullOrBlank() || label == tr(R.string.s_xitongyuyin)) {
                                tr(R.string.s_xitongyuyin_keyong)
                            } else {
                                tr(R.string.s_xitongyuyin_shi, label)
                            }
                        } else {
                            tr(R.string.s_xitongyuyin_bukeyong)
                        },
                        // 不可用时仍可点：展开配置区给出具体原因，比灰掉不解释更好。
                        ready = true,
                        selected = engine == SttEngine.SYSTEM,
                        onSelect = { selectEngine(SttEngine.SYSTEM) },
                    )
                },
            )

            Spacer(Modifier.height(12.dp))

            when (engine) {
                SttEngine.LOCAL -> LocalEngineSection(
                    keepEngineAlive = keepEngineAlive,
                    onKeepEngineAliveChange = {
                        keepEngineAlive = it
                        SettingsPreferences.setSttKeepEngineAlive(context, it)
                        if (it) {
                            scope.launch(Dispatchers.IO) { AsrBackendFactory.warmup(context) }
                        } else {
                            scope.launch(Dispatchers.IO) { AsrBackendFactory.releaseModel() }
                        }
                    },
                    onNavigateToDownload = onNavigateToModelMarket,
                )

                SttEngine.BUILTIN_ONLINE -> LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                ) {
                    item {
                        BuiltinOnlineConfigSection()
                    }
                }

                SttEngine.PLUGIN -> PluginEngineSection(
                    providers = plugins,
                    loaded = pluginsLoaded,
                    activeProviderId = activePluginId,
                    onProviderSelect = { provider ->
                        val wasConfigured = provider.isConfigured
                        scope.launch(Dispatchers.IO) {
                            // 单选激活：同一时间只能使用 1 个在线 ASR 插件
                            ExtensionManager.getAllInstalledPlugins()
                                .filter { it.category == PluginCategory.ASR && it.id != provider.id }
                                .forEach { SettingsPreferences.setPluginEnabled(context, it.id, false) }
                            SettingsPreferences.setSttOnlinePluginId(context, provider.id)
                            SettingsPreferences.setPluginEnabled(context, provider.id, true)
                            PluginManager.launchPlugin(provider.id)
                            activePluginId = provider.id
                            if (!wasConfigured) {
                                withContext(Dispatchers.Main) {
                                    onNavigateToPluginSettings(provider.id)
                                }
                            }
                        }
                    },
                    onManagePlugins = onNavigateToPlugins,
                    onSettings = onNavigateToPluginSettings,
                )

                SttEngine.SYSTEM -> SystemEngineSection(
                    available = systemReady,
                    serviceLabel = systemLabel,
                )
            }
        }
    }
}

/**
 * 系统语音引擎配置区。
 *
 * 系统引擎没有任何可调参数（语言、模型、端点都由设备上的识别服务决定），
 * 这里只如实展示探测结果：可用时显示提供服务名，不可用时说明原因，
 * 避免"选了却没反应"。
 */
@Composable
private fun SystemEngineSection(
    available: Boolean,
    serviceLabel: String?,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(Modifier.padding(16.dp)) {
                    Text(
                        text = tr(R.string.s_xitongyuyin),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = if (available) {
                            val label = serviceLabel
                            if (label.isNullOrBlank() || label == tr(R.string.s_xitongyuyin)) {
                                tr(R.string.s_xitongyuyin_zhengzai)
                            } else {
                                tr(R.string.s_xitongyuyin_shi, label)
                            }
                        } else {
                            tr(R.string.s_xitongyuyin_bukeyong_shuoming)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (available) {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        } else {
                            MaterialTheme.colorScheme.error
                        },
                    )
                }
            }
        }
    }
}

/** 引擎单选行：未就绪时标灰但仍可点（点了会展开对应配置区）。 */
@Composable
private fun EngineRow(
    title: String,
    status: String,
    ready: Boolean,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
            )
            Text(
                text = status,
                style = MaterialTheme.typography.bodySmall,
                color = if (ready) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.outline
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        RadioButton(selected = selected, onClick = null)
    }
}

/** 本地引擎配置：常驻开关 + 模型管理。 */
@Composable
private fun LocalEngineSection(
    keepEngineAlive: Boolean,
    onKeepEngineAliveChange: (Boolean) -> Unit,
    onNavigateToDownload: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = tr(R.string.s_baochiyinqingchangzhu),
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = tr(R.string.s_yuyinjieshuhoumoxingbuzidongsh_2ea8d),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                )
            }
            androidx.compose.material3.Switch(
                checked = keepEngineAlive,
                onCheckedChange = onKeepEngineAliveChange,
                colors = androidx.compose.material3.SwitchDefaults.colors(
                    checkedThumbColor = MaterialTheme.colorScheme.primary,
                    checkedTrackColor = MaterialTheme.colorScheme.primaryContainer,
                ),
            )
        }
    }
    Spacer(Modifier.height(8.dp))
    OfflineAsrSettings.ModelSection(onNavigateToDownload = onNavigateToDownload)
}

/**
 * 插件引擎：只显示**当前使用**的服务（一行），切换走单选弹窗。
 * 服务商再多页面长度也恒定；选完即切换，未配置的切换后自动跳配置页。
 */
@Composable
private fun PluginEngineSection(
    providers: List<AsrProvider>,
    loaded: Boolean,
    activeProviderId: String,
    onProviderSelect: (AsrProvider) -> Unit,
    onManagePlugins: () -> Unit,
    onSettings: (String) -> Unit,
) {
    if (!loaded) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
        return
    }
    val activeProvider = providers.firstOrNull { it.id == activeProviderId }
    var showPicker by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (providers.isEmpty()) {
            item {
                Text(
                    text = tr(R.string.s_yinqing_weianzhuangchajian),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            item {
                SettingsSection(
                    title = tr(R.string.s_dangqianfuwu),
                    content = {
                        CurrentAsrProviderItem(
                            provider = activeProvider,
                            onSwitch = { showPicker = true },
                            onSettings = { activeProvider?.let { onSettings(it.id) } },
                        )
                    },
                )
            }
        }

        item {
            OutlinedButton(onClick = onManagePlugins, modifier = Modifier.fillMaxWidth()) {
                Icon(
                    Icons.Default.Extension,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(tr(R.string.s_guanlichajian))
            }
        }

        item {
            Text(
                text = tr(R.string.s_zaixian_asr_xuyaowangluolianji_2e59f),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.7f),
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }

    if (showPicker) {
        SettingsSingleChoiceDialog(
            title = tr(R.string.s_xuanzeyuyinshibiefuwu),
            options = providers.map { provider ->
                SettingsChoiceOption(
                    id = provider.id,
                    title = provider.name,
                    subtitle = buildString {
                        append(provider.description)
                        if (provider.features.isNotEmpty()) {
                            append("\n")
                            append(provider.features.joinToString(" · "))
                        }
                        append(if (provider.isConfigured) tr(R.string.s_nyipeizhi) else tr(R.string.s_nweipeizhi))
                    },
                )
            },
            selectedId = activeProviderId,
            onSelect = { pickedId ->
                showPicker = false
                providers.firstOrNull { it.id == pickedId }?.let(onProviderSelect)
            },
            onDismiss = { showPicker = false },
        )
    }
}

/**
 * 当前使用的在线识别服务（一行）：插件自己的图标 + 名称/描述 + 能力标签，
 * 右侧齿轮进配置页、整行可点打开服务选择弹窗。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CurrentAsrProviderItem(
    provider: AsrProvider?,
    onSwitch: () -> Unit,
    onSettings: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onSwitch)
            .padding(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PluginIconView(icon = provider?.pluginIcon, category = PluginCategory.ASR)
        Spacer(modifier = Modifier.width(16.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = provider?.name ?: tr(R.string.s_weixuanzefuwu),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Text(
                text = provider?.description
                    ?: tr(R.string.s_qingxuanzeyaoshiyongdezaixiany_07ac7),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (!provider?.features.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    provider.features.forEach { feature ->
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
                        ) {
                            Text(
                                text = feature,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.tertiary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }
                }
            }
        }
        if (provider != null) {
            IconButton(onClick = onSettings) {
                Icon(
                    Icons.Default.Settings,
                    contentDescription = tr(R.string.s_peizhi_3, provider.name),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp),
                )
            }
        }
        Text(
            text = if (provider == null) tr(R.string.s_xuanze) else tr(R.string.s_qiehuan),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.primary,
        )
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
