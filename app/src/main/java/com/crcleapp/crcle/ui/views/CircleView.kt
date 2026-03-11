package com.crcleapp.crcle.ui.views

import android.app.Application
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.crcleapp.crcle.ui.components.ZoomableImage
import com.crcleapp.crcle.ui.viewmodels.CircleUiState
import com.crcleapp.crcle.ui.viewmodels.CircleViewModel
import com.crcleapp.crcle.ui.viewmodels.CircleViewModelFactory
import com.crcleapp.crcle.ui.theme.LeagueSpartan

@Composable
fun VideoPlayer(
    videoUrl: String?,
    isCurrentPage: Boolean,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            playWhenReady = false
        }
    }

    LaunchedEffect(videoUrl) {
        if (videoUrl != null) {
            exoPlayer.setMediaItem(MediaItem.fromUri(videoUrl))
            exoPlayer.prepare()
        }
    }

    LaunchedEffect(isCurrentPage) {
        if (isCurrentPage) {
            exoPlayer.playWhenReady = true
        } else {
            exoPlayer.pause()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    AndroidView(
        factory = {
            PlayerView(context).apply {
                player = exoPlayer
                useController = true
                setShowNextButton(false)
                setShowPreviousButton(false)
            }
        },
        modifier = modifier
    )
}

@Composable
fun CircleView(
    circleId: String,
    onBack: () -> Unit,
    onCameraClick: (String) -> Unit,
    onSettingsClick: (String) -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val factory = remember(circleId) { CircleViewModelFactory(application, circleId) }
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

    LaunchedEffect(uiState.error) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_LONG).show()
            viewModel.clearError()
        }
    }

    CircleViewContent(
        uiState = uiState,
        onBack = onBack,
        onShowCamera = { onCameraClick(circleId) },
        onSettingsClick = { onSettingsClick(circleId) },
        onSetFullscreenImage = { viewModel.onSetFullscreenImage(it) },
        onUploadPhoto = {
            multiplePhotoPickerLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageAndVideo)
            )
        },
        onToggleSelectionMode = { viewModel.toggleSelectionMode() },
        onTogglePhotoSelection = { viewModel.togglePhotoSelection(it) },
        onDownloadSelectedPhotos = { viewModel.downloadSelectedPhotos() },
        onSavePhoto = { viewModel.savePhoto(it) },
        onDeletePhoto = { viewModel.deletePhoto(it) },
        onDeleteSelectedPhotos = { viewModel.deleteSelectedPhotos() },
        onShowInviteDialog = { viewModel.setShowInviteDialog(it) }
    )
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun CircleViewContent(
    uiState: CircleUiState,
    onBack: () -> Unit,
    onShowCamera: () -> Unit,
    onSettingsClick: () -> Unit,
    onSetFullscreenImage: (Int?) -> Unit,
    onUploadPhoto: () -> Unit,
    onToggleSelectionMode: () -> Unit,
    onTogglePhotoSelection: (String) -> Unit,
    onDownloadSelectedPhotos: () -> Unit,
    onSavePhoto: (String) -> Unit,
    onDeletePhoto: (String) -> Unit,
    onDeleteSelectedPhotos: () -> Unit,
    onShowInviteDialog: (Boolean) -> Unit
) {
    val isClosed = uiState.circleInfo?.isClosed == true
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = uiState.circleInfo?.name ?: "Circle",
                        modifier = Modifier.clickable { onSettingsClick() },
                        fontFamily = LeagueSpartan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp
                    )
                },
                navigationIcon = { 
                    TextButton(onClick = onBack) { 
                        Text(
                            "Back",
                            fontFamily = LeagueSpartan,
                            fontWeight = FontWeight.Medium
                        ) 
                    } 
                },
                actions = {
                    if (uiState.inSelectionMode) {
                        val canDeleteAll = !isClosed && uiState.selectedPhotos.isNotEmpty() && uiState.selectedPhotos.all { id ->
                            val p = uiState.photos.find { it.id == id }
                            p != null && (p.uploaderUid == uiState.currentUserUid || uiState.circleInfo?.ownerUid == uiState.currentUserUid)
                        }
                        IconButton(onClick = onDeleteSelectedPhotos, enabled = canDeleteAll) {
                            Icon(
                                Icons.Filled.Delete,
                                contentDescription = "Delete Selected Photos",
                                tint = if (canDeleteAll) MaterialTheme.colorScheme.error else Color.Gray
                            )
                        }
                        IconButton(onClick = onDownloadSelectedPhotos) {
                            Icon(Icons.Filled.Download, contentDescription = "Download Selected Photos")
                        }
                        IconButton(onClick = onToggleSelectionMode) {
                            Icon(Icons.Filled.Cancel, contentDescription = "Cancel Selection")
                        }
                    } else {
                        IconButton(onClick = { onShowInviteDialog(true) }) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Invite People")
                        }
                        if (!isClosed) {
                            IconButton(onClick = onUploadPhoto) {
                                Icon(Icons.Filled.FileUpload, contentDescription = "Upload Photo")
                            }
                        }
                    }
                }
            )
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                if (uiState.circleInfo == null) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator()
                            Spacer(Modifier.height(8.dp))
                            Text("Loading circle...", fontFamily = LeagueSpartan)
                        }
                    }
                    return@Column
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (isClosed) "Status: CLOSED" else "Status: OPEN",
                        style = MaterialTheme.typography.titleMedium,
                        color = if (isClosed) Color.Gray else Color(0xFF4CAF50),
                        fontFamily = LeagueSpartan,
                        fontWeight = FontWeight.SemiBold
                    )

                    if (!isClosed) {
                        Text(
                            text = "Closes in: ${uiState.remainingTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = LeagueSpartan
                        )
                    } else if (uiState.deleteInTime.isNotEmpty()) {
                        Text(
                            text = "Deletes in: ${uiState.deleteInTime}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.Gray,
                            fontFamily = LeagueSpartan
                        )
                    }
                }

                Spacer(Modifier.height(16.dp))
                Text(
                    "Photos (${uiState.photos.size})", 
                    style = MaterialTheme.typography.titleLarge,
                    fontFamily = LeagueSpartan,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(8.dp))

                if (uiState.isUploading) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(modifier = Modifier.size(24.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Uploading...", style = MaterialTheme.typography.bodySmall, fontFamily = LeagueSpartan)
                    }
                    Spacer(Modifier.height(8.dp))
                }

                if (uiState.photos.isEmpty()) {
                    Text("No photos yet.", fontFamily = LeagueSpartan)
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
                                .combinedClickable(
                                    onClick = {
                                        if (uiState.inSelectionMode) {
                                            onTogglePhotoSelection(p.id)
                                        } else {
                                            onSetFullscreenImage(index)
                                        }
                                    },
                                    onLongClick = {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (!uiState.inSelectionMode) {
                                            onToggleSelectionMode()
                                        }
                                        onTogglePhotoSelection(p.id)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 4.dp else 0.dp,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent
                                )
                            ) {
                                Box(modifier = Modifier.fillMaxSize()) {
                                    val request = if (p.mediaType == "video") {
                                        ImageRequest.Builder(LocalContext.current)
                                            .data(p.downloadUrl)
                                            .decoderFactory(VideoFrameDecoder.Factory())
                                            .build()
                                    } else {
                                        ImageRequest.Builder(LocalContext.current)
                                            .data(p.downloadUrl)
                                            .build()
                                    }
                                    
                                    AsyncImage(
                                        model = request,
                                        contentDescription = "Circle media",
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .aspectRatio(1f)
                                            .clip(MaterialTheme.shapes.medium),
                                        contentScale = ContentScale.Crop
                                    )
                                    
                                    if (p.mediaType == "video") {
                                        Icon(
                                            imageVector = Icons.Filled.Videocam,
                                            contentDescription = "Video Icon",
                                            tint = Color.White,
                                            modifier = Modifier
                                                .align(Alignment.TopStart)
                                                .padding(6.dp)
                                                .size(20.dp)
                                        )
                                    }
                                    
                                    if (isSelected) {
                                        Icon(
                                            imageVector = Icons.Filled.CheckCircle,
                                            contentDescription = "Selected",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier
                                                .align(Alignment.TopEnd)
                                                .padding(8.dp)
                                        )
                                    }
                                    
                                    if (uiState.inProgressSaves.contains(p.id)) {
                                        CircularProgressIndicator(
                                            modifier = Modifier
                                                .align(Alignment.Center)
                                                .padding(4.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Floating Camera Button at bottom middle
            if (!isClosed && !uiState.inSelectionMode) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 32.dp)
                        .size(100.dp)
                        .border(4.dp, MaterialTheme.colorScheme.onSurface, CircleShape)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surface)
                        .clickable { onShowCamera() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.CameraAlt,
                        contentDescription = "Take Photo",
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
    }

    if (uiState.showInviteDialog && uiState.circleInfo != null) {
        val onSurfaceColor = MaterialTheme.colorScheme.onSurface.toArgb()
        AlertDialog(
            onDismissRequest = { onShowInviteDialog(false) },
            title = { Text("Invite to ${uiState.circleInfo?.name}", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                val inviteCode = uiState.circleInfo!!.inviteCode
                Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                    val bitmap = remember(inviteCode, onSurfaceColor) {
                        generateQRCode(inviteCode, dotColor = onSurfaceColor)
                    }
                    if (bitmap != null) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.size(280.dp)
                        ) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Invite ShotCode",
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text(inviteCode, style = MaterialTheme.typography.headlineLarge, letterSpacing = 5.sp, fontFamily = LeagueSpartan)
                }
            },
            confirmButton = {
                TextButton(onClick = { onShowInviteDialog(false) }) {
                    Text("Close", fontFamily = LeagueSpartan)
                }
            }
        )
    }

    val fullscreenImageIndex = uiState.fullscreenImage
    if (fullscreenImageIndex != null) {
        Dialog(
            onDismissRequest = { onSetFullscreenImage(null) },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            val pagerState = rememberPagerState(initialPage = fullscreenImageIndex) {
                uiState.photos.size
            }
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onSetFullscreenImage(null) }
            ) {
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 200.dp),
                    pageSpacing = 16.dp,
                    beyondViewportPageCount = 2
                ) { page ->
                    val mediaItem = uiState.photos[page]
                    if (mediaItem.mediaType == "video") {
                        VideoPlayer(
                            videoUrl = mediaItem.downloadUrl,
                            isCurrentPage = pagerState.currentPage == page,
                            modifier = Modifier.fillMaxSize()
                        )
                    } else {
                        ZoomableImage(
                            model = mediaItem.downloadUrl,
                            contentDescription = "Full screen image",
                            isCurrentPage = pagerState.currentPage == page,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit,
                            onTap = { onSetFullscreenImage(null) }
                        )
                    }
                }

                val currentPhoto = uiState.photos.getOrNull(pagerState.currentPage)
                if (currentPhoto != null) {
                    Column(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(bottom = 80.dp, start = 16.dp, end = 16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        val uploader = uiState.userProfiles[currentPhoto.uploaderUid]
                        if (uploader != null) {
                            Row(
                                modifier = Modifier
                                    .align(Alignment.Start)
                                    .padding(bottom = 16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (uploader.photoUrl.isNotEmpty()) {
                                    AsyncImage(
                                        model = uploader.photoUrl,
                                        contentDescription = "Uploader profile picture",
                                        modifier = Modifier
                                            .size(40.dp)
                                            .clip(CircleShape),
                                        contentScale = ContentScale.Crop
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Person,
                                        contentDescription = "Uploader profile picture",
                                        modifier = Modifier.size(40.dp),
                                        tint = Color.White
                                    )
                                }
                                Spacer(modifier = Modifier.size(8.dp))
                                Text(
                                    text = uploader.username.ifEmpty { uploader.displayName },
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontFamily = LeagueSpartan
                                )
                            }
                        }

                        val isSaving = uiState.inProgressSaves.contains(currentPhoto.id)
                        val canDelete = !isClosed && (currentPhoto.uploaderUid == uiState.currentUserUid || uiState.circleInfo?.ownerUid == uiState.currentUserUid)
                        val isDeleting = uiState.deletingPhotos.contains(currentPhoto.id)

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            IconButton(
                                onClick = { onSavePhoto(currentPhoto.id) },
                                enabled = !isSaving,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.extraLarge)
                            ) {
                                if (isSaving) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Download,
                                        contentDescription = "Save to gallery",
                                        tint = Color.White,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.size(16.dp))

                            IconButton(
                                onClick = { onDeletePhoto(currentPhoto.id) },
                                enabled = canDelete && !isDeleting,
                                modifier = Modifier
                                    .size(64.dp)
                                    .background(Color.Black.copy(alpha = 0.5f), MaterialTheme.shapes.extraLarge)
                            ) {
                                if (isDeleting) {
                                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(32.dp))
                                } else {
                                    Icon(
                                        imageVector = Icons.Filled.Delete,
                                        contentDescription = "Delete photo",
                                        tint = if (canDelete) Color.White else Color.Gray,
                                        modifier = Modifier.size(32.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}