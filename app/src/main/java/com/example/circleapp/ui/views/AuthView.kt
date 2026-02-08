package com.example.circleapp.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circleapp.ui.viewmodels.AuthViewModel

/**
 * AuthView:
 * - Login + Sign up UI
 * - On success -> calls onAuthed()
 */
@Composable
fun AuthView(
    authViewModel: AuthViewModel = viewModel(),
    onAuthed: () -> Unit
) {
    var mode by remember { mutableStateOf("login") } // "login" or "signup"
    val s = authViewModel.state

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (mode == "login") "Login" else "Create Account",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = s.email,
            onValueChange = authViewModel::updateEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = s.password,
            onValueChange = authViewModel::updatePassword,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth()
        )

        if (mode == "signup") {
            OutlinedTextField(
                value = s.displayName,
                onValueChange = authViewModel::updateDisplayName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = s.username,
                onValueChange = authViewModel::updateUsername,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = s.phone,
                onValueChange = authViewModel::updatePhone,
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (s.error.isNotBlank()) {
            Text(s.error, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !s.isLoading,
            onClick = {
                if (mode == "login") authViewModel.login(onAuthed)
                else authViewModel.signUp(onAuthed)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mode == "login") "Login" else "Create Account")
        }

        TextButton(
            onClick = { mode = if (mode == "login") "signup" else "login" },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (mode == "login") "Need an account? Sign up" else "Have an account? Login")
        }
    }
}
