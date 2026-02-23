package com.crcleapp.crcle.ui.views

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.FriendsViewModel
import com.crcleapp.crcle.ui.viewmodels.FriendRequestWithUser
import com.crcleapp.crcle.data.UserProfile
import com.crcleapp.crcle.data.CircleInvite

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FriendsView(
    onBack: () -> Unit,
    initialTab: Int = 0,
    viewModel: FriendsViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        if (initialTab != 0) {
            viewModel.setSelectedTab(initialTab)
        }
    }

    LaunchedEffect(uiState.message) {
        uiState.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.clearMessage()
        }
    }

    if (uiState.showCircleSelector != null) {
        Dialog(onDismissRequest = { viewModel.setShowCircleSelector(null) }) {
            Card(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Add ${uiState.showCircleSelector?.username} to Circle", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    if (uiState.userCircles.isEmpty()) {
                        Text("No circles found", fontFamily = LeagueSpartan)
                    } else {
                        LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                            items(uiState.userCircles) { circle ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { viewModel.addFriendToCircle(uiState.showCircleSelector!!.uid, circle.id) }
                                        .padding(vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(Icons.Default.Circle, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(24.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Text(circle.name, fontFamily = LeagueSpartan, fontSize = 18.sp)
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    TextButton(onClick = { viewModel.setShowCircleSelector(null) }, modifier = Modifier.align(Alignment.End)) {
                        Text("Cancel", fontFamily = LeagueSpartan)
                    }
                }
            }
        }
    }

    if (uiState.showRemoveConfirmation != null) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowRemoveConfirmation(null) },
            title = { Text("Remove Friend", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to remove ${uiState.showRemoveConfirmation?.username} from your friends?", fontFamily = LeagueSpartan) },
            confirmButton = {
                TextButton(onClick = { viewModel.removeFriend(uiState.showRemoveConfirmation!!.uid) }) {
                    Text("Remove", color = Color.Red, fontFamily = LeagueSpartan)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowRemoveConfirmation(null) }) {
                    Text("Cancel", fontFamily = LeagueSpartan)
                }
            }
        )
    }

    if (uiState.showBlockConfirmation != null) {
        AlertDialog(
            onDismissRequest = { viewModel.setShowBlockConfirmation(null) },
            title = { Text("Block User", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to block ${uiState.showBlockConfirmation?.username}? They will be removed from your friends and you won't see each other's posts.", fontFamily = LeagueSpartan) },
            confirmButton = {
                TextButton(onClick = { viewModel.blockUser(uiState.showBlockConfirmation!!.uid) }) {
                    Text("Block", color = Color.Red, fontFamily = LeagueSpartan)
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.setShowBlockConfirmation(null) }) {
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
                            text = "Friends",
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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
            )
        }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .padding(paddingValues)
                .fillMaxSize()
                .padding(horizontal = 24.dp)
        ) {
            // Search Bar
            OutlinedTextField(
                value = uiState.searchText,
                onValueChange = { viewModel.onSearchTextChange(it) },
                label = { Text("Search by username", fontFamily = LeagueSpartan) },
                modifier = Modifier.fillMaxWidth(),
                shape = CircleShape,
                singleLine = true,
                trailingIcon = {
                    if (uiState.searchText.isNotEmpty()) {
                        IconButton(onClick = { viewModel.sendFriendRequest() }) {
                            Icon(Icons.Default.PersonAdd, contentDescription = "Add Friend")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.sendFriendRequest() })
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Tabs
            TabRow(
                selectedTabIndex = uiState.selectedTab,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = uiState.selectedTab == 0,
                    onClick = { viewModel.setSelectedTab(0) },
                    text = { Text("Friends", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) }
                )
                Tab(
                    selected = uiState.selectedTab == 1,
                    onClick = { viewModel.setSelectedTab(1) },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.incomingRequests.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Pending", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                        }
                    }
                )
                Tab(
                    selected = uiState.selectedTab == 2,
                    onClick = { viewModel.setSelectedTab(2) },
                    text = { 
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (uiState.circleInvites.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(Color.Red)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                            }
                            Text("Invites", fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold)
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Filtered Lists
            val filteredFriends = uiState.friends.filter {
                it.username.contains(uiState.searchText, ignoreCase = true) ||
                it.displayName.contains(uiState.searchText, ignoreCase = true)
            }.take(50)
            
            val filteredIncoming = uiState.incomingRequests.filter {
                it.user.username.contains(uiState.searchText, ignoreCase = true)
            }
            val filteredOutgoing = uiState.outgoingRequests.filter {
                it.user.username.contains(uiState.searchText, ignoreCase = true)
            }

            LazyColumn(modifier = Modifier.weight(1f)) {
                if (uiState.selectedTab == 0) {
                    items(filteredFriends) { friend ->
                        FriendItem(
                            user = friend,
                            onAddToCircle = { viewModel.setShowCircleSelector(friend) },
                            onRemove = { viewModel.setShowRemoveConfirmation(friend) },
                            onBlock = { viewModel.setShowBlockConfirmation(friend) }
                        )
                    }
                } else if (uiState.selectedTab == 1) {
                    item {
                        if (filteredIncoming.isNotEmpty()) {
                            Text(
                                "Incoming",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(filteredIncoming) { request ->
                        IncomingRequestItem(
                            request = request,
                            onAccept = { viewModel.acceptRequest(request.request) },
                            onDecline = { viewModel.declineRequest(request.request.id) },
                            onBlock = { viewModel.setShowBlockConfirmation(request.user) }
                        )
                    }
                    item {
                        if (filteredOutgoing.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "Outgoing",
                                style = MaterialTheme.typography.labelLarge,
                                color = Color.Gray,
                                modifier = Modifier.padding(vertical = 8.dp)
                            )
                        }
                    }
                    items(filteredOutgoing) { request ->
                        OutgoingRequestItem(
                            request = request,
                            onCancel = { viewModel.declineRequest(request.request.id) }
                        )
                    }
                } else if (uiState.selectedTab == 2) {
                    items(uiState.circleInvites) { invite ->
                        CircleInviteItem(
                            invite = invite,
                            onAccept = { viewModel.respondToCircleInvite(invite, true) },
                            onDecline = { viewModel.respondToCircleInvite(invite, false) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun FriendItem(
    user: UserProfile,
    onAddToCircle: () -> Unit,
    onRemove: () -> Unit,
    onBlock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        ListItem(
            modifier = Modifier.clickable { showMenu = true },
            headlineContent = { Text(user.username, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            supportingContent = { Text(user.displayName, fontFamily = LeagueSpartan) },
            leadingContent = { UserAvatar(user.photoUrl) },
            trailingContent = {
                Icon(Icons.Default.MoreVert, contentDescription = "Options")
            }
        )
        
        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Add to Circle", fontFamily = LeagueSpartan) },
                onClick = {
                    showMenu = false
                    onAddToCircle()
                },
                leadingIcon = { Icon(Icons.Default.AddCircle, contentDescription = null) }
            )
            DropdownMenuItem(
                text = { Text("Remove Friend", fontFamily = LeagueSpartan) },
                onClick = {
                    showMenu = false
                    onRemove()
                },
                leadingIcon = { Icon(Icons.Default.PersonRemove, contentDescription = null, tint = Color.Red) }
            )
            DropdownMenuItem(
                text = { Text("Block", fontFamily = LeagueSpartan) },
                onClick = {
                    showMenu = false
                    onBlock()
                },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color.Red) }
            )
        }
    }
}

@Composable
fun IncomingRequestItem(
    request: FriendRequestWithUser,
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    onBlock: () -> Unit
) {
    var showMenu by remember { mutableStateOf(false) }

    Box {
        ListItem(
            headlineContent = { Text(request.user.username, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
            supportingContent = { Text("Wants to be your friend", fontFamily = LeagueSpartan) },
            leadingContent = { UserAvatar(request.user.photoUrl) },
            trailingContent = {
                Row {
                    IconButton(onClick = onAccept) {
                        Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF4CAF50))
                    }
                    IconButton(onClick = { showMenu = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = "Options")
                    }
                }
            }
        )

        DropdownMenu(
            expanded = showMenu,
            onDismissRequest = { showMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Decline", fontFamily = LeagueSpartan) },
                onClick = {
                    showMenu = false
                    onDecline()
                },
                leadingIcon = { Icon(Icons.Default.Close, contentDescription = null, tint = Color.Red) }
            )
            DropdownMenuItem(
                text = { Text("Block", fontFamily = LeagueSpartan) },
                onClick = {
                    showMenu = false
                    onBlock()
                },
                leadingIcon = { Icon(Icons.Default.Block, contentDescription = null, tint = Color.Red) }
            )
        }
    }
}

@Composable
fun OutgoingRequestItem(
    request: FriendRequestWithUser,
    onCancel: () -> Unit
) {
    ListItem(
        headlineContent = { Text(request.user.username, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
        supportingContent = { Text("Request sent", fontFamily = LeagueSpartan) },
        leadingContent = { UserAvatar(request.user.photoUrl) },
        trailingContent = {
            IconButton(onClick = onCancel) {
                Icon(Icons.Default.Cancel, contentDescription = "Cancel", tint = Color.Gray)
            }
        }
    )
}

@Composable
fun CircleInviteItem(
    invite: CircleInvite,
    onAccept: () -> Unit,
    onDecline: () -> Unit
) {
    ListItem(
        headlineContent = { Text(invite.circleName, fontFamily = LeagueSpartan, fontWeight = FontWeight.Bold) },
        supportingContent = { Text("Invited by ${invite.inviterName}", fontFamily = LeagueSpartan) },
        leadingContent = {
            if (!invite.circleBackgroundUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = invite.circleBackgroundUrl,
                    contentDescription = null,
                    modifier = Modifier.size(40.dp).clip(CircleShape),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(Icons.Default.Circle, contentDescription = null, modifier = Modifier.size(40.dp), tint = Color.Gray)
            }
        },
        trailingContent = {
            Row {
                IconButton(onClick = onAccept) {
                    Icon(Icons.Default.Check, contentDescription = "Accept", tint = Color(0xFF4CAF50))
                }
                IconButton(onClick = onDecline) {
                    Icon(Icons.Default.Close, contentDescription = "Decline", tint = Color.Red)
                }
            }
        }
    )
}

@Composable
fun UserAvatar(photoUrl: String?) {
    if (!photoUrl.isNullOrEmpty()) {
        AsyncImage(
            model = photoUrl,
            contentDescription = null,
            modifier = Modifier.size(40.dp).clip(CircleShape),
            contentScale = ContentScale.Crop
        )
    } else {
        Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(40.dp))
    }
}