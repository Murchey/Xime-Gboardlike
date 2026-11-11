package com.kingzcheung.xime.keyboard

import com.kingzcheung.xime.R

import com.kingzcheung.xime.i18n.tr

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Assignment
import androidx.compose.material.icons.automirrored.rounded.LastPage
import androidx.compose.material.icons.rounded.ContentCopy
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.EmojiEmotions
import androidx.compose.material.icons.rounded.FirstPage
import androidx.compose.material.icons.rounded.KeyboardAlt
import androidx.compose.material.icons.rounded.LastPage
import androidx.compose.material.icons.rounded.Paid
import androidx.compose.material.icons.rounded.Quickreply
import androidx.compose.material.icons.rounded.SelectAll
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Create
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PictureInPicture
import androidx.compose.material.icons.outlined.BugReport
import androidx.compose.material.icons.outlined.Gesture
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.ui.graphics.vector.ImageVector

enum class ToolbarButton(
    val id: String,
    val label: String,
    val icon: ImageVector
) {
    SETTINGS("settings", tr(R.string.s_shezhi), Icons.Rounded.Settings),
    EMOJI("emoji", tr(R.string.s_biaoqing), Icons.Rounded.EmojiEmotions),
    CLIPBOARD("clipboard", tr(R.string.s_jiantieban), Icons.AutoMirrored.Rounded.Assignment),
    SCHEMA("schema", tr(R.string.s_fanganxuanze), Icons.Rounded.KeyboardAlt),
    QUICK_PHRASE("quick_phrase", tr(R.string.s_kuaijiefasong), Icons.Rounded.Quickreply),
    SYMBOL("symbol", tr(R.string.s_fuhao), Icons.Rounded.Paid),
    FLOAT("float", tr(R.string.s_xuanfu), Icons.Rounded.PictureInPicture),
    SELECT_ALL("select_all", tr(R.string.s_quanxuan), Icons.Rounded.SelectAll),
    COPY("copy", tr(R.string.s_fuzhi), Icons.Rounded.ContentCopy),
    PASTE("paste", tr(R.string.s_zhantie), Icons.Rounded.ContentPaste),
    HOME("home", tr(R.string.s_duanshou), Icons.Rounded.FirstPage),
    END("end", tr(R.string.s_duanwei), Icons.AutoMirrored.Rounded.LastPage),
    HANDWRITING_LOOKUP("handwriting_lookup", tr(R.string.s_shouxiezhaozi), Icons.Outlined.Gesture),
    EDIT("edit", tr(R.string.s_bianji), Icons.Rounded.Edit),
    VOICE("voice", tr(R.string.s_yuyin), Icons.Rounded.Mic),

    /** 开发者调试用：一键开关滑动轨迹采集，并在同一面板里标记真实意图。 */
    DEBUG_GLIDE("debug_glide", tr(R.string.s_debug_huadongbiaoj), Icons.Outlined.BugReport);

    companion object {
        /** Gboard 风格的首次启用工具：设置、表情、剪贴板、方案切换和快捷短语。 */
        val DEFAULT_VISIBLE = listOf(SETTINGS, EMOJI, CLIPBOARD, SCHEMA, QUICK_PHRASE)

        fun fromId(id: String): ToolbarButton? =
            entries.find { it.id == id }
    }
}

/**
 * 内置工具栏按钮当前是否可用。
 *
 * 不可用的按钮既要从候选栏隐藏（KeyboardView），也要从定制列表里去掉
 * （ToolbarCustomizeView）——两处必须同一口径，否则会出现「定制里能勾选、
 * 键盘上却不显示」的幽灵按钮。
 *
 * [hasHandwritingModel] 按需取值：只有「手写找字」需要探测本地模型。
 */
fun isBuiltinToolbarButtonAvailable(
    button: ToolbarButton,
    isSttEnabled: Boolean,
    hasHandwritingModel: () -> Boolean,
    /** 调试日志总开关：关着时调试类按钮（滑动标记）不出现，避免误触日常使用。 */
    isDebugLogEnabled: Boolean = false,
): Boolean = when (button) {
    ToolbarButton.HANDWRITING_LOOKUP -> hasHandwritingModel()
    ToolbarButton.VOICE -> isSttEnabled
    ToolbarButton.DEBUG_GLIDE -> isDebugLogEnabled
    else -> true
}

data class ToolbarAction(
    val item: ToolbarButtonItem,
    val onClick: () -> Unit
)
