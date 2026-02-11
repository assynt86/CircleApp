package com.example.circleapp.ui.views

import android.app.Application
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.circleapp.ui.viewmodels.CircleUiState
import com.example.circleapp.ui.viewmodels.CircleViewModel
import com.example.circleapp.ui.viewmodels.CircleViewModelFactory

@Composable
fun CircleView(
    circleId: String,
    onBack: () -> Unit,
    onCameraClick: (String) -> Unit
) {
    val application = LocalContext.current.applicationContext as Application
    val factory = CircleViewModelFactory(application, circleId)
    val viewModel: CircleViewModel = viewModel(factory = factory)
    val uiState by viewModel.uiState.collectAsState()

    val multiplePhotoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickMultipleVisualMedia(20),
        onResult = { uris ->
            if (uris.isNotEmpty()) {
                viewModel.uploadPhotos(uris, listOf(circleId)) { }
            }
        }
    )

    CircleViewContent(
        uiState = uiState,
        onBack = onBack,
        onShowCamera = { onCameraClick(circleId) },
        onSetFullscreenImage = { viewModel.onSetFullscreenImage(it) },
        onUploadPhoto = {
            multiplePhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        },
        onToggleSelectionMode = { viewModel.toggleSelectionMode() },
        onTogglePhotoSelection = { viewModel.togglePhotoSelection(it) },
        onDownloadSelectedPhotos = { viewModel.downloadSelectedPhotos() },
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CircleViewContent(
    uiState: CircleUiState,
    onBack: () -> Unit,
    onShowCamera: () -> Unit,
    onSetFullscreenImage: (Int?) -> Unit,
    onUploadPhoto: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onTogglePhotoSelection: (String) -> Unit,
    onDownloadSelectedPhotos: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(uiState.circleInfo?.name ?: "Circle") },
                navigationIcon = { TextButton(onClick = onBack) { Text("Back") } },
                actions = {
                    if (uiState.inSelectionMode) {
                        IconButton(onClick = onDownloadSelectedPhotos) {
                            Icon(Icons.Filled.Download, contentDescription = "Download Selected Photos")
                        }
                        IconButton(onClick = onToggleSelectionMode) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancel Selection")
                        }
                    } else {
                        IconButton(onClick = onToggleSelectionMode) {
                            Icon(Icons.Filled.SelectAll, contentDescription = "Select Photos")
                        }
                        if (uiState.circleInfo?.status == "open") {
                            IconButton(onClick = onUploadPhoto) {
                                Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Upload Photo")
                            }
                            IconButton(onClick = onShowCamera) {
                                Icon(Icons.Filled.CameraAlt, contentDescription = "Take Photo")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            if (uiState.error != null) {
                Text("Error: ${uiState.error}", color = MaterialTheme.colorScheme.error)
                return@Column
            }

            if (uiState.circleInfo == null) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                    Text("Loading circle...")
                }
                return@Column
            }

            val info = uiState.circleInfo
            Text("Invite code: ${info.inviteCode}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(8.dp))

            val isClosed = info.status == "closed"
            Text(
                text = if (isClosed) "Status: CLOSED" else "Status: OPEN",
                style = MaterialTheme.typography.titleMedium
            )

            if (!isClosed) {
                Text("Closes in: ${uiState.remainingTime}")
            }

            Spacer(Modifier.height(16.dp))
            Text("Photos (${uiState.photos.size})", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))

            if (uiState.isUploading) {
                CircularProgressIndicator()
            }

            if (uiState.photos.isEmpty()) {
                Text("No photos yet.")
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) { itemsIndexed(uiState.photos) { index, p ->
                        val isSelected = uiState.selectedPhotos.contains(p.id)
                        Card(modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                if (uiState.inSelectionMode) {
                                    onTogglePhotoSelection(p.id)
                                } else {
                                    onSetFullscreenImage(index)
                                }
                            }
                            .border(
                                width = if (isSelected) 4.dp else 0.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                            )
                        ) {
                            Box(contentAlignment = Alignment.TopEnd) {
                                AsyncImage(
                                    model = p.downloadUrl,
                                    contentDescription = "Circle photo",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(1f)
                                        .clip(MaterialTheme.shapes.medium),
                                    contentScale = ContentScale.Crop
                                )
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Filled.CheckCircle,
                                        contentDescription = "Selected",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(8.dp)
                                    )
                                }
                                if (uiState.inProgressSaves.contains(p.id)) {
                                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.fullscreenImage != null) {
        Dialog(onDismissRequest = { onSetFullscreenImage(null) }) {
            val pagerState = rememberPagerState(initialPage = uiState.fullscreenImage) {
                uiState.photos.size
            }
            HorizontalPager(
                state = pagerState,
                modifier = Modifier.fillMaxSize()
            ) { page ->
                AsyncImage(
                    model = uiState.photos[page].downloadUrl,
                    contentDescription = "Full screen image",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }
}
