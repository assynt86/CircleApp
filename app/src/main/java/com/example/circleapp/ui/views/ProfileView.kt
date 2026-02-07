package com.example.circleapp.ui.views

import androidx.compose.foundation.background
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circleapp.ui.viewmodels.ProfileViewModel

@Composable
fun ProfileView(profileViewModel: ProfileViewModel = viewModel()) {
    val name by profileViewModel.name
    val username by profileViewModel.username
    val email by profileViewModel.email
    val phone by profileViewModel.phone

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .padding(top = 96.dp, start = 16.dp, end = 16.dp, bottom = 96.dp) // Leave space for button
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Profile Picture
                Box(
                    modifier = Modifier
                        .size(100.dp)
                        .clip(CircleShape)
                        .background(Color.Gray),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Face,
                        contentDescription = "Profile Picture",
                        modifier = Modifier.size(60.dp),
                        tint = Color.White
                    )
                }

                Spacer(modifier = Modifier.width(16.dp))

                // Account Info
                Column {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { profileViewModel.name.value = it },
                        label = { Text("Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            EditableProfileRow(
                icon = Icons.Default.Person,
                label = "Username",
                value = username,
                onValueChange = {},
                readOnly = true
            )
            EditableProfileRow(
                icon = Icons.Default.Email,
                label = "Email",
                value = email,
                onValueChange = {},
                readOnly = true
            )
            EditableProfileRow(
                icon = Icons.Default.Phone,
                label = "Phone",
                value = phone,
                onValueChange = {},
                readOnly = true
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Action Buttons
            Button(
                onClick = { /* TODO: Implement Friends screen navigation */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Group, contentDescription = "Friends")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Friends")
            }
            Spacer(modifier = Modifier.height(8.dp))
            Button(
                onClick = { /* TODO: Implement Settings screen navigation */ },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(Icons.Default.Settings, contentDescription = "Settings")
                Spacer(modifier = Modifier.width(8.dp))
                Text("Settings")
            }
        }

        // Log Out Button
        Button(
            onClick = { /* TODO: Implement Log Out */ },
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(horizontal = 16.dp, vertical = 24.dp), // Adjust vertical padding to move
            colors = ButtonDefaults.buttonColors(containerColor = Color.Black, contentColor = Color.Red)
        ) {
            Icon(Icons.Default.Logout, contentDescription = "Log Out")
            Spacer(modifier = Modifier.width(8.dp))
            Text("Log Out")
        }
    }
}

@Composable
fun EditableProfileRow(
    icon: ImageVector,
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    readOnly: Boolean = false
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        leadingIcon = { Icon(imageVector = icon, contentDescription = null) },
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        readOnly = readOnly
    )
}
