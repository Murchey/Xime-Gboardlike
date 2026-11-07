package com.kingzcheung.xime.ui.settings

import com.kingzcheung.xime.R
import com.kingzcheung.xime.settings.SettingsPreferences
import com.kingzcheung.xime.settings.declarative.ChoiceOption
import com.kingzcheung.xime.settings.declarative.SettingSpec
import com.kingzcheung.xime.settings.declarative.SettingVisibility

/**
 * 「按键效果」页的声明式设置项（迁移试点）。
 *
 * 只描述「键名 + 类型 + 文案 + 可见性」，UI 由 [DeclarativeSettingItems] 统一渲染。
 */
object KeyEffectSettingsSpecs {

    /** 触感模式取值，与 SettingsPreferences 中的持久化值一致。 */
    const val HAPTIC_FOLLOWING_SYSTEM = "following_system"
    const val HAPTIC_ENABLED = "enabled"
    const val HAPTIC_DISABLED = "disabled"

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

    /**
     * 触感反馈段。
     *
     * @param hasAmplitudeControl 设备振动器是否支持振幅控制（运行时能力，不读偏好）
     */
    fun haptic(hasAmplitudeControl: Boolean): List<SettingSpec> {
        val modeEnabled = SettingVisibility.whenText(
            SettingsPreferences.KEY_HAPTIC_MODE, HAPTIC_ENABLED
        )
        val modeNotDisabled = SettingVisibility.whenText(
            SettingsPreferences.KEY_HAPTIC_MODE, HAPTIC_FOLLOWING_SYSTEM, HAPTIC_ENABLED
        )
        val amplitudeVisible = SettingVisibility.all(
            modeEnabled,
            SettingVisibility.whenTrue { hasAmplitudeControl },
        )
        return listOf(
            SettingSpec.Choice(
                key = SettingsPreferences.KEY_HAPTIC_MODE,
                titleRes = R.string.s_chujuefankuimoshi,
                descriptionRes = R.string.s_xuanzeshifougensuixitongshezhi,
                default = HAPTIC_FOLLOWING_SYSTEM,
                options = listOf(
                    ChoiceOption(HAPTIC_FOLLOWING_SYSTEM, R.string.s_gensuixitong),
                    ChoiceOption(HAPTIC_ENABLED, R.string.s_qiyong),
                    ChoiceOption(HAPTIC_DISABLED, R.string.s_guanbi),
                ),
            ),
            SettingSpec.Switch(
                key = SettingsPreferences.KEY_HAPTIC_ON_KEYUP,
                titleRes = R.string.s_anjiantaiqizhendong,
                descriptionRes = R.string.s_shouzhitaiqishichufaduanzhendong,
                visibility = modeNotDisabled,
            ),
            SettingSpec.Slider(
                key = SettingsPreferences.KEY_VIBRATION_PRESS_DURATION,
                titleRes = R.string.s_anjianzhendongshizhang,
                default = 0,
                min = 0,
                max = 100,
                step = 5,
                suffix = " ms",
                zeroLabelRes = R.string.s_xitongmoren,
                visibility = modeEnabled,
            ),
            SettingSpec.Slider(
                key = SettingsPreferences.KEY_VIBRATION_LONG_PRESS_DURATION,
                titleRes = R.string.s_zhanganzhendongshizhang,
                default = 0,
                min = 0,
                max = 100,
                step = 5,
                suffix = " ms",
                zeroLabelRes = R.string.s_xitongmoren,
                visibility = modeEnabled,
            ),
            SettingSpec.Slider(
                key = SettingsPreferences.KEY_VIBRATION_PRESS_AMPLITUDE,
                titleRes = R.string.s_anjianzhendongzhenfu,
                default = 0,
                min = 0,
                max = 255,
                step = 5,
                zeroLabelRes = R.string.s_xitongmoren,
                visibility = amplitudeVisible,
            ),
            SettingSpec.Slider(
                key = SettingsPreferences.KEY_VIBRATION_LONG_PRESS_AMPLITUDE,
                titleRes = R.string.s_zhanganzhendongzhenfu,
                default = 0,
                min = 0,
                max = 255,
                step = 5,
                zeroLabelRes = R.string.s_xitongmoren,
                visibility = amplitudeVisible,
            ),
        )
    }
}
