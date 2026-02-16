package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import com.example.circleapp.data.ThemePreferences
import kotlinx.coroutines.flow.Flow

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val themePreferences = ThemePreferences(application)
    
    val useSystemTheme: Flow<Boolean> = themePreferences.useSystemThemeFlow
    val isDarkMode: Flow<Boolean> = themePreferences.isDarkModeFlow
}
