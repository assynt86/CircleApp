package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.content.ContentValues
import android.net.Uri
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CameraUiState(
    val hasPermission: Boolean = false,
    val isCapturing: Boolean = false
)

class CameraViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CameraUiState())
    val uiState = _uiState.asStateFlow()

    fun onPermissionResult(isGranted: Boolean) {
        _uiState.update { it.copy(hasPermission = isGranted) }
    }

    fun takePicture(
        imageCapture: ImageCapture?,
        onPhotoSaved: (Uri) -> Unit
    ) {
        if (_uiState.value.isCapturing) return
        val capture = imageCapture ?: return

        _uiState.update { it.copy(isCapturing = true) }

        val context = getApplication<Application>().applicationContext
        val name = "Circle_${System.currentTimeMillis()}"
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Circle")
        }

        val outputOptions = ImageCapture.OutputFileOptions.Builder(
            context.contentResolver,
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            contentValues
        ).build()

        capture.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    output.savedUri?.let { uri ->
                        onPhotoSaved(uri)
                    }
                    _uiState.update { it.copy(isCapturing = false) }
                }

                override fun onError(exception: ImageCaptureException) {
                    exception.printStackTrace()
                    _uiState.update { it.copy(isCapturing = false) }
                }
            }
        )
    }
}
