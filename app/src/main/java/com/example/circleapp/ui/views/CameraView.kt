package com.example.circleapp.ui.views

import android.Manifest
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Camera
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.circleapp.ui.viewmodels.HomeViewModel
import kotlinx.coroutines.delay
import java.io.File

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
    val uiState by homeViewModel.uiState.collectAsState()
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var showCirclesPopup by remember { mutableStateOf(false) }
    var showFlash by remember { mutableStateOf(false) }

    LaunchedEffect(showFlash) {
        if (showFlash) {
            delay(100)
            showFlash = false
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
                Text("Camera permission is required.")
                Spacer(Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Permission")
                }
                Spacer(Modifier.height(12.dp))
                TextButton(onClick = onCancel) { Text("Cancel") }
            }
        }
        return
    }

    Box(Modifier.fillMaxSize()) {        AndroidView(
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
                        .build()
                    imageCapture = capture

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
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

        if (showFlash) {
            Box(
                Modifier
                    .fillMaxSize()
                    .border(4.dp, Color.White)
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 64.dp, end = 16.dp)
        ) {
            IconButton(onClick = { showCirclesPopup = true }) {
                Icon(Icons.Filled.Groups, contentDescription = "Select Circles", tint = Color.White)
            }

            DropdownMenu(
                expanded = showCirclesPopup,
                onDismissRequest = { showCirclesPopup = false }
            ) { uiState.circles.forEach { circle ->
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(
                                checked = uiState.selectedCircleIds.contains(circle.id),
                                onCheckedChange = { isChecked ->
                                    homeViewModel.onCircleSelected(circle.id, isChecked)
                                }
                            )
                            Text(circle.name)
                        }
                    },
                    onClick = {
                        homeViewModel.onCircleSelected(circle.id, !uiState.selectedCircleIds.contains(circle.id))
                    }
                )
            } }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            IconButton(
                modifier = Modifier
                    .size(80.dp)
                    .padding(8.dp)
                    .border(2.dp, Color.White, CircleShape),
                onClick = {
                    showFlash = true
                    val capture = imageCapture ?: return@IconButton

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
                                val savedUri = Uri.fromFile(photoFile)
                                homeViewModel.uploadPhotoToCircles(
                                    uri = savedUri,
                                    onUploadsComplete = onUploadsComplete,
                                    onUploadFailed = onUploadFailed
                                )
                            }

                            override fun onError(exc: ImageCaptureException) {
                                onUploadFailed("Capture", exc.message ?: "Unknown error")
                                onCancel()
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
                    modifier = Modifier.size(50.dp)
                )
            }

            Spacer(Modifier.height(8.dp))

            OutlinedButton(
                onClick = onCancel
            ) {
                Text("Cancel")
            }
        }
    }
}
