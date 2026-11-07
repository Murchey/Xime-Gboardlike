package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.DoublePinyinFinals
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.declarative.ChoiceOption
import com.kingzcheung.xime.settings.declarative.SettingSpec
import com.kingzcheung.xime.settings.declarative.SettingVisibility

/**
 * 「布局与显示」页的声明式设置项。
 *
 * 目前迁移「键盘布局」段中无副作用的纯开关；字号滑杆、字母大小写等
 * （含枚举映射与自定义控件）暂保留手写，后续逐步迁移。
 */
object LayoutDisplaySettingsSpecs {

    /** 键盘布局段的纯开关项（按原页面顺序）。 */
    fun keyboardLayout(currentSchema: String): List<SettingSpec> = listOf(
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_HARDWARE_KEYBOARD_DETECTION_ENABLED,
            titleRes = R.string.s_shitijianpanjiance,
            descriptionRes = R.string.s_guanbihoubujianceshitijianpan_6aaf1,
            default = true,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_LANDSCAPE_SPLIT_KEYBOARD_ENABLED,
            titleRes = R.string.s_hengpingshiyongfentijianpan,
            descriptionRes = R.string.s_guanbihou_hengpingjiangxianshi_e98c9,
            default = false,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_KEYBOARD_FLUSH_BOTTOM,
            titleRes = R.string.s_jianpantiedixianshi,
            descriptionRes = R.string.s_yincangdibudaohanglanhuoshoush_daf00,
            default = false,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_HIDE_KEY_LABELS,
            titleRes = R.string.s_yincanganjianzifu,
            descriptionRes = R.string.s_yincanganjianzifu_fu,
            default = false,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_SHOW_LETTER_HINT_SYMBOLS,
            titleRes = R.string.s_xianshizimujianfushangfangdefuhao,
            descriptionRes = R.string.s_xianshizimujianfushangfangdefuhao_fu,
            default = true,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_SHOW_DOUBLE_PINYIN_FINALS,
            titleRes = R.string.s_shuangpin_yunmu_tihuan_fuhao,
            descriptionRes = R.string.s_shuangpin_yunmu_tihuan_fuhao_fu,
            default = false,
            // 仅双拼方案下显示
            visibility = SettingVisibility.whenTrue {
                DoublePinyinFinals.isDoublePinyin(currentSchema)
            },
        ),
        // 字母大小写用字符串枚举存值（upper/lower），开关只是它的视图
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_LETTER_DISPLAY_CASE,
            titleRes = R.string.s_zimudaxiaoxie,
            descriptionRes = R.string.s_zimudaxiaoxie_fu,
            default = false,
            onText = SettingsPreferences.LETTER_CASE_UPPER,
            offText = SettingsPreferences.LETTER_CASE_LOWER,
        ),
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_LETTER_CASE_FOLLOW_SHIFT,
            titleRes = R.string.s_gensuishiftjianqiehuan,
            descriptionRes = R.string.s_gensuishiftjianqiehuan_fu,
            default = true,
            // 大写模式下不生效：可见但禁用，并显示为关
            enabledWhen = SettingVisibility.whenText(
                SettingsPreferences.KEY_LETTER_DISPLAY_CASE,
                SettingsPreferences.LETTER_CASE_LOWER,
            ),
            offWhenDisabled = true,
        ),
    )

    /** 展开页布局：网格 / 流式（枚举存值）。 */
    val expandedLayout: List<SettingSpec> = listOf(
        SettingSpec.Choice(
            key = SettingsPreferences.KEY_EXPANDED_CANDIDATE_STYLE,
            titleRes = R.string.s_paibufangshi,
            descriptionRes = R.string.s_zhanakaiye_buju_fu,
            default = SettingsPreferences.EXPANDED_STYLE_FLOW,
            options = listOf(
                ChoiceOption(SettingsPreferences.EXPANDED_STYLE_GRID, R.string.s_wangge),
                ChoiceOption(SettingsPreferences.EXPANDED_STYLE_FLOW, R.string.s_liushi),
            ),
        ),
    )

    /** 每页候选词数（松手后由页面调用引擎立即生效）。 */
    val pageSize: List<SettingSpec> = listOf(
        SettingSpec.Slider(
            key = SettingsPreferences.KEY_PAGE_SIZE,
            titleRes = R.string.s_meiyehouxuancishu,
            default = SettingsPreferences.DEFAULT_PAGE_SIZE,
            min = 5,
            max = SettingsPreferences.MAX_PAGE_SIZE,
            step = 1,
            suffix = " 个",
        ),
    )

    /** 输入框适配段。 */
    val inputField: List<SettingSpec> = listOf(
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_AUTO_NUMBER_KEYBOARD,
            titleRes = R.string.s_shuzikuangzidongshuzijianpan,
            descriptionRes = R.string.s_jinruhaoma_yanzhengmadengshuzi_1aed9,
            default = true,
        ),
    )

    /** 候选词段：展开页逐条注释开关（依赖编码注释总开关）。 */
    val candidateComments: List<SettingSpec> = listOf(
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_SHOW_EXPANDED_CANDIDATE_COMMENTS,
            titleRes = R.string.s_zhanakaiye_xianshibianma,
            descriptionRes = R.string.s_zhanakaiye_xianshibianma_fu,
            default = false,
            enabledWhen = SettingVisibility.whenFlag(
                SettingsPreferences.KEY_SHOW_CANDIDATE_COMMENTS
            ),
        ),
    )
}
