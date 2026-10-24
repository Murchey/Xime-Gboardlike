package com.kingzcheung.xime.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.twotone.Add
import androidx.compose.material.icons.twotone.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.settings.CustomColorScheme
import com.kingzcheung.xime.settings.CustomThemeStore
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.ThemeColorSet
import com.kingzcheung.xime.ui.theme.KeyboardThemes
import com.kingzcheung.xime.ui.theme.toKeyboardColorScheme

/** 可调颜色项；名称同时用于取色与保存。 */
private enum class ThemeColorField(val labelRes: Int) {
    KEYBOARD_BG(R.string.s_jianpanbeijing),
    KEY_BG(R.string.s_anjianbeijing),
    FUNCTION_KEY_BG(R.string.s_gongnengjianbeijing),
    KEY_TEXT(R.string.s_anjianwenzi),
    CANDIDATE_TEXT(R.string.s_houxuanwenzi),
    ACCENT(R.string.s_qiangdiaose),
}

private val COLOR_PRESETS = listOf(
    "#1A73E8", "#A8C8FB", "#202124", "#5F6368",
    "#F1F3F4", "#FFFFFF", "#E7E8EA", "#38393B",
    "#1E1F21", "#8D8E90", "#E8EAED", "#DADCE0",
)

/**
 * 个性化调色面板：以 Gboard 公开主题色为起点编辑浅色/深色两套颜色，
 * 保存后立即应用到键盘，并支持配色方案的 JSON 导入导出。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ThemeEditorContent(
    onBack: () -> Unit,
    onThemeChanged: () -> Unit = {},
) {
    val context = LocalContext.current
    val initialSchemes = remember { CustomThemeStore.load(context) }
    val initialLightThemeId = remember {
        SettingsPreferences.getKeyboardThemeForMode(context, isDark = false)
    }
    val initialDarkThemeId = remember {
        SettingsPreferences.getKeyboardThemeForMode(context, isDark = true)
    }
    var schemes by remember { mutableStateOf(initialSchemes) }
    var appliedLightThemeId by remember { mutableStateOf(initialLightThemeId) }
    var appliedDarkThemeId by remember { mutableStateOf(initialDarkThemeId) }
    var draft by remember {
        mutableStateOf(
            initialSchemes.firstOrNull { it.id == initialLightThemeId }
                ?: initialSchemes.firstOrNull()
                ?: CustomThemeStore.defaultScheme()
        )
    }
    var isDarkMode by remember { mutableStateOf(false) }
    var editingField by remember { mutableStateOf<ThemeColorField?>(null) }
    var pendingDelete by remember { mutableStateOf<CustomColorScheme?>(null) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageIsError by remember { mutableStateOf(false) }

    fun showMessage(text: String, isError: Boolean = false) {
        message = text
        messageIsError = isError
    }

    fun applyScheme(scheme: CustomColorScheme) {
        KeyboardThemes.reload(context)
        // 自定义方案同时包含浅色与深色色板，两套模式一起应用。
        SettingsPreferences.setKeyboardThemeForMode(context, isDark = false, themeId = scheme.id)
        SettingsPreferences.setKeyboardThemeForMode(context, isDark = true, themeId = scheme.id)
        appliedLightThemeId = scheme.id
        appliedDarkThemeId = scheme.id
        onThemeChanged()
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val json = CustomThemeStore.exportJson(listOf(draft))
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(json.toByteArray(Charsets.UTF_8))
            } ?: throw IllegalStateException("无法写入所选文件")
        }.onSuccess {
            showMessage(tr(R.string.s_daochuchenggong))
        }.onFailure {
            showMessage(tr(R.string.s_daochupese_shibai), isError = true)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                ?: throw IllegalStateException("无法读取所选文件")
            val imported = CustomThemeStore.importJson(text)
            val merged = LinkedHashMap<String, CustomColorScheme>()
            schemes.forEach { merged[it.id] = it }
            imported.forEach { merged[it.id] = it }
            val list = merged.values.toList()
            CustomThemeStore.saveAll(context, list)
            schemes = list
            draft = imported.first()
            applyScheme(draft)
            imported.size
        }.onSuccess { count ->
            showMessage(tr(R.string.s_yidaodiaosechenggong, count))
        }.onFailure { error ->
            showMessage(error.message ?: tr(R.string.s_daorushibai), isError = true)
        }
    }

    fun saveAndApply() {
        val name = draft.name.trim().ifBlank { "我的配色" }.take(CustomThemeStore.MAX_NAME_LENGTH)
        val normalized = draft.copy(name = name)
        val updated = CustomThemeStore.upsert(context, normalized)
        schemes = updated
        draft = normalized
        applyScheme(normalized)
        showMessage(tr(R.string.s_baocunyingyong))
    }

    fun newScheme() {
        draft = CustomThemeStore.defaultScheme(name = "我的配色 ${schemes.size + 1}")
        message = null
    }

    fun deleteScheme(scheme: CustomColorScheme) {
        val remaining = CustomThemeStore.delete(context, scheme.id)
        schemes = remaining
        pendingDelete = null
        if (appliedLightThemeId == scheme.id || appliedDarkThemeId == scheme.id) {
            val lightFallback = SettingsPreferences.defaultKeyboardTheme.ifBlank { "gboard_gray" }
            val darkFallback = SettingsPreferences.defaultDarkKeyboardTheme.ifBlank { "gboard_black" }
            if (appliedLightThemeId == scheme.id) {
                SettingsPreferences.setKeyboardThemeForMode(context, isDark = false, themeId = lightFallback)
                appliedLightThemeId = lightFallback
            }
            if (appliedDarkThemeId == scheme.id) {
                SettingsPreferences.setKeyboardThemeForMode(context, isDark = true, themeId = darkFallback)
                appliedDarkThemeId = darkFallback
            }
            onThemeChanged()
        }
        if (draft.id == scheme.id) {
            draft = remaining.firstOrNull() ?: CustomThemeStore.defaultScheme()
        }
        KeyboardThemes.reload(context)
        showMessage(tr(R.string.s_yishanchupese, scheme.name))
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_diaoseban)) },
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
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Text(
                    text = tr(R.string.s_diaoseban_shuoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item { ThemePreviewCard(draft, isDarkMode, onModeChange = { isDarkMode = it }) }

            item {
                SettingsSection(title = tr(R.string.s_mingcheng)) {
                    OutlinedTextField(
                        value = draft.name,
                        onValueChange = {
                            draft = draft.copy(name = it.take(CustomThemeStore.MAX_NAME_LENGTH))
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        singleLine = true,
                        placeholder = { Text("我的配色") },
                    )
                }
            }

            item {
                val colorSet = if (isDarkMode) draft.dark else draft.light
                SettingsSection(
                    title = if (isDarkMode) tr(R.string.s_shensepeise) else tr(R.string.s_qiansepeise)
                ) {
                    ThemeColorField.entries.forEachIndexed { index, field ->
                        if (index > 0) {
                            HorizontalDivider(
                                modifier = Modifier.padding(start = 16.dp),
                                thickness = 0.5.dp,
                                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        }
                        ThemeColorRow(
                            label = tr(field.labelRes),
                            value = colorSet.valueFor(field),
                            onClick = { editingField = field },
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { saveAndApply() },
                        modifier = Modifier.weight(1.6f),
                    ) {
                        Text(tr(R.string.s_baocunyingyong))
                    }
                    OutlinedButton(
                        onClick = { newScheme() },
                        modifier = Modifier.weight(1f),
                    ) {
                        Icon(Icons.TwoTone.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(tr(R.string.s_xinjian))
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            importLauncher.launch(
                                arrayOf("application/json", "text/json", "text/plain", "*/*")
                            )
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(tr(R.string.s_daorudiaose))
                    }
                    OutlinedButton(
                        onClick = {
                            val safeName = draft.name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                                .ifBlank { "xime-theme" }
                            exportLauncher.launch("$safeName.json")
                        },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(tr(R.string.s_daochudiaose))
                    }
                }
            }

            message?.let { text ->
                item {
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (messageIsError) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                }
            }

            item {
                SettingsSection(title = tr(R.string.s_wodepeise)) {
                    if (schemes.isEmpty()) {
                        Text(
                            text = tr(R.string.s_wupeise),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(16.dp)
                        )
                    } else {
                        schemes.forEachIndexed { index, scheme ->
                            if (index > 0) {
                                HorizontalDivider(
                                    modifier = Modifier.padding(start = 16.dp),
                                    thickness = 0.5.dp,
                                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                                )
                            }
                            SavedSchemeRow(
                                scheme = scheme,
                                isApplied = appliedLightThemeId == scheme.id ||
                                    appliedDarkThemeId == scheme.id,
                                onEdit = { draft = scheme },
                                onApply = {
                                    applyScheme(scheme)
                                    showMessage(tr(R.string.s_yiyongpese, scheme.name))
                                },
                                onDelete = { pendingDelete = scheme },
                            )
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    editingField?.let { field ->
        val colorSet = if (isDarkMode) draft.dark else draft.light
        ColorPickerDialog(
            title = tr(field.labelRes),
            initialHex = colorSet.valueFor(field),
            onDismiss = { editingField = null },
            onConfirm = { hex ->
                draft = draft.withColor(field, isDarkMode, hex)
                editingField = null
            },
        )
    }

    pendingDelete?.let { scheme ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(tr(R.string.s_shanchupese)) },
            text = { Text(tr(R.string.s_shanchupese_queren, scheme.name)) },
            confirmButton = {
                TextButton(onClick = { deleteScheme(scheme) }) {
                    Text(tr(R.string.s_shanchu), color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(tr(R.string.s_quxiao)) }
            }
        )
    }
}

@Composable
private fun ThemePreviewCard(
    scheme: CustomColorScheme,
    isDarkMode: Boolean,
    onModeChange: (Boolean) -> Unit,
) {
    val previewScheme = remember(scheme) { scheme.toKeyboardColorScheme() }
    val colorSet = if (isDarkMode) scheme.dark else scheme.light
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = tr(R.string.s_yulan),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.weight(1f),
                )
                FilterChip(
                    selected = !isDarkMode,
                    onClick = { onModeChange(false) },
                    label = { Text(tr(R.string.s_qianse)) },
                )
                Spacer(modifier = Modifier.width(6.dp))
                FilterChip(
                    selected = isDarkMode,
                    onClick = { onModeChange(true) },
                    label = { Text(tr(R.string.s_shense)) },
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            ThemeKeyboardPreview(
                theme = previewScheme,
                isDark = isDarkMode,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp),
                keyColorOverride = colorSet.keyBackground.toComposeColor(),
                candidateTextColorOverride = colorSet.candidateText.toComposeColor(),
                selectedTextColorOverride = colorSet.accent.toComposeColor(),
            )
        }
    }
}

@Composable
private fun ThemeColorRow(label: String, value: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = value.uppercase(),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Box(
            modifier = Modifier
                .size(28.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(value.toComposeColor())
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
        )
    }
}

@Composable
private fun SavedSchemeRow(
    scheme: CustomColorScheme,
    isApplied: Boolean,
    onEdit: () -> Unit,
    onApply: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 16.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            listOf(
                scheme.light.keyboardBackground,
                scheme.light.keyBackground,
                scheme.light.accent,
            ).forEach { hex ->
                Box(
                    modifier = Modifier
                        .size(14.dp)
                        .clip(CircleShape)
                        .background(hex.toComposeColor())
                        .border(0.5.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
                )
            }
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = scheme.name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isApplied) FontWeight.SemiBold else FontWeight.Normal,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                text = if (isApplied) tr(R.string.s_dangqian) else tr(R.string.s_dianjibianji),
                style = MaterialTheme.typography.bodySmall,
                color = if (isApplied) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (!isApplied) {
            TextButton(onClick = onApply) { Text(tr(R.string.s_yingyong)) }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.TwoTone.DeleteOutline,
                contentDescription = tr(R.string.s_shanchu),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ColorPickerDialog(
    title: String,
    initialHex: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var color by remember { mutableStateOf(initialHex.toComposeColor()) }
    var hexText by remember { mutableStateOf(initialHex.uppercase()) }
    var hexError by remember { mutableStateOf(false) }
    val hsv = remember(color) {
        FloatArray(3).also { android.graphics.Color.colorToHSV(color.toArgb(), it) }
    }

    fun syncHex(newColor: Color) {
        color = newColor
        hexText = CustomThemeStore.formatHexColor(newColor.toArgb().toLong() and 0xFFFFFFFFL)
        hexError = false
    }

    fun updateComponent(index: Int, value: Float) {
        val values = hsv.copyOf()
        values[index] = value
        syncHex(Color(android.graphics.Color.HSVToColor(values)))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(color)
                            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    OutlinedTextField(
                        value = hexText,
                        onValueChange = { input ->
                            hexText = input.uppercase()
                            val parsed = CustomThemeStore.parseHexColor(input)
                            hexError = parsed == null && input.length >= 6
                            if (parsed != null) color = Color(parsed.toInt())
                        },
                        modifier = Modifier.weight(1f),
                        singleLine = true,
                        label = { Text("HEX") },
                        isError = hexError,
                        supportingText = if (hexError) {
                            { Text(tr(R.string.s_yansegeshicuowu)) }
                        } else null,
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                SliderRow(tr(R.string.s_sexian), hsv[0], 0f..360f) { updateComponent(0, it) }
                SliderRow(tr(R.string.s_baohedu), hsv[1], 0f..1f) { updateComponent(1, it) }
                SliderRow(tr(R.string.s_mingdu), hsv[2], 0f..1f) { updateComponent(2, it) }

                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = tr(R.string.s_kuaijieyanse),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COLOR_PRESETS.take(6).forEach { hex ->
                        ColorPresetDot(hex) { syncHex(hex.toComposeColor()) }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    COLOR_PRESETS.drop(6).forEach { hex ->
                        ColorPresetDot(hex) { syncHex(hex.toComposeColor()) }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    if (!hexError) {
                        onConfirm(
                            CustomThemeStore.normalizeHexColor(hexText)
                                ?: CustomThemeStore.formatHexColor(
                                    color.toArgb().toLong() and 0xFFFFFFFFL
                                )
                        )
                    }
                }
            ) { Text(tr(R.string.s_queding)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(tr(R.string.s_quxiao)) }
        },
    )
}

@Composable
private fun SliderRow(label: String, value: Float, range: ClosedFloatingPointRange<Float>, onChange: (Float) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(52.dp),
        )
        Slider(
            value = value,
            onValueChange = onChange,
            valueRange = range,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ColorPresetDot(hex: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(26.dp)
            .clip(CircleShape)
            .background(hex.toComposeColor())
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, CircleShape)
            .clickable(onClick = onClick)
    )
}

private fun ThemeColorSet.valueFor(field: ThemeColorField): String = when (field) {
    ThemeColorField.KEYBOARD_BG -> keyboardBackground
    ThemeColorField.KEY_BG -> keyBackground
    ThemeColorField.FUNCTION_KEY_BG -> functionKeyBackground
    ThemeColorField.KEY_TEXT -> keyText
    ThemeColorField.CANDIDATE_TEXT -> candidateText
    ThemeColorField.ACCENT -> accent
}

private fun ThemeColorSet.withValue(field: ThemeColorField, value: String): ThemeColorSet = when (field) {
    ThemeColorField.KEYBOARD_BG -> copy(keyboardBackground = value)
    ThemeColorField.KEY_BG -> copy(keyBackground = value)
    ThemeColorField.FUNCTION_KEY_BG -> copy(functionKeyBackground = value)
    ThemeColorField.KEY_TEXT -> copy(keyText = value)
    ThemeColorField.CANDIDATE_TEXT -> copy(candidateText = value)
    ThemeColorField.ACCENT -> copy(accent = value)
}

private fun CustomColorScheme.withColor(field: ThemeColorField, isDark: Boolean, value: String): CustomColorScheme =
    if (isDark) copy(dark = dark.withValue(field, value))
    else copy(light = light.withValue(field, value))

private fun String.toComposeColor(): Color =
    Color((CustomThemeStore.parseHexColor(this) ?: 0xFF808080).toInt())
