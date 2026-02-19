package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.CircleInfo
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.data.UserProfile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CircleSettingsUiState(
    val circleInfo: CircleInfo? = null,
    val members: List<UserProfile> = emptyList(),
    val isAdmin: Boolean = false,
    val isLoading: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val showDeleteConfirmation: Boolean = false
)

class CircleSettingsViewModel(
    application: Application,
    private val circleId: String
) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val _uiState = MutableStateFlow(CircleSettingsUiState())
    val uiState: StateFlow<CircleSettingsUiState> = _uiState.asStateFlow()

    init {
        loadCircleData()
    }

    private fun loadCircleData() {
        _uiState.update { it.copy(isLoading = true) }
        
        repository.listenToCircle(circleId,
            onSuccess = { info ->
                val currentUserUid = repository.getCurrentUserUid()
                _uiState.update { it.copy(
                    circleInfo = info,
                    isAdmin = info.ownerUid == currentUserUid
                ) }
                loadMembers()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    private fun loadMembers() {
        // We need the list of members from the circle document. 
        // listenToCircle currently doesn't return the members list.
        // Let's get the full circle document once to get member UIDs.
        // Actually, let's update repository or use another way.
        // For now, I'll assume repository.getCircleMembers needs memberUids.
        
        // I will use a simple get to fetch circle members
        com.google.firebase.firestore.FirebaseFirestore.getInstance()
            .collection("circles").document(circleId)
            .get()
            .addOnSuccessListener { doc ->
                val memberUids = doc.get("members") as? List<String> ?: emptyList()
                repository.getCircleMembers(memberUids,
                    onSuccess = { members ->
                        _uiState.update { it.copy(isLoading = false, members = members) }
                    },
                    onError = { e ->
                        _uiState.update { it.copy(isLoading = false, error = e.message) }
                    }
                )
            }
            .addOnFailureListener { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
    }

    fun updateCircleName(newName: String) {
        if (newName.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }
        repository.updateCircleName(circleId, newName,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, successMessage = "Name updated") }
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun updateBackground(uri: Uri) {
        _uiState.update { it.copy(isLoading = true) }
        repository.updateCircleBackground(circleId, uri,
            onSuccess = { url ->
                _uiState.update { it.copy(isLoading = false, successMessage = "Background updated") }
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun addMember(username: String) {
        if (username.isBlank()) return
        _uiState.update { it.copy(isLoading = true) }
        repository.addMemberByUsername(circleId, username,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, successMessage = "Member added") }
                loadMembers()
            },
            onNotFound = {
                _uiState.update { it.copy(isLoading = false, error = "User not found") }
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun kickMember(memberUid: String) {
        _uiState.update { it.copy(isLoading = true) }
        repository.kickMember(circleId, memberUid,
            onSuccess = {
                _uiState.update { it.copy(isLoading = false, successMessage = "Member removed") }
                loadMembers()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun deleteCircle(onDeleted: () -> Unit) {
        _uiState.update { it.copy(isLoading = true) }
        repository.deleteCircle(circleId,
            onSuccess = {
                onDeleted()
            },
            onError = { e ->
                _uiState.update { it.copy(isLoading = false, error = e.message) }
            }
        )
    }

    fun setShowDeleteConfirmation(show: Boolean) {
        _uiState.update { it.copy(showDeleteConfirmation = show) }
    }

    fun clearMessages() {
        _uiState.update { it.copy(error = null, successMessage = null) }
    }
}

class CircleSettingsViewModelFactory(
    private val application: Application,
    private val circleId: String
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return CircleSettingsViewModel(application, circleId) as T
    }
}
