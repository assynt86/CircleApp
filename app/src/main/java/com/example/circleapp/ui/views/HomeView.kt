package com.example.circleapp.ui.views

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.circleapp.R
import com.example.circleapp.ui.viewmodels.HomeUiState
import com.example.circleapp.ui.viewmodels.HomeViewModel

@Composable
fun HomeView(
    homeViewModel: HomeViewModel = viewModel(),
    onCircleClick: (String) -> Unit,
    onJoinCircle: (String) -> Unit,
    onCreateCircle: (String) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val context = LocalContext.current

    // Observe error message and show toast
    LaunchedEffect(uiState.errorMessage) {
        uiState.errorMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            homeViewModel.clearErrorMessage()
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        HomeViewContent(
            uiState = uiState,
            onCircleClick = onCircleClick,
            onJoinCircle = { homeViewModel.joinCircle(onSuccess = onJoinCircle) },
            onCreateCircle = { homeViewModel.createCircle(onSuccess = onCreateCircle) },
            onShowCreateCircleDialog = { homeViewModel.showCreateCircleDialog(it) },
            onShowJoinCircleDialog = { homeViewModel.showJoinCircleDialog(it) },
            onNewCircleNameChange = { homeViewModel.onNewCircleNameChange(it) },
            onNewCircleDurationChange = { homeViewModel.onNewCircleDurationChange(it) },
            onInviteCodeChange = { homeViewModel.onInviteCodeChange(it) }
        )

        // Full screen loading overlay
        AnimatedVisibility(
            visible = uiState.isLoading,
            enter = fadeIn(),
            exit = fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = null,
                        modifier = Modifier
                            .height(100.dp)
                            .width(200.dp),
                        contentScale = ContentScale.Fit
                    )
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeViewContent(
    uiState: HomeUiState,
    onCircleClick: (String) -> Unit,
    onJoinCircle: () -> Unit,
    onCreateCircle: () -> Unit,
    onShowCreateCircleDialog: (Boolean) -> Unit,
    onShowJoinCircleDialog: (Boolean) -> Unit,
    onNewCircleNameChange: (String) -> Unit,
    onNewCircleDurationChange: (Float) -> Unit,
    onInviteCodeChange: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(120.dp),
                title = { 
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .height(100.dp)
                            .width(200.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { onShowJoinCircleDialog(true) }) {
                            Icon(Icons.Filled.GroupAdd, contentDescription = "Join Circle")
                        }
                        IconButton(onClick = { onShowCreateCircleDialog(true) }) {
                            Icon(Icons.Filled.Add, contentDescription = "Create Circle")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Your circles", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (uiState.circles.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No circles yet. Create or join one!")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.circles) { circle ->
                        val borderColor = when {
                            circle.isExpiringSoon -> Color(0xFFF44336) // Red
                            !circle.isClosed -> Color(0xFF4CAF50) // Green
                            else -> Color.Transparent
                        }

                        Card(
                            shape = CircleShape,
                            border = if (borderColor != Color.Transparent) BorderStroke(4.dp, borderColor) else null,
                            modifier = Modifier
                                .clickable { onCircleClick(circle.id) }
                                .aspectRatio(1f)
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black),
                                contentAlignment = Alignment.Center
                            ) {
                                if (circle.previewUrl != null) {
                                    AsyncImage(
                                        model = circle.previewUrl,
                                        contentDescription = null,
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .alpha(0.5f),
                                        contentScale = ContentScale.Crop
                                    )
                                }

                                Text(
                                    text = circle.name,
                                    modifier = Modifier.padding(12.dp),
                                    textAlign = TextAlign.Center,
                                    color = Color.White,
                                    style = MaterialTheme.typography.titleLarge.copy(
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold
                                    ),
                                    softWrap = true,
                                    maxLines = 2
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (uiState.isCreateCircleDialogVisible) {
        AlertDialog(
            onDismissRequest = { onShowCreateCircleDialog(false) },
            title = { Text("Create a Circle") },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.newCircleName,
                        onValueChange = onNewCircleNameChange,
                        label = { Text("Circle name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Duration: ${uiState.newCircleDurationDays.toInt()} days")
                    Slider(
                        value = uiState.newCircleDurationDays,
                        onValueChange = onNewCircleDurationChange,
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onCreateCircle,
                    enabled = uiState.newCircleName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowCreateCircleDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (uiState.isJoinCircleDialogVisible) {
        val context = LocalContext.current
        var hasCameraPermission by remember {
            mutableStateOf(
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            )
        }

        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission(),
            onResult = { granted -> hasCameraPermission = granted }
        )

        AlertDialog(
            onDismissRequest = { onShowJoinCircleDialog(false) },
            title = { Text("Join a Circle") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(MaterialTheme.shapes.medium)
                        ) {
                            var lastScannedCode by remember { mutableStateOf("") }
                            QRScanner(
                                modifier = Modifier.fillMaxSize(),
                                onQrCodeScanned = { code ->
                                    if (code.length == 6 && code != lastScannedCode) {
                                        lastScannedCode = code
                                        onInviteCodeChange(code)
                                        onJoinCircle()
                                    }
                                }
                            )
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(MaterialTheme.shapes.medium)
                                .background(Color.Gray)
                                .clickable { permissionLauncher.launch(android.Manifest.permission.CAMERA) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text("Tap to enable Camera", color = Color.White)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Or enter code manually")
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = uiState.joinInviteCode,
                        onValueChange = onInviteCodeChange,
                        decorationBox = @Composable { _ ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                repeat(6) { index ->
                                    val char = uiState.joinInviteCode.getOrNull(index)
                                    Box(
                                        modifier = Modifier
                                            .width(35.dp)
                                            .height(45.dp)
                                            .border(
                                                1.dp,
                                                if (char != null) MaterialTheme.colorScheme.primary else Color.Gray,
                                                RoundedCornerShape(8.dp)
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = char?.toString() ?: "_",
                                            style = MaterialTheme.typography.headlineMedium,
                                            color = if (char == null) Color.Gray else MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = onJoinCircle,
                    enabled = uiState.joinInviteCode.length == 6
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowJoinCircleDialog(false) }) {
                    Text("Cancel")
                }
            }
        )
    }
}
