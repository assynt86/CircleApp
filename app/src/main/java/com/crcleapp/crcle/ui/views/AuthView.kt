package com.crcleapp.crcle.ui.views

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.crcleapp.crcle.ui.theme.LeagueSpartan
import com.crcleapp.crcle.ui.viewmodels.AuthMode
import com.crcleapp.crcle.ui.viewmodels.AuthViewModel

/**
 * AuthView:
 * - Login + Sign up UI
 * - On success -> calls onAuthed()
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthView(
    authViewModel: AuthViewModel = viewModel(),
    onAuthed: () -> Unit
) {
    val uiState by authViewModel.uiState.collectAsState()
    var passwordVisible by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    val onAuthAction = {
        if (uiState.mode == AuthMode.LOGIN) authViewModel.login(onAuthed)
        else authViewModel.signUp(onAuthed)
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
                            text = if (uiState.mode == AuthMode.LOGIN) "Login" else "Create Account",
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(horizontal = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(20.dp))

            // IDENTIFIER (Login) or EMAIL (Signup)
            if (uiState.mode == AuthMode.LOGIN) {
                OutlinedTextField(
                    value = uiState.identifier,
                    onValueChange = authViewModel::updateIdentifier,
                    label = { Text("Email, Username or Phone", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )
            } else {
                OutlinedTextField(
                    value = uiState.email,
                    onValueChange = authViewModel::updateEmail,
                    label = { Text("Email", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Email,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )
            }

            // PASSWORD
            OutlinedTextField(
                value = uiState.password,
                onValueChange = { newValue ->
                    // Sanitize password: Block potentially injectable/dangerous characters
                    val sanitized = newValue.filter { char ->
                        char != '\'' && char != '"' && char != ';' && char != '\\' && char != '-'
                    }
                    authViewModel.updatePassword(sanitized)
                },
                label = { Text("Password", fontFamily = LeagueSpartan) },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                shape = MaterialTheme.shapes.medium,
                trailingIcon = {
                    val image = if (passwordVisible)
                        Icons.Filled.Visibility
                    else Icons.Filled.VisibilityOff

                    val description = if (passwordVisible) "Hide password" else "Show password"

                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                        Icon(imageVector = image, description)
                    }
                },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Password,
                    imeAction = if (uiState.mode == AuthMode.LOGIN) ImeAction.Done else ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) },
                    onDone = { 
                        focusManager.clearFocus()
                        onAuthAction()
                    }
                ),
                singleLine = true
            )

            if (uiState.mode == AuthMode.SIGNUP) {
                // DISPLAY NAME
                OutlinedTextField(
                    value = uiState.displayName,
                    onValueChange = authViewModel::updateDisplayName,
                    label = { Text("Name", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )

                // USERNAME
                OutlinedTextField(
                    value = uiState.username,
                    onValueChange = { newValue ->
                        // Only allow alphanumeric, underscore, and period
                        val filtered = newValue.filter { char ->
                            char.isLetterOrDigit() || char == '_' || char == '.'
                        }
                        authViewModel.updateUsername(filtered)
                    },
                    label = { Text("Username", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { focusManager.moveFocus(FocusDirection.Down) }
                    ),
                    singleLine = true
                )

                // PHONE
                OutlinedTextField(
                    value = uiState.phone,
                    onValueChange = authViewModel::updatePhone,
                    label = { Text("Phone", fontFamily = LeagueSpartan) },
                    modifier = Modifier.fillMaxWidth(),
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Phone,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { 
                            focusManager.clearFocus()
                            onAuthAction()
                        }
                    ),
                    singleLine = true
                )
            }

            if (uiState.error.isNotBlank()) {
                Text(uiState.error, color = MaterialTheme.colorScheme.error, fontFamily = LeagueSpartan)
            }

            Spacer(modifier = Modifier.weight(1f))

            Button(
                enabled = !uiState.isLoading,
                onClick = {
                    focusManager.clearFocus()
                    onAuthAction()
                },
                modifier = Modifier.fillMaxWidth().height(60.dp),
                shape = MaterialTheme.shapes.medium
            ) {
                if (uiState.isLoading) {
                    CircularProgressIndicator(color = Color.White, modifier = Modifier.size(24.dp))
                } else {
                    Text(
                        if (uiState.mode == AuthMode.LOGIN) "Login" else "Create Account",
                        fontFamily = LeagueSpartan,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            TextButton(
                onClick = { authViewModel.toggleMode() },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    if (uiState.mode == AuthMode.LOGIN) "Need an account? Sign up" else "Have an account? Login",
                    fontFamily = LeagueSpartan,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            
            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}
