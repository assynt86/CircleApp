package com.example.circleapp.ui.views

import android.app.Application
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.example.circleapp.data.UserProfile
import com.example.circleapp.ui.theme.LeagueSpartan
import com.example.circleapp.ui.viewmodels.CircleSettingsViewModel
import com.example.circleapp.ui.viewmodels.CircleSettingsViewModelFactory

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CircleSettingsView(
    circleId: String,
    onBack: () -> Unit,
    onDeleted: () -> Unit
) {
    val context = LocalContext.current
    val application = context.applicationContext as Application
    val viewModel: CircleSettingsViewModel = viewModel(
        factory = CircleSettingsViewModelFactory(application, circleId)
    )
    val uiState by viewModel.uiState.collectAsState()

    var showEditNameDialog by remember { mutableStateOf(false) }
    var newCircleName by remember { mutableStateOf("") }
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberUsernameToAdd by remember { mutableStateOf("") }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let { viewModel.updateBackground(it) }
    }

    LaunchedEffect(uiState.error, uiState.successMessage) {
        uiState.error?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
        uiState.successMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessages()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Circle Settings", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        if (uiState.isLoading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator()
            }
        }

        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Circle Info Section
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.medium
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = uiState.circleInfo?.name ?: "Loading...",
                                style = MaterialTheme.typography.headlineMedium,
                                fontFamily = LeagueSpartan,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "Invite Code: ${uiState.circleInfo?.inviteCode ?: "..."}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontFamily = LeagueSpartan
                            )
                        }
                        if (uiState.isAdmin) {
                            IconButton(onClick = { 
                                newCircleName = uiState.circleInfo?.name ?: ""
                                showEditNameDialog = true 
                            }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit Name")
                            }
                        }
                    }

                    Spacer(Modifier.height(24.dp))

                    // Circular Cover Image
                    Text("Cover Image", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                    Text("This image appears in the circle list on the Home screen.", style = MaterialTheme.typography.labelSmall, fontFamily = LeagueSpartan, color = Color.Gray)
                    Spacer(Modifier.height(12.dp))
                    
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(CircleShape)
                            .background(Color.LightGray)
                            .clickable(enabled = uiState.isAdmin) { photoPickerLauncher.launch("image/*") },
                        contentAlignment = Alignment.Center
                    ) {
                        if (uiState.circleInfo?.backgroundUrl != null) {
                            AsyncImage(
                                model = uiState.circleInfo?.backgroundUrl,
                                contentDescription = "Circle Background",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(48.dp))
                        }
                        if (uiState.isAdmin) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color.Black.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.BottomCenter
                            ) {
                                Text(
                                    "Edit",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontFamily = LeagueSpartan,
                                    modifier = Modifier.padding(bottom = 8.dp)
                                )
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))

            // Members Section
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Members", style = MaterialTheme.typography.titleLarge, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                if (uiState.isAdmin) {
                    IconButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.PersonAdd, contentDescription = "Add Member")
                    }
                }
            }

            LazyColumn(modifier = Modifier.weight(1f).fillMaxWidth()) {
                items(uiState.members) { member ->
                    ListItem(
                        headlineContent = { Text(member.username, fontFamily = LeagueSpartan) },
                        supportingContent = { Text(member.displayName, fontFamily = LeagueSpartan) },
                        modifier = Modifier.clickable { viewModel.onMemberSelected(member) },
                        leadingContent = {
                            if (member.photoUrl.isNotEmpty()) {
                                AsyncImage(
                                    model = member.photoUrl,
                                    contentDescription = null,
                                    modifier = Modifier.size(40.dp).clip(CircleShape),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
                            }
                        },
                        trailingContent = {
                             if (member.uid == uiState.circleInfo?.ownerUid) {
                                Text("Admin", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    )
                    HorizontalDivider()
                }
            }

            // Actions
            Spacer(Modifier.height(16.dp))
            if (uiState.isAdmin) {
                Button(
                    onClick = { viewModel.setShowDeleteConfirmation(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.Delete, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Delete Circle", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                }
            } else {
                Button(
                    onClick = { viewModel.setShowLeaveConfirmation(true) },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                    shape = MaterialTheme.shapes.medium
                ) {
                    Icon(Icons.Default.ExitToApp, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Leave Circle", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                }
            }
        }
    }

    // Dialogs
    if (uiState.showUserActionDialog) {
        uiState.selectedMember?.let { member ->
            UserActionDialog(
                member = member,
                isAdmin = uiState.isAdmin,
                isFriend = uiState.friends.any { it.uid == member.uid },
                onDismiss = { viewModel.dismissUserActionDialog() },
                onReport = { viewModel.onReportSelected() },
                onBlock = { viewModel.blockSelectedUser() },
                onAddFriend = { viewModel.sendFriendRequestToSelectedUser() },
                onKick = { viewModel.kickMember(member.uid) }
            )
        }
    }

    if (uiState.showReportDialog) {
        ReportUserDialog(
            onDismiss = { viewModel.dismissReportDialog() },
            onReport = { reason -> viewModel.reportUser(reason) }
        )
    }

    if (showEditNameDialog) {
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = { Text("Edit Circle Name", fontFamily = LeagueSpartan) },
            text = {
                OutlinedTextField(
                    value = newCircleName,
                    onValueChange = { newCircleName = it },
                    label = { Text("Name") }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.updateCircleName(newCircleName)
                    showEditNameDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Add Members", fontFamily = LeagueSpartan) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = memberUsernameToAdd,
                        onValueChange = { memberUsernameToAdd = it },
                        label = { Text("Add by Username") },
                        modifier = Modifier.fillMaxWidth()
                    )

                    Text("Or select from Friends:", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold, fontSize = 14.sp)

                    if (uiState.friends.isEmpty()) {
                        Text("No friends to show", fontFamily = LeagueSpartan, color = Color.Gray, fontSize = 12.sp)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 240.dp)) {
                            items(uiState.friends) { friend ->
                                val isSelected = uiState.selectedFriendUids.contains(friend.uid)
                                val isAlreadyMember = uiState.members.any { it.uid == friend.uid }

                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable(enabled = !isAlreadyMember) { viewModel.toggleFriendSelection(friend.uid) }
                                        .padding(vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isSelected || isAlreadyMember,
                                        onCheckedChange = { if (!isAlreadyMember) viewModel.toggleFriendSelection(friend.uid) },
                                        enabled = !isAlreadyMember
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = friend.username,
                                        fontFamily = LeagueSpartan,
                                        color = if (isAlreadyMember) Color.Gray else Color.Unspecified
                                    )
                                    if (isAlreadyMember) {
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("(In Circle)", fontFamily = LeagueSpartan, fontSize = 10.sp, color = Color.Gray)
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addMembers(memberUsernameToAdd)
                    showAddMemberDialog = false
                    memberUsernameToAdd = ""
                }) { Text("Add") }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowDeleteConfirmation(false) },
            title = { Text("Delete Circle?", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = { Text("This action cannot be undone. All photos and data will be lost.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.deleteCircle(onDeleted) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowDeleteConfirmation(false) }) { Text("Cancel") }
            }
        )
    }

    if (uiState.showLeaveConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowLeaveConfirmation(false) },
            title = { Text("Leave Circle?", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to leave this circle? You will need an invite to join again.") },
            confirmButton = {
                Button(
                    onClick = { viewModel.leaveCircle(onDeleted) },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Gray)
                ) { Text("Leave") }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowLeaveConfirmation(false) }) { Text("Cancel") }
            }
        )
    }
}

@Composable
fun UserActionDialog(
    member: UserProfile,
    isAdmin: Boolean,
    isFriend: Boolean,
    onDismiss: () -> Unit,
    onReport: () -> Unit,
    onBlock: () -> Unit,
    onAddFriend: () -> Unit,
    onKick: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(member.username, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
        text = {
            LazyColumn {
                item { TextButton(onClick = onReport) { Text("Report User") } }
                item { TextButton(onClick = onBlock) { Text("Block User") } }
                if (!isFriend) {
                    item { TextButton(onClick = onAddFriend) { Text("Send Friend Request") } }
                }
                if (isAdmin) {
                    item { TextButton(onClick = onKick) { Text("Kick from Circle", color = Color.Red) } }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun ReportUserDialog(
    onDismiss: () -> Unit,
    onReport: (String) -> Unit
) {
    val reportReasons = listOf(
        "Spam or Scams",
        "Nudity or Sexual Activity",
        "Hate Speech or Symbols",
        "Bullying or Harassment",
        "False Information",
        "Other"
    )
    var selectedReason by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Report User", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                Text("Select a reason for reporting this user:", fontFamily = LeagueSpartan, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(16.dp))
                reportReasons.forEach { reason ->
                    Row(Modifier
                        .fillMaxWidth()
                        .clickable { selectedReason = reason }
                        .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(
                            selected = (selectedReason == reason),
                            onClick = { selectedReason = reason }
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(reason)
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { selectedReason?.let { onReport(it) } },
                enabled = selectedReason != null
            ) { Text("Submit Report") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}