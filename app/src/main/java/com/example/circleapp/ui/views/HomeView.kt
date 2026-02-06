package com.example.circleapp.ui.views

import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circleapp.ui.viewmodels.HomeUiState
import com.example.circleapp.ui.viewmodels.HomeViewModel
import com.google.accompanist.pager.ExperimentalPagerApi
import com.google.accompanist.pager.HorizontalPager
import com.google.accompanist.pager.rememberPagerState
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.launch

@OptIn(ExperimentalPagerApi::class)
@Composable
fun HomeView(
    homeViewModel: HomeViewModel = viewModel(),
    onCircleClick: (String) -> Unit,
    onPhotoSaved: (String) -> Unit,
    onUploadFailed: (String, String) -> Unit,
    onJoinCircle: (String, (String) -> Unit, () -> Unit, (Exception) -> Unit) -> Unit,
    onCreateCircle: (String, Int, (String) -> Unit, (Exception) -> Unit) -> Unit
) {
    val uiState by homeViewModel.uiState.collectAsState()
    val pagerState = rememberPagerState(initialPage = 1)
    val coroutineScope = rememberCoroutineScope()

    HorizontalPager(count = 2, state = pagerState) { page ->
        when (page) {
            0 -> CameraView(
                circles = uiState.circles.filter { it.status == "open" },
                selectedCircleIds = uiState.selectedCircleIds,
                onPhotoSaved = { uri, circleIds ->
                    homeViewModel.uploadPhotoToCircles(uri, onPhotoSaved, onUploadFailed)
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1, animationSpec = tween(500))
                    }
                },
                onCancel = {
                    coroutineScope.launch {
                        pagerState.animateScrollToPage(1, animationSpec = tween(500))
                    }
                },
                onCircleSelected = { circleId, isSelected ->
                    homeViewModel.onCircleSelected(circleId, isSelected)
                }
            )
            1 -> HomeViewContent(
                uiState = uiState,
                onCircleClick = onCircleClick,
                onJoinCircle = { onJoinCircle(uiState.joinInviteCode, { /* onSuccess */ }, { /* onNotFound */ }, { /* onError */ }) },
                onCreateCircle = { onCreateCircle(uiState.newCircleName, uiState.newCircleDurationDays.toInt(), { /* onSuccess */ }, { /* onError */ }) },
                onShowCreateCircleDialog = { homeViewModel.showCreateCircleDialog(it) },
                onShowJoinCircleDialog = { homeViewModel.showJoinCircleDialog(it) },
                onNewCircleNameChange = { homeViewModel.onNewCircleNameChange(it) },
                onNewCircleDurationChange = { homeViewModel.onNewCircleDurationChange(it) },
                onInviteCodeChange = { homeViewModel.onInviteCodeChange(it) }
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
    onInviteCodeChange: (String) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CircleApp") },
                actions = {
                    IconButton(onClick = { onShowCreateCircleDialog(true) }) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Circle")
                    }
                    IconButton(onClick = { onShowJoinCircleDialog(true) }) {
                        Icon(Icons.Default.CameraAlt, contentDescription = "Join Circle")
                    }
                }
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp)
        ) {
            Text("Your Circles", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(16.dp))

            if (uiState.circles.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No circles yet. Create or join one!")
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) { items(uiState.circles) { circle ->
                        val now = System.currentTimeMillis()
                        val closeAtMillis = circle.closeAt?.toDate()?.time ?: (now + TimeUnit.DAYS.toMillis(8))
                        val isClosed = circle.status == "closed" || closeAtMillis < now
                        val isExpiringSoon = !isClosed && (closeAtMillis - now) < TimeUnit.DAYS.toMillis(1)

                        val borderColor = when {
                            isExpiringSoon -> Color.Red.copy(alpha = 0.5f)
                            !isClosed -> Color.Green.copy(alpha = 0.5f)
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
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = circle.name,
                                    modifier = Modifier.padding(8.dp),
                                    textAlign = TextAlign.Center,
                                    style = TextStyle.Default.copy(fontSize = 24.sp),
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
        AlertDialog(
            onDismissRequest = { onShowJoinCircleDialog(false) },
            title = { Text("Join a Circle") },
            text = {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(250.dp)
                            .clip(MaterialTheme.shapes.medium)
                            .background(Color.Gray),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("QR Scanner Preview Here", color = Color.White)
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
