package com.example.circleapp.ui.views

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
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
import com.example.circleapp.ui.theme.LeagueSpartan
import com.example.circleapp.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var showBlockedAccountsDialog by remember { mutableStateOf(false) }
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
                modifier = Modifier.height(120.dp),
                title = {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .padding(start = 8.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = "Settings",
                            fontFamily = LeagueSpartan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.padding(top = 16.dp)) {
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
                // GENERAL SECTION
                item { SettingsHeader("General") }
                item {
                    SettingsToggleRow(
                        label = "Auto-save photos to gallery",
                        checked = uiState.isAutoSaveEnabled,
                        onCheckedChange = { viewModel.toggleAutoSave(it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        label = "Auto-accept circle invites",
                        checked = uiState.autoAcceptInvites,
                        onCheckedChange = { viewModel.setAutoAcceptInvites(it) }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // NOTIFICATIONS SECTION
                item { SettingsHeader("Notifications") }
                item {
                    SettingsToggleRow(
                        label = "Enable Notifications",
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

                // THEME SECTION
                item { SettingsHeader("Theme") }
                item {
                    SettingsToggleRow(
                        label = "Match System Theme",
                        checked = uiState.useSystemTheme,
                        onCheckedChange = { viewModel.setUseSystemTheme(it) }
                    )
                }
                item {
                    SettingsToggleRow(
                        label = "Dark Mode",
                        checked = uiState.isDarkMode,
                        onCheckedChange = { viewModel.setDarkMode(it) },
                        enabled = !uiState.useSystemTheme
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // PRIVACY SECTION
                item { SettingsHeader("Privacy") }
                item {
                    SettingsClickableRow(
                        label = "Blocked Accounts",
                        onClick = { showBlockedAccountsDialog = true }
                    )
                }
                item {
                    SettingsClickableRow(
                        label = "Privacy Policy",
                        onClick = { /* Implement later */ }
                    )
                }
            }
        }
    }

    if (showBlockedAccountsDialog) {
        AlertDialog(
            onDismissRequest = { showBlockedAccountsDialog = false },
            title = { Text("Blocked Accounts", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                if (uiState.blockedUsers.isEmpty()) {
                    Text("No blocked accounts", fontFamily = LeagueSpartan)
                } else {
                    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                        items(uiState.blockedUsers) { user ->
                            ListItem(
                                headlineContent = { Text(user.username, fontFamily = LeagueSpartan) },
                                trailingContent = {
                                    TextButton(onClick = { viewModel.unblockUser(user.uid) }) {
                                        Text("Unblock", fontFamily = LeagueSpartan)
                                    }
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBlockedAccountsDialog = false }) {
                    Text("Close", fontFamily = LeagueSpartan)
                }
            }
        )
    }
}

@Composable
fun SettingsHeader(text: String) {
    Text(
        text = text,
        fontFamily = LeagueSpartan,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(vertical = 8.dp)
    )
}

@Composable
fun SettingsToggleRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .alpha(if (enabled) 1f else 0.5f),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = LeagueSpartan,
            fontSize = 18.sp
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled
        )
    }
}

@Composable
fun SettingsClickableRow(
    label: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            fontFamily = LeagueSpartan,
            fontSize = 18.sp
        )
        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = Color.Gray
        )
    }
}
