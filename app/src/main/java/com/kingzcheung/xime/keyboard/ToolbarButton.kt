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
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material.icons.rounded.ZoomOutMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** 圆角矩形子路径：让自绘图标和 Material Rounded 图标一样是实心、圆角。 */
private fun PathBuilder.roundedRect(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    radius: Float,
) {
    moveTo(left + radius, top)
    lineTo(right - radius, top)
    quadTo(right, top, right, top + radius)
    lineTo(right, bottom - radius)
    quadTo(right, bottom, right - radius, bottom)
    lineTo(left + radius, bottom)
    quadTo(left, bottom, left, bottom - radius)
    lineTo(left, top + radius)
    quadTo(left, top, left + radius, top)
    close()
}

/**
 * 文本选择编辑图标：中间“工”，左右各一个向外指的三角形。
 *
 * 全部使用实心圆角块，重量与工具栏里的 Material Rounded 图标一致；
 * “工”上下拉高，强调这是文本选择/编辑入口，而不是键盘尺寸调节。
 */
private val TextSelectionIcon: ImageVector = ImageVector.Builder(
    name = "TextSelection",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color.Black)) {
        // 中间“工”：上下横杠 + 中间竖杠，实心圆角；整体比上一版细 1dp
        // 横杠 2.8 → 1.8，竖杠 2.4 → 1.4，外轮廓高度保持不变
        roundedRect(6.6f, 4.6f, 17.4f, 6.4f, 0.9f)
        roundedRect(6.6f, 17.6f, 17.4f, 19.4f, 0.9f)
        roundedRect(11.3f, 4.6f, 12.7f, 19.4f, 0.7f)
        // 左右向外指的三角形箭头，高度同步收 1dp
        moveTo(1.6f, 12f)
        lineTo(6.4f, 7.7f)
        lineTo(6.4f, 16.3f)
        close()
        moveTo(22.4f, 12f)
        lineTo(17.6f, 7.7f)
        lineTo(17.6f, 16.3f)
        close()
    }
}.build()

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

    /** 键盘尺寸调节：用缩放箭头图标，和文本选择编辑的“工”字图标区分。 */
    KEYBOARD_RESIZE("keyboard_resize", tr(R.string.s_jianpandiaojie), Icons.Rounded.ZoomOutMap),
    SELECT_ALL("select_all", tr(R.string.s_quanxuan), Icons.Rounded.SelectAll),
    COPY("copy", tr(R.string.s_fuzhi), Icons.Rounded.ContentCopy),
    PASTE("paste", tr(R.string.s_zhantie), Icons.Rounded.ContentPaste),
    HOME("home", tr(R.string.s_duanshou), Icons.Rounded.FirstPage),
    END("end", tr(R.string.s_duanwei), Icons.AutoMirrored.Rounded.LastPage),
    HANDWRITING_LOOKUP("handwriting_lookup", tr(R.string.s_shouxiezhaozi), Icons.Outlined.Gesture),
    /** 文本选择编辑：使用“工 + 左右三角”的专用图标。 */
    EDIT("edit", tr(R.string.s_bianji), TextSelectionIcon),
    VOICE("voice", tr(R.string.s_yuyin), Icons.Rounded.Mic),

    /** 单手模式：点一次进入（默认靠右），再点一次退出；可加入顶部快捷栏。 */
    ONE_HANDED("one_handed", tr(R.string.s_danshoumoshi), Icons.Rounded.Smartphone),

    /**
     * 隐私输入：一次开关同时关掉用户词库学习与联想记录（见 [com.kingzcheung.xime.settings.PrivacyInput]）。
     *
     * 图标语义统一为「眼睛＝输入可见/正在学习，划掉的眼睛＝已隐藏/不记录」：
     * 关闭隐私（默认）用睁眼，开启隐私后用划掉的眼；工具栏条与菜单卡片同一口径。
     */
    PRIVACY("privacy", tr(R.string.s_yinsishuru), Icons.Rounded.Visibility),

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
    /** 浮动键盘 / 独立 IME 面板等场景不能进入键盘调节，定制与显示都过滤掉。 */
    canResizeKeyboard: Boolean = true,
): Boolean = when (button) {
    ToolbarButton.HANDWRITING_LOOKUP -> hasHandwritingModel()
    ToolbarButton.VOICE -> isSttEnabled
    ToolbarButton.DEBUG_GLIDE -> isDebugLogEnabled
    ToolbarButton.KEYBOARD_RESIZE -> canResizeKeyboard
    else -> true
}

data class ToolbarAction(
    val item: ToolbarButtonItem,
    val onClick: () -> Unit,
    /** 状态化图标（隐私输入开启时用划掉的眼睛）；null = 用按钮默认图标。 */
    val iconOverride: ImageVector? = null,
)
