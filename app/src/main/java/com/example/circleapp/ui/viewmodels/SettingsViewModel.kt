package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.*
import com.google.firebase.auth.FirebaseAuth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class SettingsUiState(
    val isAutoSaveEnabled: Boolean = false,
    val useSystemTheme: Boolean = true,
    val isDarkMode: Boolean = true,
    val autoAcceptInvites: Boolean = false,
    val notificationsEnabled: Boolean = true,
    val blockedUsers: List<UserProfile> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null
)

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository()
    private val friendsRepository = FriendsRepository()
    private val savedPhotosStore = SavedPhotosStore(application)
    private val themePreferences = ThemePreferences(application)
    private val notificationPreferences = NotificationPreferencesStore(application)
    private val auth = FirebaseAuth.getInstance()

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadUserSettings()
        viewModelScope.launch {
            savedPhotosStore.autoSaveFlow.collectLatest { enabled ->
                _uiState.update { it.copy(isAutoSaveEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            themePreferences.useSystemThemeFlow.collectLatest { useSystem ->
                _uiState.update { it.copy(useSystemTheme = useSystem) }
            }
        }
        viewModelScope.launch {
            themePreferences.isDarkModeFlow.collectLatest { isDark ->
                _uiState.update { it.copy(isDarkMode = isDark) }
            }
        }
        viewModelScope.launch {
            notificationPreferences.notificationsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            friendsRepository.listenToBlockedUsers().collectLatest { blockedUids ->
                friendsRepository.getUsers(blockedUids, { blocked ->
                    _uiState.update { it.copy(blockedUsers = blocked) }
                }, {})
            }
        }
    }

    private fun loadUserSettings() {
        viewModelScope.launch {
            val user = repository.getCurrentUser()
            user?.let {
                _uiState.update { state ->
                    state.copy(
                        autoAcceptInvites = it.autoAcceptInvites
                    )
                }
            }
        }
    }

    fun toggleAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            savedPhotosStore.setAutoSave(enabled)
        }
    }

    fun setUseSystemTheme(useSystem: Boolean) {
        viewModelScope.launch {
            themePreferences.setUseSystemTheme(useSystem)
        }
    }

    fun setDarkMode(isDark: Boolean) {
        viewModelScope.launch {
            themePreferences.setDarkMode(isDark)
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setNotificationsEnabled(enabled)
        }
    }

    fun setAutoAcceptInvites(enabled: Boolean) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            try {
                repository.setAutoAcceptInvites(uid, enabled)
                _uiState.update { it.copy(autoAcceptInvites = enabled) }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message) }
            }
        }
    }

    fun unblockUser(uid: String) {
        friendsRepository.unblockUser(uid, {}, {})
    }
}
