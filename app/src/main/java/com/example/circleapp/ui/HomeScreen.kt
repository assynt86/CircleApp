package com.example.circleapp.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.circleapp.data.Circle
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    circles: List<Circle>,
    onCreateCircle: (circleName: String, durationDays: Int) -> Unit,
    onJoinCircle: (inviteCode: String) -> Unit,
    onCircleClick: (circleId: String) -> Unit
) {
    var showCreateDialog by remember { mutableStateOf(false) }
    var showJoinDialog by remember { mutableStateOf(false) }

    var circleName by remember { mutableStateOf("") }
    var durationSliderPosition by remember { mutableStateOf(1f) }
    var inviteCode by remember { mutableStateOf("") }
    val db = remember { FirebaseFirestore.getInstance() }
    val allCircles = remember { mutableStateOf(emptyList<Circle>()) }

    LaunchedEffect(key1 = circles) {
        val currentUser = FirebaseAuth.getInstance().currentUser
        if (currentUser != null) {
            db.collection("circles").addSnapshotListener { snapshot, _ ->
                snapshot?.let { querySnapshot ->
                    val userCircles = mutableListOf<Circle>()
                    for (doc in querySnapshot.documents) {
                        val membersCollection = doc.reference.collection("members")
                        membersCollection.addSnapshotListener { membersSnapshot, _ ->
                            membersSnapshot?.let { membersQuerySnapshot ->
                                if (membersQuerySnapshot.documents.any { it.id == currentUser.uid }) {
                                    val circle = doc.toObject(Circle::class.java)
                                    if (circle != null) {
                                        userCircles.add(circle)
                                    }
                                }
                                allCircles.value = circles + userCircles.filterNot { newCircle -> circles.any { it.id == newCircle.id } }
                            }
                        }
                    }
                }
            }
        }
    }


    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("CircleApp") },
                actions = {
                    IconButton(onClick = { showCreateDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "Create Circle")
                    }
                    IconButton(onClick = { showJoinDialog = true }) {
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

            if (allCircles.value.isEmpty()) {
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
                    items(allCircles.value) { circle ->
                        Card(
                            shape = CircleShape,
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
                                    style = MaterialTheme.typography.bodyLarge,
                                    modifier = Modifier.padding(8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create a Circle") },
            text = {
                Column {
                    OutlinedTextField(
                        value = circleName,
                        onValueChange = { circleName = it },
                        label = { Text("Circle name") },
                        singleLine = true
                    )
                    Spacer(Modifier.height(24.dp))
                    Text("Duration: ${durationSliderPosition.toInt()} days")
                    Slider(
                        value = durationSliderPosition,
                        onValueChange = { durationSliderPosition = it },
                        valueRange = 1f..7f,
                        steps = 5
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onCreateCircle(circleName, durationSliderPosition.toInt())
                        showCreateDialog = false
                        circleName = ""
                        durationSliderPosition = 1f
                    },
                    enabled = circleName.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showJoinDialog) {
        AlertDialog(
            onDismissRequest = { showJoinDialog = false },
            title = { Text("Join a Circle") },
            text = {
                Column {
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
                    OutlinedTextField(
                        value = inviteCode,
                        onValueChange = { inviteCode = it },
                        label = { Text("Or enter code manually") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onJoinCircle(inviteCode)
                        showJoinDialog = false
                        inviteCode = ""
                    },
                    enabled = inviteCode.isNotBlank()
                ) {
                    Text("Join")
                }
            },
            dismissButton = {
                TextButton(onClick = { showJoinDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}