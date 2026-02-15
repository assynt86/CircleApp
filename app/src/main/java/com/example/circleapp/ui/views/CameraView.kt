package com.example.circleapp.ui.views

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circleapp.ui.viewmodels.CameraViewModel
import com.example.circleapp.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import java.io.File
import java.util.Locale

@Composable
fun CameraView(
    homeViewModel: HomeViewModel,
    cameraViewModel: CameraViewModel = viewModel(),
    entryPointCircleId: String?,
    onCancel: () -> Unit,
    onUploadsComplete: () -> Unit,
    onUploadFailed: (String, String) -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current
    val homeUiState by homeViewModel.uiState.collectAsState()
    val cameraUiState by cameraViewModel.uiState.collectAsState()
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
    val focusAlpha by animateFloatAsState(
        targetValue = if (cameraUiState.focusPoint != null) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "FocusAlpha"
    )

    val zoomState by (camera?.cameraInfo?.zoomState?.observeAsState()) ?: remember { mutableStateOf<ZoomState?>(null) }
    val minZoomRatio = zoomState?.minZoomRatio ?: 1f
    val maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
    val currentZoomRatio = zoomState?.zoomRatio ?: 1f

    LaunchedEffect(cameraUiState.showFlashUIEffect) {
        if (cameraUiState.showFlashUIEffect) {
            delay(100)
            cameraViewModel.hideFlashEffect()
        }
    }

    LaunchedEffect(cameraUiState.showZoomBar, cameraUiState.zoomLevel) {
        if (cameraUiState.showZoomBar) {
            delay(1500)
            cameraViewModel.hideZoomBar()
        }
    }

    LaunchedEffect(cameraUiState.focusPoint) {
        if (cameraUiState.focusPoint != null) {
            delay(700)
            cameraViewModel.setFocusPoint(null)
        }
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> cameraViewModel.onPermissionResult(isGranted) }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
        homeViewModel.handleCameraEntry(entryPointCircleId)
    }

    if (!cameraUiState.hasPermission) {
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
                detectTapGestures { offset ->
                    val factory = previewView?.meteringPointFactory ?: return@detectTapGestures
                    val point = factory.createPoint(offset.x, offset.y)
                    val action = FocusMeteringAction.Builder(point, FocusMeteringAction.FLAG_AF)
                        .setAutoCancelDuration(3, java.util.concurrent.TimeUnit.SECONDS)
                        .build()
                    
                    cameraViewModel.setFocusPoint(offset)
                    camera?.cameraControl?.startFocusAndMetering(action)
                }
            }
            .pointerInput(camera) {
                if (camera == null) return@pointerInput
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        if (event.changes.size > 1) {
                            val zoom = event.calculateZoom()
                            if (zoom != 1f) {
                                val newZoom = (cameraUiState.zoomLevel + (zoom - 1f) * 1.2f).coerceIn(0f, 1f)
                                cameraViewModel.setZoomLevel(newZoom)
                                camera?.cameraControl?.setLinearZoom(newZoom)
                                event.changes.forEach { it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })
                }
            }
    ) {
        key(cameraUiState.lensFacing) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    val view = PreviewView(ctx)
                    previewView = view
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also { p ->
                            p.setSurfaceProvider(view.surfaceProvider)
                        }
                        val capture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                            .setFlashMode(cameraUiState.flashMode)
                            .build()
                        imageCapture = capture

                        try {
                            cameraProvider.unbindAll()
                            camera = cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.Builder().requireLensFacing(cameraUiState.lensFacing).build(),
                                preview,
                                capture
                            )
                        } catch (e: Exception) {
                            e.printStackTrace()
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    view
                }
            )
        }

        cameraUiState.focusPoint?.let { point ->
            Canvas(modifier = Modifier.fillMaxSize()) {
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha),
                    radius = 40.dp.toPx(),
                    center = point,
                    style = Stroke(width = 2.dp.toPx())
                )
                drawCircle(
                    color = Color.White.copy(alpha = focusAlpha * 0.5f),
                    radius = 2.dp.toPx(),
                    center = point
                )
            }
        }

        if (cameraUiState.showGrid) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val width = size.width
                val height = size.height
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(width / 3, 0f), end = Offset(width / 3, height), strokeWidth = 1.dp.toPx())
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(2 * width / 3, 0f), end = Offset(2 * width / 3, height), strokeWidth = 1.dp.toPx())
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(0f, height / 3), end = Offset(width, height / 3), strokeWidth = 1.dp.toPx())
                drawLine(color = Color.White.copy(alpha = 0.5f), start = Offset(0f, 2 * height / 3), end = Offset(width, 2 * height / 3), strokeWidth = 1.dp.toPx())
            }
        }

        if (cameraUiState.showFlashUIEffect) {
            Box(Modifier.fillMaxSize().border(4.dp, Color.White))
        }

        if (cameraUiState.showZoomBar) {
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
                        .fillMaxHeight(cameraUiState.zoomLevel.coerceIn(0.01f, 1f))
                        .background(Color.White, RoundedCornerShape(1.dp))
                )
            }
        }

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
                        val newZoom = (cameraUiState.zoomLevel + delta).coerceIn(0f, 1f)
                        cameraViewModel.setZoomLevel(newZoom)
                        camera?.cameraControl?.setLinearZoom(newZoom)
                    }
                }
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 48.dp, start = 8.dp, end = 8.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.CenterStart),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    val nextMode = when (cameraUiState.flashMode) {
                        ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                        ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                        else -> ImageCapture.FLASH_MODE_OFF
                    }
                    cameraViewModel.setFlashMode(nextMode)
                    imageCapture?.flashMode = nextMode
                }) {
                    val icon = when (cameraUiState.flashMode) {
                        ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                        ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                        else -> Icons.Filled.FlashOff
                    }
                    Icon(icon, contentDescription = "Flash Mode", tint = Color.White)
                }

                IconButton(onClick = { cameraViewModel.toggleGrid() }) {
                    Icon(
                        if (cameraUiState.showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                        contentDescription = "Toggle Grid",
                        tint = Color.White
                    )
                }
            }

            if (minZoomRatio < 0.95f || currentZoomRatio > 1.05f) {
                TextButton(
                    modifier = Modifier.align(Alignment.Center),
                    onClick = {
                        if (currentZoomRatio > 0.95f) {
                            val target = if (minZoomRatio < 0.95f) minZoomRatio else 1.0f
                            camera?.cameraControl?.setZoomRatio(target)
                            cameraViewModel.setZoomLevel(if (target == 1.0f && maxZoomRatio > minZoomRatio) (1.0f - minZoomRatio) / (maxZoomRatio - minZoomRatio) else 0f)
                        } else {
                            camera?.cameraControl?.setZoomRatio(1.0f)
                            cameraViewModel.setZoomLevel(if (maxZoomRatio > minZoomRatio) (1.0f - minZoomRatio) / (maxZoomRatio - minZoomRatio) else 0f)
                        }
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

            IconButton(
                modifier = Modifier.align(Alignment.CenterEnd),
                onClick = { cameraViewModel.setShowCirclesPopup(true) }
            ) {
                Icon(Icons.Filled.Groups, contentDescription = "Select Circles", tint = Color.White)
            }
        }

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
                    cameraViewModel.triggerFlashEffect()
                    val capture = imageCapture ?: return@IconButton

                    cameraViewModel.setCapturing(true)

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
                                cameraViewModel.setCapturing(false)
                                val savedUri = Uri.fromFile(photoFile)
                                homeViewModel.uploadPhotoToCircles(
                                    uri = savedUri,
                                    onUploadsComplete = onUploadsComplete,
                                    onUploadFailed = onUploadFailed
                                )
                            }

                            override fun onError(exc: ImageCaptureException) {
                                cameraViewModel.setCapturing(false)
                                onUploadFailed("Capture", exc.message ?: "Unknown error")
                            }
                        }
                    )
                },
                enabled = !cameraUiState.isCapturing && homeUiState.selectedCircleIds.isNotEmpty()
            ) {
                Icon(
                    imageVector = Icons.Filled.Camera,
                    contentDescription = "Capture photo",
                    tint = Color.White,
                    modifier = Modifier.size(64.dp)
                )
            }

            IconButton(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 48.dp)
                    .size(48.dp)
                    .background(Color.Black.copy(alpha = 0.4f), CircleShape),
                onClick = { cameraViewModel.toggleLensFacing() }
            ) {
                Icon(
                    imageVector = Icons.Filled.FlipCameraAndroid,
                    contentDescription = "Flip camera",
                    tint = Color.White
                )
            }
        }

        if (cameraUiState.showCirclesPopup) {
            AlertDialog(
                onDismissRequest = { cameraViewModel.setShowCirclesPopup(false) },
                title = { Text("Select Circles") },
                text = {
                    LazyColumn {
                        // Only show open circles for photo capture
                        items(homeUiState.circles.filter { !it.isClosed }) { circle ->
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        val isChecked = !homeUiState.selectedCircleIds.contains(circle.id)
                                        homeViewModel.onCircleSelected(circle.id, isChecked)
                                    }
                                    .padding(vertical = 8.dp)
                            ) {
                                Checkbox(
                                    checked = homeUiState.selectedCircleIds.contains(circle.id),
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
                    TextButton(onClick = { cameraViewModel.setShowCirclesPopup(false) }) {
                        Text("Done")
                    }
                }
            )
        }
    }
}
