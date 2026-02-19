package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.FriendsRepository
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.ThemePreferences
import com.example.circleapp.data.UserProfile
import com.example.circleapp.data.UserRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class ProfileUiState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val photoUrl: String = "",
    val isAutoSaveEnabled: Boolean = false,
    val useSystemTheme: Boolean = true,
    val isDarkMode: Boolean = true,
    val autoAcceptInvites: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showEditNameDialog: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val isEmailVisible: Boolean = false,
    val isPhoneVisible: Boolean = false,
    val editedName: String = "",
    val blockedUsers: List<UserProfile> = emptyList()
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository()
    private val friendsRepository = FriendsRepository()
    private val savedPhotosStore = SavedPhotosStore(application)
    private val themePreferences = ThemePreferences(application)
    private val auth = FirebaseAuth.getInstance()
    private val storage = FirebaseStorage.getInstance()

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadCurrentUser()
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
            friendsRepository.listenToBlockedUsers().collectLatest { blockedUids ->
                friendsRepository.getUsers(blockedUids, { blocked ->
                    _uiState.update { it.copy(blockedUsers = blocked) }
                }, {})
            }
        }
    }

    private fun loadCurrentUser() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val user = repository.getCurrentUser()
                user?.let {
                    _uiState.update { state ->
                        state.copy(
                            name = it.displayName,
                            username = it.username,
                            email = it.email,
                            phone = it.phone,
                            photoUrl = it.photoUrl,
                            autoAcceptInvites = it.autoAcceptInvites,
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
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

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowEditNameDialog(show: Boolean) {
        _uiState.update { it.copy(showEditNameDialog = show, editedName = if (show) it.name else "") }
    }

    fun updateEditedName(newName: String) {
        _uiState.update { it.copy(editedName = newName) }
    }

    fun saveName() {
        val uid = auth.currentUser?.uid ?: return
        val newName = _uiState.value.editedName
        if (newName.isBlank()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                repository.updateDisplayName(uid, newName)
                _uiState.update { it.copy(name = newName, showEditNameDialog = false, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun toggleEmailVisibility() {
        _uiState.update { it.copy(isEmailVisible = !it.isEmailVisible) }
    }

    fun togglePhoneVisibility() {
        _uiState.update { it.copy(isPhoneVisible = !it.isPhoneVisible) }
    }

    fun uploadProfilePicture(uri: Uri) {
        val uid = auth.currentUser?.uid ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val ref = storage.reference.child("profile_pictures/$uid.jpg")
                ref.putFile(uri).await()
                val downloadUrl = ref.downloadUrl.await().toString()

                repository.updateProfilePicture(uid, downloadUrl)
                _uiState.update { it.copy(photoUrl = downloadUrl, isLoading = false) }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun unblockUser(uid: String) {
        friendsRepository.unblockUser(uid, {}, {})
    }
}