package com.example.circleapp.ui.viewmodels

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CircleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class HomeUiState(
    val circles: List<Circle> = emptyList(),
    val selectedCircleIds: List<String> = emptyList(),
    val isCreateCircleDialogVisible: Boolean = false,
    val isJoinCircleDialogVisible: Boolean = false,
    val newCircleName: String = "",
    val newCircleDurationDays: Float = 1f,
    val joinInviteCode: String = ""
)

class HomeViewModel : ViewModel() {

    private val repository = CircleRepository()

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        loadUserCircles()
    }

    private fun loadUserCircles() {
        viewModelScope.launch {
            repository.getUserCircles(
                onSuccess = { circleList ->
                    _uiState.update {
                        it.copy(
                            circles = circleList,
                            selectedCircleIds = circleList.filter { circle -> circle.status == "open" }.map { circle -> circle.id }
                        )
                    }
                },
                onError = {  }
            )
        }
    }

    fun createCircle(onSuccess: (String) -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            repository.createCircle(
                circleName = _uiState.value.newCircleName,
                durationDays = _uiState.value.newCircleDurationDays.toInt(),
                onSuccess = onSuccess,
                onError = onError
            )
            _uiState.update { it.copy(isCreateCircleDialogVisible = false, newCircleName = "", newCircleDurationDays = 1f) }
        }
    }

    fun joinCircle(onSuccess: (String) -> Unit, onNotFound: () -> Unit, onError: (Exception) -> Unit) {
        viewModelScope.launch {
            repository.joinCircleByInviteCode(
                inviteCode = _uiState.value.joinInviteCode,
                onSuccess = onSuccess,
                onNotFound = onNotFound,
                onError = onError
            )
            _uiState.update { it.copy(isJoinCircleDialogVisible = false, joinInviteCode = "") }
        }
    }

    fun uploadPhotoToCircles(uri: Uri, onPhotoUploaded: (String) -> Unit, onUploadFailed: (String, String) -> Unit) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedCircleIds
            if (selectedIds.isEmpty()) return@launch

            selectedIds.forEach { circleId ->
                repository.uploadPhotoToCircle(
                    circleId = circleId,
                    photoUri = uri,
                    onSuccess = { photoId -> onPhotoUploaded(circleId) },
                    onError = { e -> onUploadFailed(circleId, e.message ?: "Unknown error") }
                )
            }
        }
    }

    fun onCircleSelected(circleId: String, isSelected: Boolean) {
        _uiState.update { currentState ->
            val newSelectedIds = if (isSelected) {
                currentState.selectedCircleIds + circleId
            } else {
                currentState.selectedCircleIds - circleId
            }
            currentState.copy(selectedCircleIds = newSelectedIds)
        }
    }

    fun showCreateCircleDialog(show: Boolean) {
        _uiState.update { it.copy(isCreateCircleDialogVisible = show) }
    }

    fun showJoinCircleDialog(show: Boolean) {
        _uiState.update { it.copy(isJoinCircleDialogVisible = show, joinInviteCode = "") }
    }
    
    fun onNewCircleNameChange(name: String) {
        _uiState.update { it.copy(newCircleName = name) }
    }

    fun onNewCircleDurationChange(duration: Float) {
        _uiState.update { it.copy(newCircleDurationDays = duration) }
    }

    fun onInviteCodeChange(code: String) {
        if (code.length <= 6) {
            _uiState.update { it.copy(joinInviteCode = code.uppercase()) }
        }
    }
}