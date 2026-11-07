package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.DoublePinyinFinals
import com.kingzcheung.xime.settings.SettingsPreferences
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
    )
}
