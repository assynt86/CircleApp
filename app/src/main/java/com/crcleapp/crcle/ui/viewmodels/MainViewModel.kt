package com.crcleapp.crcle.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.crcleapp.crcle.data.CirclePreferencesStore
import com.crcleapp.crcle.data.ThemePreferences
import kotlinx.coroutines.flow.Flow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences(application)
    private val circlePreferences = CirclePreferencesStore(application)
    
    val useSystemTheme: Flow<Boolean> = themePreferences.useSystemThemeFlow
    val isDarkMode: Flow<Boolean> = themePreferences.isDarkModeFlow
    val launchOnCamera: Flow<Boolean> = circlePreferences.launchOnCameraFlow
}
