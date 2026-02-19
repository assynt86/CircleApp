package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CirclePreferencesStore
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.data.UserRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
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
    val isLoading: Boolean = true,
    val errorMessage: String? = null,
    val previewCircle: Circle? = null,
    val showJoinPreview: Boolean = false,
    val autoAcceptInvites: Boolean = false,
    val pendingInvitesCount: Int = 0
)

class HomeViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val userRepository = UserRepository()
    private val prefsStore = CirclePreferencesStore(application)

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    private var initialSelectionDone = false
    private var initialLoadCompleted = false
    private var pendingEntryPointCircleId: String? = null
    private var lastHandledEntryPointId: String? = "" // "" means nothing handled yet. null is a valid entry point.

    init {
        loadUserCircles()
        loadUserSettings()
        listenToInvites()
    }

    private fun loadUserSettings() {
        viewModelScope.launch {
            val user = userRepository.getCurrentUser()
            _uiState.update { it.copy(autoAcceptInvites = user?.autoAcceptInvites ?: false) }
        }
    }

    private fun listenToInvites() {
        viewModelScope.launch {
            repository.listenToCircleInvites().collectLatest { invites ->
                _uiState.update { it.copy(pendingInvitesCount = invites.size) }
            }
        }
    }

    private fun loadUserCircles() {
        viewModelScope.launch {
            val savedSelection = prefsStore.selectedCircleIdsFlow.first()
            
            repository.getUserCircles(
                onSuccess = { circleList ->
                    _uiState.update { currentState ->
                        val selection = if (pendingEntryPointCircleId != null) {
                            val id = pendingEntryPointCircleId!!
                            pendingEntryPointCircleId = null // Consume it!
                            initialSelectionDone = true
                            listOf(id)
                        } else if (!initialSelectionDone) {
                            initialSelectionDone = true
                            if (savedSelection.isNotEmpty()) {
                                // Filter saved selection to only include valid and OPEN circles
                                val openCircleIds = circleList.filter { !it.isClosed }.map { it.id }.toSet()
                                savedSelection.filter { it in openCircleIds }
                            } else {
                                // Default to all open circles
                                circleList.filter { !it.isClosed }.map { it.id }
                            }
                        } else {
                            // If initial selection is already done, preserve current in-memory selection
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
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error loading circles") }
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
            // Prioritize custom backgroundUrl if it exists
            if (!circle.backgroundUrl.isNullOrBlank()) {
                _uiState.update { state ->
                    val updatedCircles = state.circles.map { c ->
                        if (c.id == circle.id) c.copy().apply { previewUrl = circle.backgroundUrl } else c
                    }
                    state.copy(circles = updatedCircles)
                }
                completedCount++
                if (completedCount == circles.size) onComplete()
            } else {
                // Fallback to the first photo in the circle
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

    fun createCircle(onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            repository.createCircle(
                circleName = _uiState.value.newCircleName,
                durationDays = _uiState.value.newCircleDurationDays.toInt(),
                onSuccess = onSuccess,
                onError = { e ->
                    _uiState.update { it.copy(errorMessage = "Error creating circle: ${e.message}") }
                }
            )
            _uiState.update { it.copy(isCreateCircleDialogVisible = false, newCircleName = "", newCircleDurationDays = 1f) }
        }
    }

    fun onJoinClick(onSuccess: (String) -> Unit) {
        val currentInviteCode = _uiState.value.joinInviteCode
        if (currentInviteCode.length < 6) return

        _uiState.update { it.copy(isLoading = true) }
        viewModelScope.launch {
            repository.getCircleByInviteCode(
                inviteCode = currentInviteCode,
                onSuccess = { circle ->
                    _uiState.update { it.copy(isLoading = false, isJoinCircleDialogVisible = false) }
                    if (_uiState.value.autoAcceptInvites) {
                        confirmJoinCircle(circle.id, onSuccess)
                    } else {
                        _uiState.update { it.copy(previewCircle = circle, showJoinPreview = true) }
                    }
                },
                onNotFound = {
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Circle not found: $currentInviteCode") }
                },
                onError = { e ->
                    _uiState.update { it.copy(isLoading = false, errorMessage = "Error: ${e.message}") }
                }
            )
        }
    }

    fun confirmJoinCircle(circleId: String, onSuccess: (String) -> Unit) {
        viewModelScope.launch {
            repository.addMemberByUid(
                circleId = circleId,
                uid = repository.getCurrentUserUid() ?: return@launch,
                onSuccess = {
                    _uiState.update { it.copy(showJoinPreview = false, previewCircle = null, joinInviteCode = "") }
                    onSuccess(circleId)
                },
                onError = { e ->
                    _uiState.update { it.copy(errorMessage = "Error joining: ${e.message}") }
                }
            )
        }
    }

    fun cancelJoin() {
        _uiState.update { it.copy(showJoinPreview = false, previewCircle = null, joinInviteCode = "") }
    }

    fun clearErrorMessage() {
        _uiState.update { it.copy(errorMessage = null) }
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
                // Final safety check: ensure the circle is still open
                val circle = _uiState.value.circles.find { it.id == circleId }
                if (circle != null && !circle.isClosed) {
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
                } else {
                    onUploadFailed(circleId, "Circle is closed")
                    checkCompletion()
                }
            }
        }
    }

    fun onCircleSelected(circleId: String, isSelected: Boolean) {
        val circle = _uiState.value.circles.find { it.id == circleId }
        if (isSelected && circle?.isClosed == true) return // Cannot select closed circle

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
        // Only apply the selection override if it's a DIFFERENT entry point than the one we last handled
        // This prevents swiping back and forth from resetting manual selections.
        if (entryPointCircleId != lastHandledEntryPointId) {
            lastHandledEntryPointId = entryPointCircleId
            
            if (entryPointCircleId != null) {
                // If we enter with a specific circle, we set it as pending
                // so loadUserCircles (or current state) can use it once.
                pendingEntryPointCircleId = entryPointCircleId
                _uiState.update {
                    it.copy(selectedCircleIds = listOf(entryPointCircleId))
                }
            } else {
                // If entering from general navigation (null), we reset any pending state
                // but we DO NOT reset selectedCircleIds, allowing previous selection to persist.
                pendingEntryPointCircleId = null
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
