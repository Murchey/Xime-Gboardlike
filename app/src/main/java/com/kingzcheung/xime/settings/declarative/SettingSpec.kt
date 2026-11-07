package com.kingzcheung.xime.settings.declarative

import androidx.annotation.StringRes

/** 设置项的取值（与类型一一对应，避免各页面自己拆装箱）。 */
sealed interface SettingValue {
    data class Flag(val value: Boolean) : SettingValue
    data class Number(val value: Int) : SettingValue
    data class Text(val value: String) : SettingValue
}

/** 单选（chips）的一个选项：持久化值 + 文案资源。 */
data class ChoiceOption(val value: String, @StringRes val labelRes: Int)

/**
 * 可见性：依赖另一个设置项的取值。
 *
 * - [whenFlag]：依赖开关（如"音量"仅当"启用按键音"开启时显示）
 * - [whenText]：依赖枚举值（如"振动时长"仅当触感模式为 enabled 时显示）
 */
data class SettingVisibility(
    val key: String,
    private val allowedTexts: Set<String> = emptySet(),
    private val requiredFlag: Boolean? = null,
) {
    fun evaluate(store: SettingsStore): Boolean = when {
        requiredFlag != null -> store.flag(key, false) == requiredFlag
        allowedTexts.isNotEmpty() -> store.text(key, "") in allowedTexts
        else -> true
    }

    companion object {
        fun whenFlag(key: String, expected: Boolean = true): SettingVisibility =
            SettingVisibility(key, requiredFlag = expected)

        fun whenText(key: String, vararg values: String): SettingVisibility =
            SettingVisibility(key, allowedTexts = values.toSet())
    }
}

/** 一条设置项的声明：类型、键、文案与可见性。 */
sealed interface SettingSpec {
    val key: String
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int?
    val visibility: SettingVisibility?

    /** 开关行。 */
    data class Switch(
        override val key: String,
        override val titleRes: Int,
        override val descriptionRes: Int? = null,
        val default: Boolean = false,
        override val visibility: SettingVisibility? = null,
    ) : SettingSpec

    /** 滑杆行；[suffix] 为数值后缀（如 "%"），[zeroLabelRes] 用于 0 值显示"系统默认"。 */
    data class Slider(
        override val key: String,
        override val titleRes: Int,
        override val descriptionRes: Int? = null,
        val default: Int,
        val min: Int,
        val max: Int,
        val step: Int = 1,
        val suffix: String? = null,
        @StringRes val zeroLabelRes: Int? = null,
        override val visibility: SettingVisibility? = null,
    ) : SettingSpec {
        init {
            require(max > min) { "slider range must be increasing" }
        }

        fun coerce(value: Int): Int = value.coerceIn(min, max)
    }

    /** 单选行（chips）。 */
    data class Choice(
        override val key: String,
        override val titleRes: Int,
        override val descriptionRes: Int? = null,
        val default: String,
        val options: List<ChoiceOption>,
        override val visibility: SettingVisibility? = null,
    ) : SettingSpec
}

/** 声明对应的默认值。 */
fun SettingSpec.defaultValue(): SettingValue = when (this) {
    is SettingSpec.Switch -> SettingValue.Flag(default)
    is SettingSpec.Slider -> SettingValue.Number(coerce(default))
    is SettingSpec.Choice -> SettingValue.Text(
        if (options.any { it.value == default }) default else options.first().value
    )
}

/** 从存储读取当前值；未设置时回落到声明默认值。 */
fun SettingSpec.read(store: SettingsStore): SettingValue = when (this) {
    is SettingSpec.Switch -> SettingValue.Flag(store.flag(key, default))
    is SettingSpec.Slider -> SettingValue.Number(coerce(store.number(key, coerce(default))))
    is SettingSpec.Choice -> SettingValue.Text(
        store.text(key, default).takeIf { saved -> options.any { it.value == saved } }
            ?: (defaultValue() as SettingValue.Text).value
    )
}

/** 写入存储；滑杆值越界时收敛到声明范围。 */
fun SettingSpec.write(store: SettingsStore, value: SettingValue) {
    when (this) {
        is SettingSpec.Switch -> store.putFlag(key, (value as SettingValue.Flag).value)
        is SettingSpec.Slider -> store.putNumber(key, coerce((value as SettingValue.Number).value))
        is SettingSpec.Choice -> {
            val text = (value as SettingValue.Text).value
            check(options.any { it.value == text }) { "unknown choice '$text' for $key" }
            store.putText(key, text)
        }
    }
}
