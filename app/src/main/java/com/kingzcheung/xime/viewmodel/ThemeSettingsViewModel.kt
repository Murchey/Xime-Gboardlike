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
)

class ThemeSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val context = application.applicationContext

    private val _uiState = MutableStateFlow(
        ThemeUiState(
            darkMode = SettingsPreferences.getDarkMode(context),
            lightThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = false),
            darkThemeId = SettingsPreferences.getKeyboardThemeForMode(context, isDark = true),
        )
    )
    val uiState: StateFlow<ThemeUiState> = _uiState.asStateFlow()

    fun setDarkMode(mode: Int) {
        SettingsPreferences.setDarkMode(context, mode)
        _uiState.update { it.copy(darkMode = mode) }
    }

    /** 只修改指定明暗模式的主题，另一套保持不变。 */
    fun setColorTheme(themeId: String, isDark: Boolean) {
        SettingsPreferences.setKeyboardThemeForMode(context, isDark, themeId)
        _uiState.update {
            if (isDark) it.copy(darkThemeId = themeId) else it.copy(lightThemeId = themeId)
        }
    }
}
