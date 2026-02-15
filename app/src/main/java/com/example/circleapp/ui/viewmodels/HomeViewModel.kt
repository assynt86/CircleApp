package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CirclePreferencesStore
import com.example.circleapp.data.CircleRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class CameraNavigationState(
    val navigateToCamera: Boolean = false,
    val entryPointCircleId: String? = null
)

data class HomeUiState(
    val circles: List<Circle> = emptyList(),
    val selectedCircleIds: List<String> = emptyList(),
    val isCreateCircleDialogVisible: Boolean = false,
    val isJoinCircleDialogVisible: Boolean = false,
    val newCircleName: String = "",
    val newCircleDurationDays: Float = 1f,
    val joinInviteCode: String = "",
    val hasCameraPermission: Boolean = false,
    val isCapturing: Boolean = false,
    val cameraNavigationState: CameraNavigationState = CameraNavigationState(),
    val isLoading: Boolean = true
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val prefsStore = CirclePreferencesStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var initialSelectionDone = false
    private var initialLoadCompleted = false

    init {
        loadUserCircles()
    }

    private fun loadUserCircles() {
        viewModelScope.launch {
            val savedSelection = prefsStore.selectedCircleIdsFlow.first()
            
            repository.getUserCircles(
                onSuccess = { circleList ->
                    _uiState.update { currentState ->
                        val selection = if (!initialSelectionDone) {
                            initialSelectionDone = true
                            if (savedSelection.isNotEmpty()) {
                                val validIds = circleList.map { it.id }.toSet()
                                savedSelection.filter { it in validIds }
                            } else {
                                circleList.filter { it.status == "open" }.map { it.id }
                            }
                        } else {
                            currentState.selectedCircleIds
                        }
                        
                        currentState.copy(
                            circles = circleList,
                            selectedCircleIds = selection
                        )
                    }
                    
                    if (!initialLoadCompleted) {
                        fetchPreviewPhotos(circleList, onComplete = {
                            initialLoadCompleted = true
                            _uiState.update { it.copy(isLoading = false) }
                        })
                    } else {
                        // For subsequent updates, just fetch photos without blocking the UI
                        fetchPreviewPhotos(circleList, onComplete = {})
                    }
                },
                onError = { 
                    _uiState.update { it.copy(isLoading = false) }
                }
            )
        }
    }

    private fun fetchPreviewPhotos(circles: List<Circle>, onComplete: () -> Unit) {
        if (circles.isEmpty()) {
            onComplete()
            return
        }
        
        var completedCount = 0
        circles.forEach { circle ->
            repository.getFirstPhoto(circle.id, 
                onSuccess = { photo ->
                    if (photo != null) {
                        repository.getDownloadUrl(photo.storagePath) { url ->
                            if (url != null) {
                                _uiState.update { state ->
                                    val updatedCircles = state.circles.map { c ->
                                        if (c.id == circle.id) c.copy().apply { previewUrl = url } else c
                                    }
                                    state.copy(circles = updatedCircles)
                                }
                            }
                            completedCount++
                            if (completedCount == circles.size) onComplete()
                        }
                    } else {
                        completedCount++
                        if (completedCount == circles.size) onComplete()
                    }
                },
                onError = { 
                    completedCount++
                    if (completedCount == circles.size) onComplete()
                }
            )
        }
    }

    fun onCameraPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasCameraPermission = isGranted) }
    }

    fun navigateToCameraWithCircle(circleId: String) {
        _uiState.update { it.copy(cameraNavigationState = CameraNavigationState(navigateToCamera = true, entryPointCircleId = circleId)) }
    }

    fun onCameraNavigationHandled() {
        _uiState.update { it.copy(cameraNavigationState = CameraNavigationState()) }
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

    fun setCapturing(isCapturing: Boolean) {
        _uiState.update { it.copy(isCapturing = isCapturing) }
    }

    fun uploadPhotoToCircles(
        uri: Uri,
        onUploadsComplete: () -> Unit,
        onUploadFailed: (String, String) -> Unit
    ) {
        viewModelScope.launch {
            val selectedIds = _uiState.value.selectedCircleIds
            if (selectedIds.isEmpty()) {
                onUploadsComplete()
                return@launch
            }

            val totalCircles = selectedIds.size
            var finishedCount = 0

            fun checkCompletion() {
                finishedCount++
                if (finishedCount == totalCircles) {
                    onUploadsComplete()
                }
            }

            selectedIds.forEach { circleId ->
                repository.uploadPhotoToCircle(
                    circleId = circleId,
                    photoUri = uri,
                    onSuccess = {
                        checkCompletion()
                    },
                    onError = { e ->
                        onUploadFailed(circleId, e.message ?: "Unknown error")
                        checkCompletion()
                    }
                )
            }
        }
    }

    fun onCircleSelected(circleId: String, isSelected: Boolean) {
        _uiState.update { currentState ->
            val newSelectedIds = if (isSelected) {
                (currentState.selectedCircleIds + circleId).distinct()
            } else {
                currentState.selectedCircleIds - circleId
            }
            
            viewModelScope.launch {
                prefsStore.saveSelectedCircleIds(newSelectedIds.toSet())
            }
            
            currentState.copy(selectedCircleIds = newSelectedIds)
        }
    }

    fun handleCameraEntry(entryPointCircleId: String?) {
        if (entryPointCircleId != null) {
            initialSelectionDone = true
            _uiState.update {
                it.copy(selectedCircleIds = listOf(entryPointCircleId))
            }
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
