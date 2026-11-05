package com.kingzcheung.xime.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.kingzcheung.xime.settings.SettingsPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class ThemeUiState(
    val darkMode: Int = 0,
    val lightThemeId: String = "gboard_gray",
    val darkThemeId: String = "gboard_black",
    /** 深浅色主题自搭配：开启后浅/深模式可分别选主题，关闭时应用主题整套切换。 */
    val mixMatch: Boolean = false,
)

class ThemeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(
        ThemeUiState(
            darkMode = SettingsPreferences.getDarkMode(context),
            lightThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = false),
            darkThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = true),
            mixMatch = SettingsPreferences.getThemeMixMatch(context),
        )
    )
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    fun setDarkMode(mode: Int) {
        SettingsPreferences.setDarkMode(context, mode)
        _uiState.update { it.copy(darkMode = mode) }
    }

    /** 只修改指定明暗模式的主题，另一套保持不变（深浅色自搭配开启时使用）。 */
    fun setColorTheme(themeId: String, isDark: Boolean) {
        SettingsPreferences.setKeyboardThemeForMode(context, isDark, themeId)
        _uiState.update {
            if (isDark) it.copy(darkThemeId = themeId) else it.copy(lightThemeId = themeId)
        }
    }

    /** 整套应用：同一主题的浅色与深色模式一起切换（默认行为）。 */
    fun setColorThemeBoth(themeId: String) {
        SettingsPreferences.setKeyboardThemeForMode(context, isDark = false, themeId = themeId)
        SettingsPreferences.setKeyboardThemeForMode(context, isDark = true, themeId = themeId)
        _uiState.update { it.copy(lightThemeId = themeId, darkThemeId = themeId) }
    }

    /** 深浅色主题自搭配开关。 */
    fun setMixMatch(enabled: Boolean) {
        SettingsPreferences.setThemeMixMatch(context, enabled)
        _uiState.update { it.copy(mixMatch = enabled) }
    }
}
