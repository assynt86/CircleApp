package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ProfileUiState(
    val name: String = "",
    val username: String = "",
    val email: String = "",
    val phone: String = "",
    val isAutoSaveEnabled: Boolean = false,
    val showSettingsDialog: Boolean = false,
    val showBugReportDialog: Boolean = false,
    val bugDescription: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

class ProfileViewModel(application: Application) : AndroidViewModel(application) {
    private val repository = UserRepository()
    private val savedPhotosStore = SavedPhotosStore(application)

    private val _uiState = MutableStateFlow(ProfileUiState())
    val uiState = _uiState.asStateFlow()

    init {
        loadCurrentUser()
        viewModelScope.launch {
            savedPhotosStore.autoSaveFlow.collectLatest { enabled ->
                _uiState.update { it.copy(isAutoSaveEnabled = enabled) }
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
                            isLoading = false
                        )
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        }
    }

    fun updateName(newName: String) {
        _uiState.update { it.copy(name = newName) }
    }

    fun toggleAutoSave(enabled: Boolean) {
        viewModelScope.launch {
            savedPhotosStore.setAutoSave(enabled)
        }
    }

    fun setShowSettingsDialog(show: Boolean) {
        _uiState.update { it.copy(showSettingsDialog = show) }
    }

    fun setShowBugReportDialog(show: Boolean) {
        _uiState.update { it.copy(showBugReportDialog = show, bugDescription = "") }
    }

    fun updateBugDescription(description: String) {
        _uiState.update { it.copy(bugDescription = description) }
    }

    fun submitBugReport() {
        val description = _uiState.value.bugDescription
        if (description.isBlank()) return

        viewModelScope.launch {
            try {
                repository.reportBug(description)
                setShowBugReportDialog(false)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = "Failed to report bug: ${e.message}") }
            }
        }
    }
}
