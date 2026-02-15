package com.example.circleapp.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.circleapp.ui.viewmodels.AuthMode
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
    val uiState by authViewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = if (uiState.mode == AuthMode.LOGIN) "Login" else "Create Account",
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = uiState.email,
            onValueChange = authViewModel::updateEmail,
            label = { Text("Email") },
            modifier = Modifier.fillMaxWidth()
        )

        OutlinedTextField(
            value = uiState.password,
            onValueChange = authViewModel::updatePassword,
            label = { Text("Password") },
            modifier = Modifier.fillMaxWidth(),
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val image = if (passwordVisible)
                    Icons.Filled.Visibility
                else Icons.Filled.VisibilityOff

                val description = if (passwordVisible) "Hide password" else "Show password"

                IconButton(onClick = {passwordVisible = !passwordVisible}){
                    Icon(imageVector  = image, description)
                }
            }
        )

        if (uiState.mode == AuthMode.SIGNUP) {
            OutlinedTextField(
                value = uiState.displayName,
                onValueChange = authViewModel::updateDisplayName,
                label = { Text("Name") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.username,
                onValueChange = authViewModel::updateUsername,
                label = { Text("Username") },
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = uiState.phone,
                onValueChange = authViewModel::updatePhone,
                label = { Text("Phone") },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (uiState.error.isNotBlank()) {
            Text(uiState.error, color = MaterialTheme.colorScheme.error)
        }

        Button(
            enabled = !uiState.isLoading,
            onClick = {
                if (uiState.mode == AuthMode.LOGIN) authViewModel.login(onAuthed)
                else authViewModel.signUp(onAuthed)
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.mode == AuthMode.LOGIN) "Login" else "Create Account")
        }

        TextButton(
            onClick = { authViewModel.toggleMode() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (uiState.mode == AuthMode.LOGIN) "Need an account? Sign up" else "Have an account? Login")
        }
    }
}
