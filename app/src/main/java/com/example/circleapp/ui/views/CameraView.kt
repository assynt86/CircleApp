package com.example.circleapp.ui.views

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.circleapp.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun CameraView(
    homeViewModel: HomeViewModel,
    entryPointCircleId: String?,
    onCancel: () -> Unit,
    onUploadsComplete: () -> Unit,
    onUploadFailed: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val uiState by homeViewModel.uiState.collectAsState()
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    
    var flashMode by remember { mutableIntStateOf(ImageCapture.FLASH_MODE_OFF) }
    var zoomLevel by remember { mutableFloatStateOf(0f) } 
    var showZoomBar by remember { mutableStateOf(false) }
    var showGrid by remember { mutableStateOf(false) }
    
    var showCirclesPopup by remember { mutableStateOf(false) }
    var showFlashUIEffect by remember { mutableStateOf(false) }

    // Observe zoom state for ratio display and ultrawide support
    val zoomState by (camera?.cameraInfo?.zoomState?.observeAsState()) ?: remember { mutableStateOf<ZoomState?>(null) }
    val minZoomRatio = zoomState?.minZoomRatio ?: 1f
    val maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
    val currentZoomRatio = zoomState?.zoomRatio ?: 1f

    LaunchedEffect(showFlashUIEffect) {
        if (showFlashUIEffect) {
            delay(100)
            showFlashUIEffect = false
        }
    }

    // Auto-hide zoom bar after 1.5 seconds
    LaunchedEffect(showZoomBar, zoomLevel) {
        if (showZoomBar) {
            delay(1500)
            showZoomBar = false
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> homeViewModel.onCameraPermissionResult(isGranted) }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        homeViewModel.handleCameraEntry(entryPointCircleId)
    }

    if (!uiState.hasCameraPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required.", color = Color.White)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .pointerInput(camera) {
                if (camera == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                zoomLevel = (zoomLevel + (zoom - 1f) * 1.2f).coerceIn(0f, 1f)
                                camera?.cameraControl?.setLinearZoom(zoomLevel)
                                showZoomBar = true
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also { p ->
                        p.setSurfaceProvider(previewView.surfaceProvider)
                    }
                    val capture = ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .setFlashMode(flashMode)
                        .build()
                    imageCapture = capture

                    try {
                        cameraProvider.unbindAll()
                        camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            capture
                        )
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            }
        )

        // 3x3 Grid Overlay
        if (showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                
                // Vertical lines
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(width / 3, 0f),
                    end = Offset(width / 3, height),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(2 * width / 3, 0f),
                    end = Offset(2 * width / 3, height),
                    strokeWidth = 1.dp.toPx()
                )
                
                // Horizontal lines
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(0f, height / 3),
                    end = Offset(width, height / 3),
                    strokeWidth = 1.dp.toPx()
                )
                drawLine(
                    color = Color.White.copy(alpha = 0.5f),
                    start = Offset(0f, 2 * height / 3),
                    end = Offset(width, 2 * height / 3),
                    strokeWidth = 1.dp.toPx()
                )
            }
        }

        // Capture flash effect
        if (showFlashUIEffect) {
            Box(Modifier.fillMaxSize().border(4.dp, Color.White))
        }

        // Subtle Left Side Zoom Bar
        if (showZoomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .width(2.dp)
                    .height(200.dp)
                    .background(Color.White.copy(alpha = 0.3f), RoundedCornerShape(1.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(zoomLevel.coerceIn(0.01f, 1f))
                        .background(Color.White, RoundedCornerShape(1.dp))
                )
            }
        }

        // Left area swipe detector for zoom
        Box(
            modifier = Modifier
                .align(Alignment.CenterStart)
                .fillMaxHeight()
                .fillMaxWidth(0.2f)
                .pointerInput(camera) {
                    if (camera == null) return@pointerInput
                    detectVerticalDragGestures { change, dragAmount ->
                        change.consume()
                        val sensitivity = 0.003f
                        val delta = -dragAmount * sensitivity
                        zoomLevel = (zoomLevel + delta).coerceIn(0f, 1f)
                        camera?.cameraControl?.setLinearZoom(zoomLevel)
                        showZoomBar = true
                    }
                }
        )

        // Top Controls Bar (Redesigned for better centering)
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp)
        ) {
            // Left Controls
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val nextMode = when (flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    flashMode = nextMode
                    imageCapture?.flashMode = nextMode
                }) {
                    val icon = when (flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    }
                    Icon(icon, contentDescription = "Flash Mode", tint = Color.White)
                }

                IconButton(onClick = { showGrid = !showGrid }) {
                    Icon(
                        if (showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                        contentDescription = "Toggle Grid",
                        tint = Color.White
                    )
                }
            }

            // Center: Zoom Toggle and Indicator
            // Show if device has ultrawide support OR if currently zoomed in
            if (minZoomRatio < 0.95f || currentZoomRatio > 1.05f) {
                TextButton(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = {
                        if (currentZoomRatio > 0.95f) {
                            // If at 1x or higher, jump to ultrawide (if supported) or 1x
                            val target = if (minZoomRatio < 0.95f) minZoomRatio else 1.0f
                            camera?.cameraControl?.setZoomRatio(target)
                            zoomLevel = if (target == 1.0f && maxZoomRatio > minZoomRatio) (1.0f - minZoomRatio) / (maxZoomRatio - minZoomRatio) else 0f
                        } else {
                            // If in ultrawide, jump to 1x
                            camera?.cameraControl?.setZoomRatio(1.0f)
                            zoomLevel = if (maxZoomRatio > minZoomRatio) (1.0f - minZoomRatio) / (maxZoomRatio - minZoomRatio) else 0f
                        }
                        showZoomBar = true
                    }
                ) {
                    Box(
                        modifier = Modifier
                            .background(Color.Black.copy(alpha = 0.4f), CircleShape)
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "%.1fx".format(Locale.US, currentZoomRatio),
                            color = Color.White,
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }
            }

            // Right: Circle Selector
            IconButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { showCirclesPopup = true }
            ) {
                Icon(Icons.Filled.Groups, contentDescription = "Select Circles", tint = Color.White)
            }
        }

        // Bottom Capture Button Area
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 96.dp)
                .fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                modifier = Modifier
                    .size(110.dp)
                    .border(5.dp, Color.White, CircleShape),
                onClick = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showFlashUIEffect = true
                    val capture = imageCapture ?: return@IconButton

                    // Lock the UI immediately
                    homeViewModel.setCapturing(true)

                    val photoFile = File(
                        context.cacheDir,
                        "photo_${System.currentTimeMillis()}.jpg"
                    )

                    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

                    capture.takePicture(
                        outputOptions,
                        ContextCompat.getMainExecutor(context),
                        object : ImageCapture.OnImageSavedCallback {
                            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                // Unlock UI as soon as image is saved to disk
                                homeViewModel.setCapturing(false)
                                
                                val savedUri = Uri.fromFile(photoFile)
                                homeViewModel.uploadPhotoToCircles(
                                    uri = savedUri,
                                    onUploadsComplete = onUploadsComplete,
                                    onUploadFailed = onUploadFailed
                                )
                            }

                            override fun onError(exc: ImageCaptureException) {
                                homeViewModel.setCapturing(false)
                                onUploadFailed("Capture", exc.message ?: "Unknown error")
                            }
                        }
                    )
                },
                enabled = !uiState.isCapturing && uiState.selectedCircleIds.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Camera,
                    contentDescription = "Capture photo",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }
        }

        // Circle selection popup
        if (showCirclesPopup) {
            AlertDialog(
                onDismissRequest = { showCirclesPopup = false },
                title = { Text("Select Circles") },
                text = {
                    LazyColumn {
                        items(uiState.circles) { circle ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val isChecked = !uiState.selectedCircleIds.contains(circle.id)
                                        homeViewModel.onCircleSelected(circle.id, isChecked)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = uiState.selectedCircleIds.contains(circle.id),
                                    onCheckedChange = { isChecked ->
                                        homeViewModel.onCircleSelected(circle.id, isChecked)
                                    }
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(circle.name)
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showCirclesPopup = false }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}
