package com.crcleapp.crcle.ui.views

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.SettingsViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onBack: () -> Unit,
    onReportBug: () -> Unit,
    onBlockedAccountsClick: () -> Unit,
    onAccountDeleted: () -> Unit,
    viewModel: SettingsViewModel = viewModel()
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
                        onClick = onBlockedAccountsClick
                    )
                }
                item {
                    SettingsClickableRow(
                        label = "Privacy Policy",
                        onClick = { /* Implement later */ }
                    )
                }

                item { Spacer(modifier = Modifier.height(16.dp)) }

                // HELP SECTION
                item { SettingsHeader("Help") }
                item {
                    SettingsClickableRow(
                        label = "Report a Bug",
                        onClick = onReportBug
                    )
                }

                item { Spacer(modifier = Modifier.height(32.dp)) }

                // ACCOUNT DELETION
                item {
                    Button(
                        onClick = { viewModel.showDeleteDialog() },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFEBEE), contentColor = Color.Red),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Delete Account", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                    }
                }
                
                item { Spacer(modifier = Modifier.height(32.dp)) }
            }
        }
    }

    if (uiState.showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { viewModel.hideDeleteDialog() },
            title = { Text("Delete Account?", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    Text(
                        "This action is permanent and will delete all your data. Please enter your password to confirm.",
                        fontFamily = LeagueSpartan
                    )
                    Spacer(Modifier.height(16.dp))
                    OutlinedTextField(
                        value = uiState.deletePassword,
                        onValueChange = { viewModel.updateDeletePassword(it) },
                        label = { Text("Password") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(),
                        isError = uiState.error != null,
                        supportingText = { uiState.error?.let { Text(it) } }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteAccount(onAccountDeleted) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    enabled = !uiState.isDeleting
                ) {
                    if (uiState.isDeleting) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.White, strokeWidth = 2.dp)
                    } else {
                        Text("Delete Permanently")
                    }
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.hideDeleteDialog() }, enabled = !uiState.isDeleting) {
                    Text("Cancel")
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
