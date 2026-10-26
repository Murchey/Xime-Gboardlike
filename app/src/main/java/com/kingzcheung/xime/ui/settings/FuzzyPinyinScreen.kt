package com.kingzcheung.xime.ui.settings

import android.widget.Toast
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
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.R
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.rime.RimeConfigHelper
import com.kingzcheung.xime.settings.FuzzyPinyinPreferences
import com.kingzcheung.xime.settings.FuzzyPinyinRules
import com.kingzcheung.xime.settings.FuzzyPinyinStore
import com.kingzcheung.xime.settings.SchemaManager
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FuzzyPinyinSettingsContent(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val schemaId = remember { SettingsPreferences.getCurrentSchema(context) }
    val schemaName = remember(schemaId) {
        SchemaManager.getSchemaDisplayName(context, schemaId) ?: schemaId
    }
    var enabled by remember(schemaId) {
        mutableStateOf(FuzzyPinyinPreferences.enabled(context, schemaId))
    }
    var saving by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            TopAppBar(
                title = { Text(tr(R.string.s_mohuyin)) },
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
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                Text(
                    text = tr(R.string.s_dangqianfangan) + "：" + schemaName,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
                )
                Text(
                    text = tr(R.string.s_mohuyin_shuoming),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            items(FuzzyPinyinRules.ALL, key = { it.id }) { rule ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            enabled = if (rule.id in enabled) enabled - rule.id else enabled + rule.id
                        }
                        .padding(vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = rule.label,
                            style = MaterialTheme.typography.bodyLarge,
                        )
                        Text(
                            text = rule.id,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.outline,
                        )
                    }
                    Switch(
                        checked = rule.id in enabled,
                        onCheckedChange = null,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.4f))
            }

            item {
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = {
                        if (saving) return@Button
                        saving = true
                        scope.launch {
                            val ok = withContext(Dispatchers.IO) {
                                FuzzyPinyinStore.apply(context, schemaId, enabled) &&
                                    RimeConfigHelper.deployInstalledSchema(context)
                            }
                            saving = false
                            Toast.makeText(
                                context,
                                if (ok) tr(R.string.s_bushuwancheng) else tr(R.string.s_bushushibai),
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
                    Text(
                        if (saving) tr(R.string.s_baocunzhong) else tr(R.string.s_baocunbingbushu)
                    )
                }
            }
        }
    }
}
