package com.crcleapp.crcle.ui.views

import android.Manifest
import android.net.Uri
import android.util.Log
import android.view.OrientationEventListener
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.*
import androidx.camera.video.Recorder
import androidx.camera.view.PreviewView
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.*
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.PermissionChecker
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import coil.decode.VideoFrameDecoder
import coil.request.ImageRequest
import com.crcleapp.crcle.R
import com.crcleapp.crcle.ui.viewmodels.CameraMode
import com.crcleapp.crcle.ui.viewmodels.CameraViewModel
import com.crcleapp.crcle.ui.viewmodels.HomeViewModel
import com.crcleapp.crcle.ui.viewmodels.TimerMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

val LeagueSpartan = FontFamily(
    Font(R.font.league_spartan_bold, FontWeight.Bold)
)

@Composable
fun ModeToggleSwitch(
    currentMode: CameraMode,
    animatedRotation: Float,
    onModeChanged: (CameraMode) -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = modifier
            .width(100.dp)
            .height(40.dp)
            .background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(20.dp))
            .padding(4.dp)
    ) {
        val isPhoto = currentMode == CameraMode.PHOTO
        val offsetTarget = if (isPhoto) 0f else 1f
        val animatedOffset by animateFloatAsState(targetValue = offsetTarget, label = "toggle_offset")

        Box(
            modifier = Modifier
                .fillMaxHeight()
                .fillMaxWidth(0.5f)
                .offset(x = 46.dp * animatedOffset)
                .background(Color.White.copy(alpha = 0.8f), RoundedCornerShape(16.dp))
        )

        Row(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        if (!isPhoto) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChanged(CameraMode.PHOTO) 
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.PhotoCamera,
                    contentDescription = "Photo Mode",
                    tint = if (isPhoto) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = animatedRotation }
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { 
                        if (isPhoto) {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onModeChanged(CameraMode.VIDEO) 
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Filled.Videocam,
                    contentDescription = "Video Mode",
                    tint = if (!isPhoto) Color.Black else Color.White,
                    modifier = Modifier.size(20.dp).graphicsLayer { rotationZ = animatedRotation }
                )
            }
        }
    }
}

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
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val homeUiState by homeViewModel.uiState.collectAsState()
    val cameraUiState by cameraViewModel.uiState.collectAsState()
    
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var videoCapture: VideoCapture<Recorder>? by remember { mutableStateOf(null) }
    var activeRecording: Recording? by remember { mutableStateOf(null) }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var previewView by remember { mutableStateOf<PreviewView?>(null) }
    
    // UI State for popups
    var showEmptyCircleAlert by remember { mutableStateOf(false) }

    // Orientation tracking
    var rotationDegrees by remember { mutableFloatStateOf(0f) }
    var currentRotation by remember { mutableIntStateOf(Surface.ROTATION_0) }
    
    val animatedRotation by animateFloatAsState(
        targetValue = rotationDegrees,
        animationSpec = tween(durationMillis = 300),
        label = "UIRotation"
    )

    val orientationEventListener = remember {
        object : OrientationEventListener(context) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation == ORIENTATION_UNKNOWN) return
                val rotation = when (orientation) {
                    in 45..134 -> Surface.ROTATION_270
                    in 135..224 -> Surface.ROTATION_180
                    in 225..314 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }
                val degrees = when (rotation) {
                    Surface.ROTATION_0 -> 0f
                    Surface.ROTATION_90 -> 90f
                    Surface.ROTATION_180 -> 180f
                    Surface.ROTATION_270 -> 270f
                    else -> 0f
                }
                if (rotationDegrees != degrees) {
                    rotationDegrees = degrees
                    currentRotation = rotation
                }
            }
        }
    }

    DisposableEffect(Unit) {
        orientationEventListener.enable()
        onDispose {
            orientationEventListener.disable()
        }
    }

    LaunchedEffect(currentRotation, imageCapture, videoCapture) {
        imageCapture?.targetRotation = currentRotation
        videoCapture?.targetRotation = currentRotation
    }

    val focusAlpha by animateFloatAsState(
        targetValue = if (cameraUiState.focusPoint != null) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "FocusAlpha"
    )

    val zoomState by (camera?.cameraInfo?.zoomState?.observeAsState()) ?: remember { mutableStateOf<ZoomState?>(null) }
    val minZoomRatio = zoomState?.minZoomRatio ?: 1f
    val maxZoomRatio = zoomState?.maxZoomRatio ?: 1f
    val currentZoomRatio = zoomState?.zoomRatio ?: 1f

    var buttonPosition by remember { mutableStateOf(Offset.Zero) }
    var screenCenter by remember { mutableStateOf(Offset.Zero) }
    var containerSize by remember { mutableStateOf(IntSize.Zero) }
    val animProgress = remember { Animatable(0f) }

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

    LaunchedEffect(cameraUiState.flashMode, imageCapture) {
        imageCapture?.flashMode = cameraUiState.flashMode
    }

    LaunchedEffect(cameraUiState.showCaptureAnimation) {
        if (cameraUiState.showCaptureAnimation) {
            animProgress.snapTo(0f)
            animProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(durationMillis = 800, easing = FastOutSlowInEasing)
            )
            cameraViewModel.clearCaptureAnimation()
        }
    }

    LaunchedEffect(cameraUiState.cameraInitializationError, cameraUiState.isCameraReady, cameraUiState.retryTrigger) {
        if (cameraUiState.cameraInitializationError != null) {
            delay(3000) 
            cameraViewModel.retryCamera()
        } else if (!cameraUiState.isCameraReady && cameraUiState.hasPermission) {
            delay(8000)
            if (!cameraUiState.isCameraReady) {
                cameraViewModel.retryCamera()
            }
        }
    }
    
    // Video Recording Timer
    LaunchedEffect(cameraUiState.isRecording) {
        if (cameraUiState.isRecording) {
            while (cameraUiState.recordingDurationSeconds < 90) {
                delay(1000)
                cameraViewModel.incrementRecordingDuration()
            }
            // Auto stop at 90 seconds
            if (cameraUiState.isRecording) {
                Log.d("CameraView", "Auto-stopping recording at 90 seconds")
                activeRecording?.stop()
                activeRecording = null
            }
        }
    }

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted -> cameraViewModel.onAudioPermissionResult(isGranted) }
    )

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { permissions -> 
            cameraViewModel.onPermissionResult(permissions[Manifest.permission.CAMERA] == true)
            cameraViewModel.onAudioPermissionResult(permissions[Manifest.permission.RECORD_AUDIO] == true)
        }
    )

    LaunchedEffect(Unit) {
        permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO))
        homeViewModel.handleCameraEntry(entryPointCircleId)
    }

    if (!cameraUiState.hasPermission) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required.", color = MaterialTheme.colorScheme.onBackground)
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)) }) {
                    Text("Grant Permission")
                }
            }
        }
        return
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .onGloballyPositioned { coords ->
                screenCenter = Offset(coords.size.width / 2f, coords.size.height / 2f)
                containerSize = coords.size
            }
            .windowInsetsPadding(WindowInsets.safeDrawing)
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // Top Header - Flash, Grid, and Timer
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(8.dp)
            ) {
                Row(
                    modifier = Modifier.align(Alignment.CenterStart),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        modifier = Modifier.graphicsLayer { rotationZ = animatedRotation },
                        onClick = {
                            val nextMode = when (cameraUiState.flashMode) {
                                ImageCapture.FLASH_MODE_OFF -> ImageCapture.FLASH_MODE_ON
                                ImageCapture.FLASH_MODE_ON -> ImageCapture.FLASH_MODE_AUTO
                                else -> ImageCapture.FLASH_MODE_OFF
                            }
                            cameraViewModel.setFlashMode(nextMode)
                        }
                    ) {
                        val icon = when (cameraUiState.flashMode) {
                            ImageCapture.FLASH_MODE_ON -> Icons.Filled.FlashOn
                            ImageCapture.FLASH_MODE_AUTO -> Icons.Filled.FlashAuto
                            else -> Icons.Filled.FlashOff
                        }
                        Icon(icon, contentDescription = "Flash Mode", tint = MaterialTheme.colorScheme.onBackground)
                    }

                    IconButton(
                        modifier = Modifier.graphicsLayer { rotationZ = animatedRotation },
                        onClick = { cameraViewModel.toggleGrid() }
                    ) {
                        Icon(
                            if (cameraUiState.showGrid) Icons.Filled.GridOn else Icons.Filled.GridOff,
                            contentDescription = "Toggle Grid",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                }

                if (minZoomRatio < 0.95f || currentZoomRatio > 1.05f) {
                    TextButton(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .graphicsLayer { rotationZ = animatedRotation },
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
                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape)
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(
                                text = "%.1fx".format(Locale.US, currentZoomRatio),
                                color = MaterialTheme.colorScheme.onBackground,
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.align(Alignment.CenterEnd),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (cameraUiState.timerMode != TimerMode.OFF && cameraUiState.cameraMode == CameraMode.PHOTO) {
                        Text(
                            text = when(cameraUiState.timerMode) {
                                TimerMode.FIVE -> "5s"
                                TimerMode.TEN -> "10s"
                                else -> ""
                            },
                            color = Color.Yellow,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = LeagueSpartan,
                            modifier = Modifier
                                .padding(end = 4.dp)
                                .graphicsLayer { rotationZ = animatedRotation }
                        )
                    }
                    if (cameraUiState.cameraMode == CameraMode.PHOTO) {
                        IconButton(
                            modifier = Modifier.graphicsLayer { rotationZ = animatedRotation },
                            onClick = { cameraViewModel.toggleTimer() }
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Timer,
                                contentDescription = "Timer",
                                tint = if (cameraUiState.timerMode != TimerMode.OFF) Color.Yellow else MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }
            }

            // Preview Container
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
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
                key(cameraUiState.lensFacing, cameraUiState.retryTrigger) {
                    AndroidView(
                        modifier = Modifier.fillMaxSize(),
                        factory = { ctx ->
                            val view = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            previewView = view
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                try {
                                    val cameraProvider = cameraProviderFuture.get()
                                    val preview = Preview.Builder()
                                        .build().also { p ->
                                            p.setSurfaceProvider(view.surfaceProvider)
                                        }
                                        
                                    val capture = ImageCapture.Builder()
                                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                                        .setFlashMode(cameraUiState.flashMode)
                                        .setTargetRotation(currentRotation)
                                        .build()
                                    imageCapture = capture

                                    val recorder = Recorder.Builder()
                                        .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
                                        .build()
                                    val vidCapture = VideoCapture.withOutput(recorder)
                                    vidCapture.targetRotation = currentRotation
                                    videoCapture = vidCapture
                                    
                                    Log.d("CameraView", "Camera and VideoCapture successfully bound")

                                    cameraProvider.unbindAll()
                                    camera = cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.Builder().requireLensFacing(cameraUiState.lensFacing).build(),
                                        preview,
                                        capture,
                                        vidCapture
                                    )
                                    cameraViewModel.setCameraReady(true)
                                } catch (e: Exception) {
                                    e.printStackTrace()
                                    cameraViewModel.setCameraError(e.localizedMessage ?: "Camera binding failed")
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            view
                        }
                    )
                }

                if (!cameraUiState.isCameraReady) {
                    Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.5f)), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            CircularProgressIndicator(color = Color.White)
                            Spacer(Modifier.height(16.dp))
                            Text(
                                text = if (cameraUiState.cameraInitializationError != null) 
                                    "Retrying camera connection..." 
                                else 
                                    "Loading camera...",
                                color = Color.White,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center,
                                modifier = Modifier.padding(horizontal = 32.dp)
                            )
                        }
                    }
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

                if (cameraUiState.remainingSeconds > 0) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = cameraUiState.remainingSeconds.toString(),
                            color = Color.White,
                            fontSize = 120.sp,
                            fontWeight = FontWeight.Bold,
                            fontFamily = LeagueSpartan,
                            modifier = Modifier.graphicsLayer { rotationZ = animatedRotation }
                        )
                    }
                }
                
                if (cameraUiState.isRecording) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 16.dp)
                            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(8.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Canvas(modifier = Modifier.size(8.dp)) {
                                drawCircle(Color.Red)
                            }
                            Spacer(Modifier.width(8.dp))
                            val mins = cameraUiState.recordingDurationSeconds / 60
                            val secs = cameraUiState.recordingDurationSeconds % 60
                            Text(
                                text = String.format(Locale.US, "%02d:%02d / 01:30", mins, secs),
                                color = Color.White,
                                fontFamily = LeagueSpartan,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.graphicsLayer { rotationZ = animatedRotation }
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
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
            }
        }

        // Zoom Bar
        if (cameraUiState.showZoomBar) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 24.dp)
                    .width(4.dp)
                    .height(200.dp)
                    .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f), RoundedCornerShape(2.dp)),
                contentAlignment = Alignment.BottomCenter
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .fillMaxHeight(cameraUiState.zoomLevel.coerceIn(0.01f, 1f))
                        .background(MaterialTheme.colorScheme.onBackground, RoundedCornerShape(2.dp))
                )
            }
        }

        // Bottom Controls Container
        val screenWidth = configuration.screenWidthDp.dp
        val circlesBtnSize = (screenWidth * 0.18f).coerceIn(48.dp, 80.dp)
        val captureBtnSize = (screenWidth * 0.25f).coerceIn(60.dp, 110.dp)
        val flipBtnSize = (screenWidth * 0.14f).coerceIn(40.dp, 64.dp)

        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(bottom = 32.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Mode Toggle exactly at the top of the lower section
                if (!cameraUiState.isRecording && !cameraUiState.isCapturing) {
                    ModeToggleSwitch(
                        currentMode = cameraUiState.cameraMode,
                        animatedRotation = animatedRotation,
                        onModeChanged = { cameraViewModel.setCameraMode(it) }
                    )
                } else {
                    Spacer(modifier = Modifier.height(40.dp)) // Maintain visual spacing
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Bottom Buttons Row (Circles, Capture, Flip)
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Left midpoint box
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!cameraUiState.isRecording) {
                                Box(
                                    modifier = Modifier
                                        .size(circlesBtnSize)
                                        .onGloballyPositioned { coords ->
                                            val pos = coords.positionInRoot()
                                            buttonPosition = Offset(pos.x + coords.size.width / 2f, pos.y + coords.size.height / 2f)
                                        }
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null
                                        ) { cameraViewModel.setShowCirclesPopup(true) }
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .graphicsLayer { rotationZ = animatedRotation },
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxSize()
                                                .border(2.dp, MaterialTheme.colorScheme.onBackground, CircleShape)
                                                .clip(CircleShape)
                                                .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            val tintColor = MaterialTheme.colorScheme.onBackground
                                            Canvas(modifier = Modifier.fillMaxSize(0.55f)) {
                                                val r = size.minDimension / 3f
                                                val center = Offset(size.width / 2, size.height / 2)
                                                drawCircle(color = tintColor, radius = r, center = Offset(center.x, center.y - r * 0.5f), style = Stroke(width = 2.5.dp.toPx()))
                                                drawCircle(color = tintColor, radius = r, center = Offset(center.x - r * 0.45f, center.y + r * 0.35f), style = Stroke(width = 2.5.dp.toPx()))
                                                drawCircle(color = tintColor, radius = r, center = Offset(center.x + r * 0.45f, center.y + r * 0.35f), style = Stroke(width = 2.5.dp.toPx()))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                        
                        Spacer(modifier = Modifier.size(captureBtnSize))

                        // Right midpoint box
                        Box(
                            modifier = Modifier.weight(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            if (!cameraUiState.isRecording) {
                                IconButton(
                                    modifier = Modifier
                                        .size(flipBtnSize)
                                        .background(MaterialTheme.colorScheme.onBackground.copy(alpha = 0.2f), CircleShape),
                                    onClick = { cameraViewModel.toggleLensFacing() }
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.FlipCameraAndroid,
                                        contentDescription = "Flip camera",
                                        tint = MaterialTheme.colorScheme.onBackground,
                                        modifier = Modifier
                                            .size(flipBtnSize * 0.6f)
                                            .graphicsLayer { rotationZ = animatedRotation }
                                    )
                                }
                            }
                        }
                    }

                    // Actual Capture Button at the physical center
                    val outlineColor = MaterialTheme.colorScheme.onBackground
                    val innerColor = if (cameraUiState.isRecording) {
                        MaterialTheme.colorScheme.onBackground
                    } else if (cameraUiState.cameraMode == CameraMode.VIDEO) {
                        MaterialTheme.colorScheme.onBackground.copy(alpha = 0.3f)
                    } else {
                        Color.Transparent
                    }
                    val iconVector = if (cameraUiState.isRecording) Icons.Filled.Stop else (if (cameraUiState.cameraMode == CameraMode.VIDEO) Icons.Filled.Videocam else Icons.Filled.Camera)
                    val iconTint = if (cameraUiState.isRecording) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.onBackground

                    Box(
                        modifier = Modifier
                            .size(captureBtnSize)
                            .clip(CircleShape)
                            .border(5.dp, outlineColor, CircleShape)
                            .background(innerColor, CircleShape)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {
                                Log.d("CameraView", "Capture button clicked. Mode: ${cameraUiState.cameraMode}, isRecording: ${cameraUiState.isRecording}, videoCapture null? ${videoCapture == null}")
                                
                                if (homeUiState.selectedCircleIds.isEmpty()) {
                                    showEmptyCircleAlert = true
                                    return@clickable
                                }
                                if (cameraUiState.isCapturing || cameraUiState.remainingSeconds > 0) return@clickable
                                
                                if (cameraUiState.cameraMode == CameraMode.PHOTO) {
                                    coroutineScope.launch {
                                        if (cameraUiState.timerMode != TimerMode.OFF) {
                                            var seconds = cameraUiState.timerMode.seconds
                                            while (seconds > 0) {
                                                cameraViewModel.setRemainingSeconds(seconds)
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                delay(1000)
                                                seconds--
                                            }
                                            cameraViewModel.setRemainingSeconds(0)
                                        }
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        cameraViewModel.triggerFlashEffect()
                                        val capture = imageCapture ?: return@launch
                                        cameraViewModel.setCapturing(true)
                                        val photoFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
                                        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
                                        capture.takePicture(
                                            outputOptions,
                                            ContextCompat.getMainExecutor(context),
                                            object : ImageCapture.OnImageSavedCallback {
                                                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                                                    cameraViewModel.setCapturing(false)
                                                    val savedUri = Uri.fromFile(photoFile)
                                                    cameraViewModel.onPhotoCaptured(savedUri)
                                                    homeViewModel.uploadPhotoToCircles(savedUri, "image", onUploadsComplete, onUploadFailed)
                                                }
                                                override fun onError(exc: ImageCaptureException) {
                                                    cameraViewModel.setCapturing(false)
                                                    onUploadFailed("Capture", exc.message ?: "Unknown error")
                                                }
                                            }
                                        )
                                    }
                                } else {
                                    // Video Mode
                                    if (cameraUiState.isRecording) {
                                        Log.d("CameraView", "Attempting to stop recording...")
                                        activeRecording?.stop()
                                        activeRecording = null
                                    } else {
                                        Log.d("CameraView", "Attempting to start recording...")
                                        if (!cameraUiState.hasAudioPermission) {
                                            Log.d("CameraView", "No audio permission, requesting...")
                                            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                                        } else {
                                            startRecording(context, videoCapture, cameraViewModel, homeViewModel, onUploadsComplete, onUploadFailed) { recording ->
                                                activeRecording = recording
                                            }
                                        }
                                    }
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = iconVector,
                            contentDescription = if (cameraUiState.isRecording) "Stop Recording" else "Capture",
                            tint = iconTint,
                            modifier = Modifier
                                .size(captureBtnSize * 0.5f)
                                .graphicsLayer { rotationZ = animatedRotation }
                        )
                    }
                }
            }
        }

        // Capture Animation Overlay
        if (animProgress.value > 0f && cameraUiState.lastCapturedUri != null && containerSize.width > 0) {
            val t = animProgress.value
            val currentX = lerp(screenCenter.x, buttonPosition.x, t)
            val currentY = lerp(screenCenter.y, buttonPosition.y, t)
            val currentScale = lerp(0.8f, 0.1f, t)
            val currentAlpha = lerp(1f, 0f, t)
            
            val animSize = (containerSize.width * 0.8f).coerceAtMost(400f)
            
            val request = remember(cameraUiState.lastCapturedUri) {
                ImageRequest.Builder(context)
                    .data(cameraUiState.lastCapturedUri)
                    .decoderFactory(VideoFrameDecoder.Factory())
                    .build()
            }

            AsyncImage(
                model = request,
                contentDescription = null,
                modifier = Modifier
                    .size(with(LocalDensity.current) { animSize.toDp() })
                    .graphicsLayer {
                        translationX = currentX - (animSize / 2f)
                        translationY = currentY - (animSize / 2f)
                        scaleX = currentScale
                        scaleY = currentScale
                        alpha = currentAlpha
                        rotationZ = animatedRotation
                    }
                    .clip(CircleShape)
                    .border(4.dp, MaterialTheme.colorScheme.onBackground.copy(alpha = currentAlpha), CircleShape),
                contentScale = ContentScale.Crop
            )
        }

        if (cameraUiState.showCirclesPopup) {
            AlertDialog(
                onDismissRequest = { cameraViewModel.setShowCirclesPopup(false) },
                title = { Text("Select Circles") },
                text = {
                    LazyColumn {
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

        if (showEmptyCircleAlert) {
            AlertDialog(
                onDismissRequest = { showEmptyCircleAlert = false },
                title = { Text("No Circle Selected") },
                text = { Text("Please select at least one circle to capture a photo or video.") },
                confirmButton = {
                    TextButton(onClick = {
                        showEmptyCircleAlert = false
                        cameraViewModel.setShowCirclesPopup(true)
                    }) {
                        Text("Select")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEmptyCircleAlert = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

private fun startRecording(
    context: android.content.Context,
    videoCapture: VideoCapture<Recorder>?,
    cameraViewModel: CameraViewModel,
    homeViewModel: HomeViewModel,
    onUploadsComplete: () -> Unit,
    onUploadFailed: (String, String) -> Unit,
    onRecordingStarted: (Recording) -> Unit
) {
    Log.d("CameraView", "startRecording function called. videoCapture is null? ${videoCapture == null}")
    val capture = videoCapture ?: return

    val videoFile = File(
        context.cacheDir,
        "video_${System.currentTimeMillis()}.mp4"
    )

    val outputOptions = FileOutputOptions.Builder(videoFile).build()

    try {
        val recording = capture.output
            .prepareRecording(context, outputOptions)
            .apply {
                if (PermissionChecker.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PermissionChecker.PERMISSION_GRANTED) {
                    Log.d("CameraView", "Audio enabled for recording")
                    withAudioEnabled()
                } else {
                    Log.w("CameraView", "Recording WITHOUT audio - permission denied")
                }
            }
            .start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                Log.d("CameraView", "RecordEvent received: ${recordEvent.javaClass.simpleName}")
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        Log.d("CameraView", "Recording started successfully")
                        cameraViewModel.setRecording(true)
                    }
                    is VideoRecordEvent.Finalize -> {
                        Log.d("CameraView", "Recording finalized. Error code: ${recordEvent.error}")
                        cameraViewModel.setRecording(false)
                        
                        if (!recordEvent.hasError() || recordEvent.error == VideoRecordEvent.Finalize.ERROR_NONE) {
                            val savedUri = Uri.fromFile(videoFile)
                            Log.d("CameraView", "Recording successful. Saving to URI: $savedUri")
                            cameraViewModel.onPhotoCaptured(savedUri)
                            homeViewModel.uploadPhotoToCircles(
                                uri = savedUri,
                                mediaType = "video",
                                onUploadsComplete = onUploadsComplete,
                                onUploadFailed = onUploadFailed
                            )
                        } else {
                            if (recordEvent.error != VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA) {
                                Log.e("CameraView", "Recording failed with error: ${recordEvent.error}")
                                onUploadFailed("Capture", "Recording failed: ${recordEvent.error}")
                            } else {
                                Log.w("CameraView", "Recording had NO_VALID_DATA (likely too short)")
                            }
                        }
                    }
                }
            }
        onRecordingStarted(recording)
    } catch (e: SecurityException) {
        Log.e("CameraView", "SecurityException during recording setup", e)
        onUploadFailed("Permission", "Audio permission required for video")
    } catch (e: Exception) {
        Log.e("CameraView", "Exception during recording setup", e)
        onUploadFailed("Capture", e.localizedMessage ?: "Unknown setup error")
    }
}

private fun lerp(start: Float, stop: Float, fraction: Float): Float {
    return start + fraction * (stop - start)
}
