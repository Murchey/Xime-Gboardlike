package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.declarative.SettingSpec
import com.kingzcheung.xime.settings.declarative.SettingVisibility

/**
 * 「按键效果」页的声明式设置项（迁移试点）。
 *
 * 只描述「键名 + 类型 + 文案 + 可见性」，UI 由 [DeclarativeSettingItems] 统一渲染。
 */
object KeyEffectSettingsSpecs {

    val sound: List<SettingSpec> = listOf(
        SettingSpec.Switch(
            key = SettingsPreferences.KEY_SOUND_ENABLED,
            titleRes = R.string.s_qiyonganjianyin,
            descriptionRes = R.string.s_anjianshibofangyinxiao,
            default = true,
        ),
        SettingSpec.Slider(
            key = SettingsPreferences.KEY_SOUND_VOLUME,
            titleRes = R.string.s_yinliangdaxiao,
            default = 20,
            min = 0,
            max = 100,
            step = 5,
            suffix = "%",
            visibility = SettingVisibility.whenFlag(SettingsPreferences.KEY_SOUND_ENABLED),
        ),
    )
}
