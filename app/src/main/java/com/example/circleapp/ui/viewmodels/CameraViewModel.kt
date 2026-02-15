package com.example.circleapp.ui.viewmodels

import android.app.Application
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.compose.ui.geometry.Offset
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CameraUiState(
    val hasPermission: Boolean = false,
    val isCapturing: Boolean = false,
    val flashMode: Int = ImageCapture.FLASH_MODE_OFF,
    val zoomLevel: Float = 0f,
    val showZoomBar: Boolean = false,
    val showGrid: Boolean = false,
    val lensFacing: Int = CameraSelector.LENS_FACING_BACK,
    val focusPoint: Offset? = null,
    val showFlashUIEffect: Boolean = false,
    val showCirclesPopup: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = isGranted) }
    }

    fun setFlashMode(mode: Int) {
        _uiState.update { it.copy(flashMode = mode) }
    }

    fun setZoomLevel(level: Float) {
        _uiState.update { it.copy(zoomLevel = level.coerceIn(0f, 1f), showZoomBar = true) }
    }

    fun hideZoomBar() {
        _uiState.update { it.copy(showZoomBar = false) }
    }

    fun toggleGrid() {
        _uiState.update { it.copy(showGrid = !it.showGrid) }
    }

    fun toggleLensFacing() {
        _uiState.update {
            it.copy(
                lensFacing = if (it.lensFacing == CameraSelector.LENS_FACING_BACK)
                    CameraSelector.LENS_FACING_FRONT else CameraSelector.LENS_FACING_BACK,
                zoomLevel = 0f,
                focusPoint = null
            )
        }
    }

    fun setFocusPoint(point: Offset?) {
        _uiState.update { it.copy(focusPoint = point) }
    }

    fun setCapturing(isCapturing: Boolean) {
        _uiState.update { it.copy(isCapturing = isCapturing) }
    }

    fun triggerFlashEffect() {
        _uiState.update { it.copy(showFlashUIEffect = true) }
    }

    fun hideFlashEffect() {
        _uiState.update { it.copy(showFlashUIEffect = false) }
    }

    fun setShowCirclesPopup(show: Boolean) {
        _uiState.update { it.copy(showCirclesPopup = show) }
    }
}
