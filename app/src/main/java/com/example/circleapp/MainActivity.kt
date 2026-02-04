package com.example.circleapp

import android.Manifest
import android.os.Bundle

import android.content.ContentValues
import android.provider.MediaStore
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageCapture.OutputFileOptions
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Button
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.circleapp.ui.theme.CircleAppTheme
import android.widget.Toast
import androidx.navigation.compose.*
import com.example.circleapp.data.CircleRepository
import com.example.circleapp.ui.HomeScreen
import com.google.firebase.auth.FirebaseAuth
import com.example.circleapp.ui.CircleScreen
import com.example.circleapp.ui.HomeViewModel

class MainActivity : ComponentActivity() {

    private val homeViewModel by viewModels<HomeViewModel>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val auth = FirebaseAuth.getInstance()
        if (auth.currentUser == null) {
            auth.signInAnonymously()
        }

        setContent {
            CircleAppTheme {
                val navController = rememberNavController()
                val repo = remember { CircleRepository() }
                val context = LocalContext.current

                NavHost(navController = navController, startDestination = "home") {

                    composable("home") {
                        val circles by homeViewModel.circles.collectAsState()

                        HomeScreen(
                            circles = circles,
                            onCreateCircle = { name, durationDays ->
                                repo.createCircle(
                                    circleName = name,
                                    durationDays = durationDays,
                                    onSuccess = { circleId ->
                                        Toast.makeText(context, "Circle created!", Toast.LENGTH_SHORT).show()
                                        navController.navigate("circle/$circleId")
                                    },
                                    onError = { e ->
                                        Toast.makeText(context, "Create failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onJoinCircle = { code ->
                                repo.joinCircleByInviteCode(
                                    inviteCode = code,
                                    onSuccess = { circleId ->
                                        Toast.makeText(context, "Joined circle!", Toast.LENGTH_SHORT).show()
                                        navController.navigate("circle/$circleId")
                                    },
                                    onNotFound = {
                                        Toast.makeText(context, "Invite code not found", Toast.LENGTH_LONG).show()
                                    },
                                    onError = { e ->
                                        Toast.makeText(context, "Join failed: ${e.message}", Toast.LENGTH_LONG).show()
                                    }
                                )
                            },
                            onCircleClick = { circleId ->
                                navController.navigate("circle/$circleId")
                            }
                        )
                    }

                    composable("circle/{circleId}") { backStackEntry ->
                        val circleId = backStackEntry.arguments?.getString("circleId") ?: ""
                        CircleScreen(
                            circleId = circleId,
                            onBack = { navController.popBackStack() }
                        )
                    }

                }
            }

        }
    }
}

@Composable
fun CameraPreviewScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Tracks whether permission is granted
    var hasCameraPermission by remember { mutableStateOf(false) }

    // Permission request launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
    }

    // Ask for permission once when screen loads
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.CAMERA)
    }

    if (hasCameraPermission) {
        CameraPreview(modifier = modifier.fillMaxSize())
    } else {
        // Simple UI to retry permission
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Camera permission is required to use this app.")
                Spacer(modifier = Modifier.height(12.dp))
                Button(onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) }) {
                    Text("Grant Camera Permission")
                }
            }
        }
    }
}

@Composable
fun CameraPreview(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

    // ✅ Keep ImageCapture in Compose state so button can use it
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }

    Box(modifier = modifier) {

        // --- Camera Preview ---
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
                        .build()

                    imageCapture = capture

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
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

        // --- Capture Button Overlay ---
        Button(
            onClick = {
                val capture = imageCapture ?: return@Button

                // ✅ Save directly to Gallery using MediaStore
                val name = "Circle_${System.currentTimeMillis()}"
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "$name.jpg")
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, "Pictures/Circle")
                }

                val outputOptions = OutputFileOptions.Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ).build()

                capture.takePicture(
                    outputOptions,
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                            android.widget.Toast.makeText(
                                context,
                                "Saved to Gallery (Pictures/Circle)",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        }

                        override fun onError(exception: ImageCaptureException) {
                            android.widget.Toast.makeText(
                                context,
                                "Capture failed: ${exception.message}",
                                android.widget.Toast.LENGTH_LONG
                            ).show()
                        }
                    }
                )
            },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            Text("Capture")
        }
    }
}
