package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CircleInfo
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.data.PhotoItem
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.saveJpegToGallery
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CircleUiState(
    val circleInfo: CircleInfo? = null,
    val photos: List<PhotoItem> = emptyList(),
    val userCircles: List<Circle> = emptyList(),
    val error: String? = null,
    val isUploading: Boolean = false,
    val remainingTime: String = "",
    val showCamera: Boolean = false,
    val fullscreenImage: Int? = null,
    val inProgressSaves: List<String> = emptyList()
)

class CircleViewModel(application: Application, private val circleId: String) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val savedPhotosStore = SavedPhotosStore(application)

    private val _uiState = MutableStateFlow(CircleUiState())
    val uiState: StateFlow<CircleUiState> = _uiState.asStateFlow()

    init {
        listenToCircle()
        listenToPhotos()
        loadUserCircles()
    }

    private fun listenToCircle() {
        repository.listenToCircle(circleId,
            onSuccess = { circle ->
                _uiState.update { it.copy(circleInfo = circle) }
                startRemainingTimeUpdater()
            },
            onError = { e -> _uiState.update { it.copy(error = e.message) } }
        )
    }

    private fun listenToPhotos() {
        repository.listenToPhotos(circleId,
            onSuccess = { photoList ->
                _uiState.update { it.copy(photos = photoList) }
                fetchDownloadUrls(photoList)
                autoSaveNewPhotos(photoList)
            },
            onError = { e -> _uiState.update { it.copy(error = e.message) } }
        )
    }

    private fun fetchDownloadUrls(photos: List<PhotoItem>) {
        viewModelScope.launch {
            photos.forEach { item ->
                if (item.downloadUrl == null) {
                    repository.getDownloadUrl(item.storagePath) { url ->
                        if (url != null) {
                            _uiState.update { currentState ->
                                val updatedPhotos = currentState.photos.map {
                                    if (it.id == item.id) it.copy(downloadUrl = url) else it
                                }
                                currentState.copy(photos = updatedPhotos)
                            }
                        }
                    }
                }
            }
        }
    }

    private fun loadUserCircles() {
        repository.getUserCircles(
            onSuccess = { circles -> _uiState.update { it.copy(userCircles = circles) } },
            onError = { /* Handle error */ }
        )
    }

    private fun startRemainingTimeUpdater() {
        viewModelScope.launch {
            _uiState.value.circleInfo?.closeAt?.toDate()?.time?.let { closeAtMillis ->
                while (System.currentTimeMillis() < closeAtMillis) {
                    val remaining = closeAtMillis - System.currentTimeMillis()
                    if (remaining > 0) {
                        val formattedTime = DateUtils.formatElapsedTime(remaining / 1000)
                        _uiState.update { it.copy(remainingTime = formattedTime) }
                    } else {
                        _uiState.update { it.copy(remainingTime = "") }
                        break
                    }
                    delay(1000)
                }
                _uiState.update { it.copy(circleInfo = it.circleInfo?.copy()) } // Refresh state
            }
        }
    }

    private fun autoSaveNewPhotos(photos: List<PhotoItem>) {
        viewModelScope.launch {
            photos.forEach { photo ->
                if (photo.storagePath.isBlank() || _uiState.value.inProgressSaves.contains(photo.id)) return@forEach
                if (savedPhotosStore.isSaved(circleId, photo.id)) return@forEach

                _uiState.update { it.copy(inProgressSaves = it.inProgressSaves + photo.id) }

                try {
                    val bytes = FirebaseStorage.getInstance().reference.child(photo.storagePath).getBytes(10L * 1024 * 1024).await()
                    saveJpegToGallery(getApplication(), bytes, "Circle_${circleId}_${photo.id}", "Pictures/Circle")?.let {
                        savedPhotosStore.markSaved(circleId, photo.id)
                        // Optionally notify UI of successful save
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _uiState.update { it.copy(inProgressSaves = it.inProgressSaves - photo.id) }
                }
            }
        }
    }

    fun uploadPhotos(uri: Uri, circleIds: List<String>, onComplete: (Boolean) -> Unit) {
        _uiState.update { it.copy(isUploading = true) }
        repository.addPhotoToCircles(uri, circleIds) { isSuccess ->
            _uiState.update { it.copy(isUploading = false, showCamera = false) }
            onComplete(isSuccess)
        }
    }

    fun onShowCamera(show: Boolean) {
        _uiState.update { it.copy(showCamera = show) }
    }

    fun onSetFullscreenImage(index: Int?) {
        _uiState.update { it.copy(fullscreenImage = index) }
    }
}