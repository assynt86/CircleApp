package com.crcleapp.crcle.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crcleapp.crcle.data.NotificationPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class NotificationSettingsUiState(
    val notificationsEnabled: Boolean = true,
    val friendRequestsEnabled: Boolean = true,
    val friendRequestAcceptedEnabled: Boolean = true,
    val circleInvitesEnabled: Boolean = true,
    val newPhotosEnabled: Boolean = true
)

class NotificationSettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val notificationPreferences = NotificationPreferencesStore(application)

    private val _uiState = MutableStateFlow(NotificationSettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            notificationPreferences.notificationsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(notificationsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferences.friendRequestsEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(friendRequestsEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferences.friendRequestAcceptedEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(friendRequestAcceptedEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferences.circleInvitesEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(circleInvitesEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            notificationPreferences.newPhotosEnabledFlow.collectLatest { enabled ->
                _uiState.update { it.copy(newPhotosEnabled = enabled) }
            }
        }
    }

    fun setNotificationsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setNotificationsEnabled(enabled)
        }
    }

    fun setFriendRequestsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setFriendRequestsEnabled(enabled)
        }
    }

    fun setFriendRequestAcceptedEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setFriendRequestAcceptedEnabled(enabled)
        }
    }

    fun setCircleInvitesEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setCircleInvitesEnabled(enabled)
        }
    }

    fun setNewPhotosEnabled(enabled: Boolean) {
        viewModelScope.launch {
            notificationPreferences.setNewPhotosEnabled(enabled)
        }
    }
}
