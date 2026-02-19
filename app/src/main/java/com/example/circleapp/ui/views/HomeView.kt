package com.example.circleapp.ui.views

import android.content.pm.PackageManager
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.GroupAdd
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Group
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.circleapp.R
import com.example.circleapp.ui.theme.LeagueSpartan
import com.example.circleapp.ui.viewmodels.HomeUiState
import com.example.circleapp.ui.viewmodels.HomeViewModel
import com.example.circleapp.data.Circle

@Composable
fun HomeView(
    homeViewModel: HomeViewModel = viewModel(),
    onCircleClick: (String) -> Unit,
    onJoinCircle: (String) -> Unit,
    onCreateCircle: (String) -> Unit,
    onInvitesClick: () -> Unit
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
            onJoinCircle = { homeViewModel.onJoinClick(onSuccess = onJoinCircle) },
            onCreateCircle = { homeViewModel.createCircle(onSuccess = onCreateCircle) },
            onShowCreateCircleDialog = { homeViewModel.showCreateCircleDialog(it) },
            onShowJoinCircleDialog = { homeViewModel.showJoinCircleDialog(it) },
            onNewCircleNameChange = { homeViewModel.onNewCircleNameChange(it) },
            onNewCircleDurationChange = { homeViewModel.onNewCircleDurationChange(it) },
            onInviteCodeChange = { homeViewModel.onInviteCodeChange(it) },
            onInvitesClick = onInvitesClick
        )

        // Full screen loading overlay
        AnimatedVisibility(
            visible = uiState.isLoading && uiState.circles.isEmpty(),
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
                            .height(300.dp)
                            .width(600.dp),
                        contentScale = ContentScale.Fit,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(Modifier.height(32.dp))
                    CircularProgressIndicator(
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
        }

        if (uiState.showJoinPreview && uiState.previewCircle != null) {
            JoinPreviewDialog(
                circle = uiState.previewCircle!!,
                onDismiss = { homeViewModel.cancelJoin() },
                onConfirm = { homeViewModel.confirmJoinCircle(uiState.previewCircle!!.id, onJoinCircle) }
            )
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
    onInviteCodeChange: (String) -> Unit,
    onInvitesClick: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                modifier = Modifier.height(180.dp),
                title = { 
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = stringResource(R.string.app_name),
                        modifier = Modifier
                            .height(150.dp)
                            .width(300.dp),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        colorFilter = ColorFilter.tint(MaterialTheme.colorScheme.onSurface)
                    )
                },
                actions = {
                    Row(
                        modifier = Modifier.fillMaxHeight(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        if (uiState.pendingInvitesCount > 0) {
                            IconButton(onClick = onInvitesClick) {
                                BadgedBox(badge = {
                                    Badge { Text(uiState.pendingInvitesCount.toString()) }
                                }) {
                                    Icon(Icons.Filled.Notifications, contentDescription = "Pending Invites")
                                }
                            }
                        }
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
            Text(
                "Your circles", 
                style = MaterialTheme.typography.titleMedium,
                fontFamily = LeagueSpartan,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(16.dp))

            if (uiState.circles.isEmpty() && !uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No circles yet. Create or join one!", fontFamily = LeagueSpartan)
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    items(uiState.circles) { circle ->
                        val baseColor = when {
                            circle.isClosed -> Color.Gray
                            circle.isExpiringSoon -> Color(0xFFF44336) // Red
                            else -> Color(0xFF4CAF50) // Green
                        }
                        val progress = circle.remainingProgress

                        Box(
                            modifier = Modifier
                                .clickable { onCircleClick(circle.id) }
                                .aspectRatio(1f),
                            contentAlignment = Alignment.Center
                        ) {
                            // Draw dynamic progress border
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val strokeWidth = 4.dp.toPx()
                                val diameter = size.minDimension - strokeWidth
                                
                                // Faded background circle (elapsed time)
                                drawCircle(
                                    color = baseColor.copy(alpha = 0.3f),
                                    radius = diameter / 2,
                                    style = Stroke(width = strokeWidth)
                                )
                                
                                // Solid progress arc (remaining time)
                                drawArc(
                                    color = baseColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * progress,
                                    useCenter = false,
                                    topLeft = Offset(strokeWidth / 2, strokeWidth / 2),
                                    size = Size(diameter, diameter),
                                    style = Stroke(width = strokeWidth)
                                )
                            }

                            Card(
                                shape = CircleShape,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(6.dp) // Inset to leave room for border
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(MaterialTheme.colorScheme.surface),
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
                                        color = MaterialTheme.colorScheme.onSurface,
                                        style = MaterialTheme.typography.titleLarge.copy(
                                            fontSize = 32.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        fontFamily = LeagueSpartan,
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
    }

    if (uiState.isCreateCircleDialogVisible) {
        AlertDialog(
            onDismissRequest = { onShowCreateCircleDialog(false) },
            title = { Text("Create a Circle", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = uiState.newCircleName,
                        onValueChange = onNewCircleNameChange,
                        label = { Text("Circle name", fontFamily = LeagueSpartan) },
                        singleLine = true
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Duration: ${uiState.newCircleDurationDays.toInt()} days", fontFamily = LeagueSpartan)
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
                    Text("Create", fontFamily = LeagueSpartan)
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowCreateCircleDialog(false) }) {
                    Text("Cancel", fontFamily = LeagueSpartan)
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
            title = { Text("Join a Circle", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    if (hasCameraPermission) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(250.dp)
                                .clip(MaterialTheme.shapes.medium),
                            contentAlignment = Alignment.Center
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
                            // Add circular trace overlay
                            val traceColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.4f)
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                drawCircle(
                                    color = traceColor,
                                    radius = size.minDimension / 2.5f,
                                    style = Stroke(
                                        width = 2.dp.toPx(),
                                        pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f), 0f)
                                    )
                                )
                            }
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
                            Text("Tap to enable Camera", color = Color.White, fontFamily = LeagueSpartan)
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                    Text("Or enter code manually", fontFamily = LeagueSpartan)
                    Spacer(Modifier.height(8.dp))
                    BasicTextField(
                        value = uiState.joinInviteCode,
                        onValueChange = onInviteCodeChange,
                        decorationBox = { innerTextField ->
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                for (index in 0 until 6) {
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
                                            fontFamily = LeagueSpartan,
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
                    Text("Join", fontFamily = LeagueSpartan)
                }
            },
            dismissButton = {
                TextButton(onClick = { onShowJoinCircleDialog(false) }) {
                    Text("Cancel", fontFamily = LeagueSpartan)
                }
            }
        )
    }
}

@Composable
fun JoinPreviewDialog(
    circle: Circle,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(28.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Join Circle?",
                    fontFamily = LeagueSpartan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 24.sp
                )
                Spacer(modifier = Modifier.height(24.dp))
                
                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                ) {
                    if (circle.backgroundUrl != null) {
                        AsyncImage(
                            model = circle.backgroundUrl,
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            Icons.Filled.Group,
                            contentDescription = null,
                            modifier = Modifier.size(80.dp).align(Alignment.Center),
                            tint = Color.White
                        )
                    }
                }
                
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = circle.name,
                    fontFamily = LeagueSpartan,
                    fontWeight = FontWeight.Bold,
                    fontSize = 22.sp,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(32.dp))
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Cancel", fontFamily = LeagueSpartan)
                    }
                    Button(
                        onClick = onConfirm,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black)
                    ) {
                        Text("Join", fontFamily = LeagueSpartan)
                    }
                }
            }
        }
    }
}
