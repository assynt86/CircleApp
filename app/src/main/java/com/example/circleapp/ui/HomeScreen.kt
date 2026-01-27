package com.example.circleapp.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun HomeScreen(
    onCreateCircle: (circleName: String) -> Unit,
    onJoinCircle: (inviteCode: String) -> Unit
) {
    var circleName by remember { mutableStateOf("") }
    var inviteCode by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
        Text("CircleApp", style = MaterialTheme.typography.headlineMedium)

        Spacer(Modifier.height(24.dp))

        // ---- Create circle section ----
        Text("Create a Circle", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = circleName,
            onValueChange = { circleName = it },
            label = { Text("Circle name") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onCreateCircle(circleName) },
            modifier = Modifier.fillMaxWidth(),
            enabled = circleName.isNotBlank()
        ) {
            Text("Create")
        }

        Spacer(Modifier.height(32.dp))

        // ---- Join circle section ----
        Text("Join a Circle", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = inviteCode,
            onValueChange = { inviteCode = it },
            label = { Text("Invite code") },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        Button(
            onClick = { onJoinCircle(inviteCode) },
            modifier = Modifier.fillMaxWidth(),
            enabled = inviteCode.isNotBlank()
        ) {
            Text("Join")
        }
    }
}
