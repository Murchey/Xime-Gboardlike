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
 * 可见性：依赖其他设置项的取值，或设备能力。
 *
 * - [whenFlag]：依赖开关（如"音量"仅当"启用按键音"开启时显示）
 * - [whenText]：依赖枚举值（如"振动时长"仅当触感模式为 enabled 时显示）
 * - [all]：多个条件同时满足（如振幅需模式 enabled 且设备支持振幅控制）
 * - [whenTrue]：设备/运行时能力（不读偏好），如振动器是否支持振幅
 */
sealed interface SettingVisibility {
    fun evaluate(store: SettingsStore): Boolean

    data class Flag(val key: String, val expected: Boolean) : SettingVisibility {
        override fun evaluate(store: SettingsStore): Boolean =
            store.flag(key, false) == expected
    }

    data class Text(val key: String, val allowed: Set<String>) : SettingVisibility {
        override fun evaluate(store: SettingsStore): Boolean = store.text(key, "") in allowed
    }

    data class All(val conditions: List<SettingVisibility>) : SettingVisibility {
        override fun evaluate(store: SettingsStore): Boolean = conditions.all { it.evaluate(store) }
    }

    data class Static(val condition: () -> Boolean) : SettingVisibility {
        override fun evaluate(store: SettingsStore): Boolean = condition()
    }

    companion object {
        fun whenFlag(key: String, expected: Boolean = true): SettingVisibility =
            Flag(key, expected)

        fun whenText(key: String, vararg values: String): SettingVisibility =
            Text(key, values.toSet())

        fun all(vararg conditions: SettingVisibility): SettingVisibility =
            All(conditions.toList())

        fun whenTrue(condition: () -> Boolean): SettingVisibility = Static(condition)
    }
}

/** 一条设置项的声明：类型、键、文案与可见性。 */
sealed interface SettingSpec {
    val key: String
    @get:StringRes val titleRes: Int
    @get:StringRes val descriptionRes: Int?
    val visibility: SettingVisibility?

    /** 可见但禁用（如"跟随 Shift"仅在小写模式下可用）；null = 始终可用。 */
    val enabledWhen: SettingVisibility?

    /**
     * 开关行。
     *
     * [onText]/[offText] 同时非空时，用字符串枚举存值（如字母大小写 upper/lower），
     * 开关只是它的一种视图。
     */
    data class Switch(
        override val key: String,
        override val titleRes: Int,
        override val descriptionRes: Int? = null,
        val default: Boolean = false,
        val onText: String? = null,
        val offText: String? = null,
        /** 禁用时强制显示为关（联动项"不生效"时更直观）。 */
        val offWhenDisabled: Boolean = false,
        override val visibility: SettingVisibility? = null,
        override val enabledWhen: SettingVisibility? = null,
    ) : SettingSpec {
        init {
            require((onText == null) == (offText == null)) {
                "onText / offText must be provided together"
            }
        }

        val isTextBacked: Boolean get() = onText != null
    }

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
        override val enabledWhen: SettingVisibility? = null,
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
        override val enabledWhen: SettingVisibility? = null,
    ) : SettingSpec

    /**
     * 悬浮菜单单选行：点击行弹出悬浮 panel 选择，不在页面上平铺 chips。
     *
     * 选项较多（如底部工具条可绑定的功能）时用这种形态，列表更清爽。
     */
    data class Menu(
        override val key: String,
        override val titleRes: Int,
        override val descriptionRes: Int? = null,
        val default: String,
        val options: List<ChoiceOption>,
        override val visibility: SettingVisibility? = null,
        override val enabledWhen: SettingVisibility? = null,
    ) : SettingSpec
}

/** 声明对应的默认值。 */
fun SettingSpec.defaultValue(): SettingValue = when (this) {
    is SettingSpec.Switch -> SettingValue.Flag(default)
    is SettingSpec.Slider -> SettingValue.Number(coerce(default))
    is SettingSpec.Choice -> SettingValue.Text(
        if (options.any { it.value == default }) default else options.first().value
    )
    is SettingSpec.Menu -> SettingValue.Text(
        if (options.any { it.value == default }) default else options.first().value
    )
}

/** 从存储读取当前值；未设置时回落到声明默认值。 */
/** 从存储读取当前值；未设置时回落到声明默认值。 */
fun SettingSpec.read(store: SettingsStore): SettingValue = when (this) {
    is SettingSpec.Switch -> SettingValue.Flag(
        if (isTextBacked) store.text(key, offText!!) == onText else store.flag(key, default)
    )
    is SettingSpec.Slider -> SettingValue.Number(coerce(store.number(key, coerce(default))))
    is SettingSpec.Choice -> SettingValue.Text(
        store.text(key, default).takeIf { saved -> options.any { it.value == saved } }
            ?: (defaultValue() as SettingValue.Text).value
    )
    is SettingSpec.Menu -> SettingValue.Text(
        store.text(key, default).takeIf { saved -> options.any { it.value == saved } }
            ?: (defaultValue() as SettingValue.Text).value
    )
}

/**
 * 读取存储原始值（按实际存储类型）：枚举型开关存 Text，普通开关存 Flag。
 * 渲染层快照用它，保证依赖该键的可见性/禁用条件能正确求值。
 */
fun SettingSpec.readRaw(store: SettingsStore): SettingValue = when (this) {
    is SettingSpec.Switch -> if (isTextBacked) {
        SettingValue.Text(store.text(key, offText!!))
    } else {
        SettingValue.Flag(store.flag(key, default))
    }
    is SettingSpec.Slider -> SettingValue.Number(coerce(store.number(key, coerce(default))))
    is SettingSpec.Choice -> SettingValue.Text(store.text(key, default))
    is SettingSpec.Menu -> SettingValue.Text(store.text(key, default))
}

/** 写入存储；滑杆值越界时收敛到声明范围。 */
fun SettingSpec.write(store: SettingsStore, value: SettingValue) {
    when (this) {
        is SettingSpec.Switch -> {
            val flag = (value as SettingValue.Flag).value
            if (isTextBacked) store.putText(key, if (flag) onText!! else offText!!)
            else store.putFlag(key, flag)
        }
        is SettingSpec.Slider -> store.putNumber(key, coerce((value as SettingValue.Number).value))
        is SettingSpec.Choice -> {
            val text = (value as SettingValue.Text).value
            check(options.any { it.value == text }) { "unknown choice '$text' for $key" }
            store.putText(key, text)
        }
        is SettingSpec.Menu -> {
            val text = (value as SettingValue.Text).value
            check(options.any { it.value == text }) { "unknown choice '$text' for $key" }
            store.putText(key, text)
        }
    }
}
