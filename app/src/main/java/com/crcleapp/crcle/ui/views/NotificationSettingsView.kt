package com.crcleapp.crcle.ui.views

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.NotificationSettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotificationSettingsView(
    onBack: () -> Unit,
    viewModel: NotificationSettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setNotificationsEnabled(true)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Notifications",
                        fontFamily = LeagueSpartan,
                        fontWeight = FontWeight.Bold,
                        fontSize = 32.sp
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
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
                .padding(horizontal = 24.dp)
        ) {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                item {
                    SettingsToggleRow(
                        label = "Enable All Notifications",
                        checked = uiState.notificationsEnabled,
                        onCheckedChange = { enabled ->
                            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    viewModel.setNotificationsEnabled(true)
                                }
                            } else {
                                viewModel.setNotificationsEnabled(enabled)
                            }
                        }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                item {
                    SettingsHeader("Events")
                }

                item {
                    SettingsToggleRow(
                        label = "Friend Requests",
                        checked = uiState.friendRequestsEnabled,
                        onCheckedChange = { viewModel.setFriendRequestsEnabled(it) },
                        enabled = uiState.notificationsEnabled
                    )
                }

                item {
                    SettingsToggleRow(
                        label = "Friend Request Accepted",
                        checked = uiState.friendRequestAcceptedEnabled,
                        onCheckedChange = { viewModel.setFriendRequestAcceptedEnabled(it) },
                        enabled = uiState.notificationsEnabled
                    )
                }

                item {
                    SettingsToggleRow(
                        label = "Circle Invites",
                        checked = uiState.circleInvitesEnabled,
                        onCheckedChange = { viewModel.setCircleInvitesEnabled(it) },
                        enabled = uiState.notificationsEnabled
                    )
                }

                item {
                    SettingsToggleRow(
                        label = "New Photos",
                        checked = uiState.newPhotosEnabled,
                        onCheckedChange = { viewModel.setNewPhotosEnabled(it) },
                        enabled = uiState.notificationsEnabled
                    )
                }
            }
        }
    }
}
