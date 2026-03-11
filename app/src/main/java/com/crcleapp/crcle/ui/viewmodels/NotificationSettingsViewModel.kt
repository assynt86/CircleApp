package com.crcleapp.crcle.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.crcleapp.crcle.data.NotificationPreferencesStore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
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
        combine(
            notificationPreferences.notificationsEnabledFlow,
            notificationPreferences.friendRequestsEnabledFlow,
            notificationPreferences.friendRequestAcceptedEnabledFlow,
            notificationPreferences.circleInvitesEnabledFlow,
            notificationPreferences.newPhotosEnabledFlow
        ) { enabled, fr, fra, ci, np ->
            NotificationSettingsUiState(
                notificationsEnabled = enabled,
                friendRequestsEnabled = fr,
                friendRequestAcceptedEnabled = fra,
                circleInvitesEnabled = ci,
                newPhotosEnabled = np
            )
        }.onEach { state ->
            _uiState.value = state
        }.launchIn(viewModelScope)
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
