package com.example.circleapp.ui.viewmodels

import android.app.Application
import android.net.Uri
import android.text.format.DateUtils
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.circleapp.data.Circle
import com.example.circleapp.data.CircleInfo
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.data.FriendsRepository
import com.example.circleapp.data.PhotoItem
import com.example.circleapp.data.SavedPhotosStore
import com.example.circleapp.data.saveJpegToGallery
import com.google.firebase.storage.FirebaseStorage
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

data class CircleUiState(
    val circleInfo: CircleInfo? = null,
    val photos: List<PhotoItem> = emptyList(),
    val allPhotos: List<PhotoItem> = emptyList(), // Store unfiltered photos
    val blockedUserUids: List<String> = emptyList(),
    val userCircles: List<Circle> = emptyList(),
    val error: String? = null,
    val isUploading: Boolean = false,
    val remainingTime: String = "",
    val deleteInTime: String = "",
    val showCamera: Boolean = false,
    val fullscreenImage: Int? = null,
    val inProgressSaves: List<String> = emptyList(),
    val inSelectionMode: Boolean = false,
    val selectedPhotos: Set<String> = emptySet(),
    val currentUserUid: String? = null,
    val deletingPhotos: Set<String> = emptySet(),
    val showInviteDialog: Boolean = false
)

class CircleViewModel(application: Application, private val circleId: String) : AndroidViewModel(application) {

    private val repository = CircleRepository()
    private val friendsRepository = FriendsRepository()
    private val savedPhotosStore = SavedPhotosStore(application)

    private val _uiState = MutableStateFlow(CircleUiState())
    val uiState: StateFlow<CircleUiState> = _uiState.asStateFlow()

    init {
        _uiState.update { it.copy(currentUserUid = repository.getCurrentUserUid()) }
        listenToCircle()
        listenToPhotos()
        listenToBlockedUsers()
        loadUserCircles()
    }

    private fun listenToCircle() {
        repository.listenToCircle(circleId,
            onSuccess = { circle ->
                _uiState.update { it.copy(circleInfo = circle) }
                startTimeUpdaters()
            },
            onError = { e -> _uiState.update { it.copy(error = e.message) } }
        )
    }

    private fun listenToPhotos() {
        repository.listenToPhotos(circleId,
            onSuccess = { photoList ->
                _uiState.update { it.copy(allPhotos = photoList) }
                filterPhotos()
                fetchDownloadUrls(photoList)
                autoSaveNewPhotos(photoList)
            },
            onError = { e -> _uiState.update { it.copy(error = e.message) } }
        )
    }

    private fun listenToBlockedUsers() {
        viewModelScope.launch {
            friendsRepository.listenToBlockedUsers().collectLatest { blockedUids ->
                _uiState.update { it.copy(blockedUserUids = blockedUids) }
                filterPhotos()
            }
        }
    }

    private fun filterPhotos() {
        val blocked = _uiState.value.blockedUserUids
        val filtered = _uiState.value.allPhotos.filter { it.uploaderUid !in blocked }
        _uiState.update { it.copy(photos = filtered) }
    }

    private fun fetchDownloadUrls(photos: List<PhotoItem>) {
        viewModelScope.launch {
            photos.forEach { item ->
                if (item.downloadUrl == null) {
                    repository.getDownloadUrl(item.storagePath) { url ->
                        if (url != null) {
                            _uiState.update { currentState ->
                                val updatedAllPhotos = currentState.allPhotos.map {
                                    if (it.id == item.id) it.copy(downloadUrl = url) else it
                                }
                                val updatedPhotos = currentState.photos.map {
                                    if (it.id == item.id) it.copy(downloadUrl = url) else it
                                }
                                currentState.copy(allPhotos = updatedAllPhotos, photos = updatedPhotos)
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

    private fun startTimeUpdaters() {
        viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                val circleInfo = _uiState.value.circleInfo ?: break

                val closeAtMillis = circleInfo.closeAt?.toDate()?.time
                val deleteAtMillis = circleInfo.deleteAt?.toDate()?.time

                if (closeAtMillis != null) {
                    val remainingClose = closeAtMillis - now
                    if (remainingClose > 0) {
                        val formattedTime = DateUtils.formatElapsedTime(remainingClose / 1000)
                        _uiState.update { it.copy(remainingTime = formattedTime) }
                    } else {
                        _uiState.update { it.copy(remainingTime = "") }
                    }
                }

                if (deleteAtMillis != null) {
                    val remainingDelete = deleteAtMillis - now
                    if (remainingDelete > 0) {
                        val formattedTime = DateUtils.formatElapsedTime(remainingDelete / 1000)
                        _uiState.update { it.copy(deleteInTime = formattedTime) }
                    } else {
                        _uiState.update { it.copy(deleteInTime = "") }
                    }
                }

                delay(1000)
            }
        }
    }

    private fun autoSaveNewPhotos(photos: List<PhotoItem>) {
        viewModelScope.launch {
            if (!savedPhotosStore.autoSaveFlow.first()) return@launch

            photos.forEach { photo ->
                if (photo.storagePath.isBlank() || _uiState.value.inProgressSaves.contains(photo.id)) return@forEach
                if (savedPhotosStore.isSaved(circleId, photo.id)) return@forEach

                _uiState.update { it.copy(inProgressSaves = it.inProgressSaves + photo.id) }

                try {
                    val bytes = FirebaseStorage.getInstance().reference.child(photo.storagePath).getBytes(10L * 1024 * 1024).await()
                    val savedUri = saveJpegToGallery(getApplication(), bytes, "Circle_${circleId}_${photo.id}", "Pictures/Circle")
                    if (savedUri != null) {
                        savedPhotosStore.markSaved(circleId, photo.id)
                    }
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    _uiState.update { it.copy(inProgressSaves = it.inProgressSaves - photo.id) }
                }
            }
        }
    }

    fun uploadPhotos(uris: List<Uri>, circleIds: List<String>, onComplete: (Boolean) -> Unit) {
        _uiState.update { it.copy(isUploading = true) }

        val totalPhotos = uris.size
        if (totalPhotos == 0) {
            _uiState.update { it.copy(isUploading = false) }
            onComplete(true)
            return
        }

        var completedUploads = 0
        var successfulUploads = 0
        val lock = Any()

        uris.forEach { uri ->
            repository.addPhotoToCircles(uri, circleIds) { isSuccess ->
                synchronized(lock) {
                    completedUploads++
                    if (isSuccess) {
                        successfulUploads++
                    }
                    if (completedUploads == totalPhotos) {
                        _uiState.update { it.copy(isUploading = false, showCamera = false) }
                        onComplete(successfulUploads == totalPhotos)
                    }
                }
            }
        }
    }

    fun onShowCamera(show: Boolean) {
        _uiState.update { it.copy(showCamera = show) }
    }

    fun onSetFullscreenImage(index: Int?) {
        if (!_uiState.value.inSelectionMode) {
            _uiState.update { it.copy(fullscreenImage = index) }
        }
    }

    fun toggleSelectionMode() {
        _uiState.update {
            val inSelectionMode = !it.inSelectionMode
            if (!inSelectionMode) {
                it.copy(inSelectionMode = false, selectedPhotos = emptySet())
            } else {
                it.copy(inSelectionMode = true)
            }
        }
    }

    fun togglePhotoSelection(photoId: String) {
        _uiState.update {
            val selectedPhotos = it.selectedPhotos.toMutableSet()
            if (selectedPhotos.contains(photoId)) {
                selectedPhotos.remove(photoId)
            } else {
                selectedPhotos.add(photoId)
            }
            
            // If we are in selection mode and the last photo was unselected, exit selection mode.
            val inSelectionMode = if (it.inSelectionMode && selectedPhotos.isEmpty()) false else it.inSelectionMode
            
            it.copy(selectedPhotos = selectedPhotos, inSelectionMode = inSelectionMode)
        }
    }

    fun downloadSelectedPhotos() {
        viewModelScope.launch {
            val photosToDownload = _uiState.value.photos.filter { it.id in _uiState.value.selectedPhotos }
            photosToDownload.forEach { photo ->
                downloadPhotoById(photo.id)
            }
            toggleSelectionMode()
        }
    }

    fun savePhoto(photoId: String) {
        viewModelScope.launch {
            downloadPhotoById(photoId)
        }
    }

    private suspend fun downloadPhotoById(photoId: String) {
        val photo = _uiState.value.photos.find { it.id == photoId } ?: return
        if (photo.storagePath.isBlank() || _uiState.value.inProgressSaves.contains(photo.id)) return

        _uiState.update { it.copy(inProgressSaves = it.inProgressSaves + photo.id) }

        try {
            val bytes = FirebaseStorage.getInstance().reference.child(photo.storagePath).getBytes(10L * 1024 * 1024).await()
            saveJpegToGallery(getApplication(), bytes, "Circle_${circleId}_${photo.id}", "Pictures/Circle")
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            _uiState.update { it.copy(inProgressSaves = it.inProgressSaves - photo.id) }
        }
    }

    fun deletePhoto(photoId: String) {
        val photo = _uiState.value.photos.find { it.id == photoId } ?: return
        _uiState.update { it.copy(deletingPhotos = it.deletingPhotos + photoId) }
        repository.deletePhoto(circleId, photo.id, photo.storagePath) { success ->
            _uiState.update { it.copy(deletingPhotos = it.deletingPhotos - photoId) }
            if (success && _uiState.value.fullscreenImage != null) {
                _uiState.update { it.copy(fullscreenImage = null) }
            }
        }
    }

    fun deleteSelectedPhotos() {
        val selected = _uiState.value.selectedPhotos.toList()
        selected.forEach { photoId ->
            deletePhoto(photoId)
        }
        toggleSelectionMode()
    }

    fun setShowInviteDialog(show: Boolean) {
        _uiState.update { it.copy(showInviteDialog = show) }
    }
}
