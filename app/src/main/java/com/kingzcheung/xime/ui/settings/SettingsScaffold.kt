package com.kingzcheung.xime.ui.settings

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.unit.dp
import com.kingzcheung.xime.i18n.tr
import com.kingzcheung.xime.R

/** 设置页顶栏尺寸：顶级页用大标题，详情页用普通标题。 */
enum class SettingsTopBarSize { Small, Large }

/**
 * 设置页统一骨架：页面底色、顶栏、返回键、滚动行为、内容内边距一次到位。
 *
 * 详情页不再各自重复 Scaffold + TopAppBar 的颜色样板。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsPageScaffold(
    title: String,
    modifier: Modifier = Modifier,
    onBack: (() -> Unit)? = null,
    size: SettingsTopBarSize = SettingsTopBarSize.Small,
    actions: @Composable RowScope.() -> Unit = {},
    content: @Composable (PaddingValues) -> Unit,
) {
    val pageColor = settingsPageContainerColor()
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val colors = TopAppBarDefaults.topAppBarColors(
        containerColor = pageColor,
        scrolledContainerColor = pageColor,
        titleContentColor = MaterialTheme.colorScheme.onSurface,
    )
    val navIcon: @Composable () -> Unit = {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = tr(R.string.s_fanhui),
                )
            }
        }
    }
    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = pageColor,
        topBar = {
            if (size == SettingsTopBarSize.Large) {
                LargeTopAppBar(
                    title = { Text(title) },
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            } else {
                TopAppBar(
                    title = { Text(title) },
                    navigationIcon = navIcon,
                    actions = actions,
                    colors = colors,
                    scrollBehavior = scrollBehavior,
                )
            }
        },
    ) { innerPadding ->
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .nestedScroll(scrollBehavior.nestedScrollConnection)
                .padding(horizontal = SettingsDimens.PageHorizontal)
        ) {
            content(innerPadding)
        }
    }
}
