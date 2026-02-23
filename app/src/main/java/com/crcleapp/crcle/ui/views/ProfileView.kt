package com.crcleapp.crcle.ui.views

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.ProfileViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileView(
    onLogout: () -> Unit,
    onFriendsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
){
    val uiState by profileViewModel.uiState.collectAsState()
    var showFullScreenPhoto by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            profileViewModel.uploadProfilePicture(it)
        }
    }

    if (showFullScreenPhoto && uiState.photoUrl.isNotEmpty()) {
        Dialog(
            onDismissRequest = { showFullScreenPhoto = false },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { showFullScreenPhoto = false },
                contentAlignment = Alignment.TopCenter
            ) {
                AsyncImage(
                    model = uiState.photoUrl,
                    contentDescription = "Full Screen Profile Picture",
                    modifier = Modifier
                        .padding(top = 100.dp)
                        .fillMaxWidth(0.9f)
                        .aspectRatio(1f)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            }
        }
    }

    if (uiState.showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { profileViewModel.setShowEditNameDialog(false) },
            title = { Text("Edit Name", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = {
                OutlinedTextField(
                    value = uiState.editedName,
                    onValueChange = { profileViewModel.updateEditedName(it) },
                    label = { Text("Name", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                TextButton(onClick = { profileViewModel.saveName() }) {
                    Text("Save", fontFamily = LeagueSpartan)
                }
            },
            dismissButton = {
                TextButton(onClick = { profileViewModel.setShowEditNameDialog(false) }) {
                    Text("Cancel", fontFamily = LeagueSpartan)
                }
            }
        )
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
                            text = "Profile",
                            fontFamily = LeagueSpartan,
                            fontWeight = FontWeight.Bold,
                            fontSize = 40.sp,
                            modifier = Modifier.padding(top = 16.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { paddingValues ->
        Box(modifier = Modifier
            .fillMaxSize()
            .padding(paddingValues)) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(modifier = Modifier.height(20.dp))

                Box(
                    modifier = Modifier
                        .size(160.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray)
                        .clickable(enabled = uiState.photoUrl.isNotEmpty()) {
                            showFullScreenPhoto = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    if (uiState.photoUrl.isNotEmpty()) {
                        AsyncImage(
                            model = uiState.photoUrl,
                            contentDescription = "Profile Picture",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Profile Placeholder",
                            modifier = Modifier.size(80.dp),
                            tint = Color.White
                        )
                    }
                    
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .align(Alignment.BottomCenter)
                            .background(Color.Black.copy(alpha = 0.4f))
                            .clickable { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "Edit",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontFamily = LeagueSpartan
                        )
                    }
                }

                Spacer(modifier = Modifier.height(60.dp))

                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(32.dp)
                ) {
                    EditableInfoLabel(
                        label = "Name",
                        value = uiState.name,
                        onEditClick = { profileViewModel.setShowEditNameDialog(true) }
                    )
                    
                    InfoLabel(label = "Username", value = uiState.username)

                    CensoredInfoLabel(
                        label = "Email",
                        value = uiState.email,
                        isVisible = uiState.isEmailVisible,
                        onToggleVisibility = { profileViewModel.toggleEmailVisibility() }
                    )

                    CensoredInfoLabel(
                        label = "Phone",
                        value = uiState.phone,
                        isVisible = uiState.isPhoneVisible,
                        onToggleVisibility = { profileViewModel.togglePhoneVisibility() }
                    )
                }

                Spacer(modifier = Modifier.weight(1f))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 32.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Button(
                        onClick = onFriendsClick,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.Default.Group, contentDescription = "Friends")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Friends", fontFamily = LeagueSpartan, fontSize = 20.sp)
                    }
                    
                    Button(
                        onClick = onSettingsClick,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray)
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Settings")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Settings", fontFamily = LeagueSpartan, fontSize = 20.sp)
                    }
                    
                    Button(
                        onClick = onLogout,
                        modifier = Modifier.fillMaxWidth().height(60.dp),
                        shape = MaterialTheme.shapes.medium,
                        colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.White)
                    ) {
                        Icon(Icons.Default.Logout, contentDescription = "Log Out", tint = Color.Red)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Log Out", fontFamily = LeagueSpartan, color = Color.Red, fontSize = 20.sp)
                    }
                }
            }
            
            if (uiState.isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
fun InfoLabel(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            fontFamily = LeagueSpartan,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = LeagueSpartan,
            fontSize = 22.sp
        )
    }
}

@Composable
fun EditableInfoLabel(label: String, value: String, onEditClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            fontFamily = LeagueSpartan,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = value,
            fontFamily = LeagueSpartan,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onEditClick) {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = "Edit",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun CensoredInfoLabel(
    label: String,
    value: String,
    isVisible: Boolean,
    onToggleVisibility: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$label: ",
            fontFamily = LeagueSpartan,
            fontWeight = FontWeight.Bold,
            fontSize = 22.sp
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = if (isVisible) value else "•".repeat(value.length.coerceAtLeast(8)),
            fontFamily = LeagueSpartan,
            fontSize = 22.sp,
            modifier = Modifier.weight(1f)
        )
        IconButton(onClick = onToggleVisibility) {
            Icon(
                imageVector = if (isVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                contentDescription = if (isVisible) "Hide" else "Show",
                modifier = Modifier.size(24.dp)
            )
        }
    }
}